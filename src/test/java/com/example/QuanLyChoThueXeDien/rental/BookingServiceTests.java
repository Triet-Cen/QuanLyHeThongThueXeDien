package com.example.QuanLyChoThueXeDien.rental;

import com.example.QuanLyChoThueXeDien.auth.entities.*;
import com.example.QuanLyChoThueXeDien.auth.repositories.*;
import com.example.QuanLyChoThueXeDien.license.entities.DrivingLicense;
import com.example.QuanLyChoThueXeDien.license.repositories.DrivingLicenseRepository;
import com.example.QuanLyChoThueXeDien.pricing.services.RentalQuoteService;
import com.example.QuanLyChoThueXeDien.rental.dtos.*;
import com.example.QuanLyChoThueXeDien.rental.entities.*;
import com.example.QuanLyChoThueXeDien.rental.repositories.*;
import com.example.QuanLyChoThueXeDien.rental.services.*;
import com.example.QuanLyChoThueXeDien.vehicle.entities.Vehicle;
import com.example.QuanLyChoThueXeDien.vehicle.repositories.VehicleRepository;
import com.example.QuanLyChoThueXeDien.VehicleModel.entities.VehicleModel;
import com.example.QuanLyChoThueXeDien.category.entities.VehicleCategory;
import com.example.QuanLyChoThueXeDien.station.entities.Station;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookingServiceTests {
    AccountRepository accounts; CustomerProfileRepository customers; DrivingLicenseRepository licenses;
    VehicleRepository vehicles; RentalRepository rentals; CouponRepository coupons;
    RentalQuoteService pricing; EntityManager em; BookingService service;
    Account account; CustomerProfile customer; DrivingLicense license; Vehicle vehicle; BookingForm form;
    final LocalDateTime now = LocalDateTime.of(2030, 1, 1, 8, 0);

    @BeforeEach void setup() {
        accounts = mock(AccountRepository.class); customers = mock(CustomerProfileRepository.class);
        licenses = mock(DrivingLicenseRepository.class); vehicles = mock(VehicleRepository.class);
        rentals = mock(RentalRepository.class); coupons = mock(CouponRepository.class);
        pricing = mock(RentalQuoteService.class); em = mock(EntityManager.class);
        service = new BookingService(accounts, customers, licenses, vehicles, rentals, coupons, pricing,
            new RentalClock(Clock.fixed(now.atZone(RentalClock.ZONE).toInstant(), RentalClock.ZONE)), em);
        account = new Account(); account.setAccountId(1); account.setEmail("a"); account.setRole("CUSTOMER"); account.setStatus("ACTIVE");
        customer = new CustomerProfile(); customer.setCustomerId(1); customer.setAccount(account); customer.setPhone("0900000000");
        license = new DrivingLicense(); license.setLicenseClass("B"); license.setVerificationStatus(DrivingLicense.VERIFIED);
        license.setExpiryDate(now.toLocalDate().plusYears(1));
        Station station = new Station(); station.setStationId(1); station.setStationStatus("ACTIVE"); station.setStationName("Trạm A");
        VehicleCategory category = new VehicleCategory(); category.setCategoryId(1);
        VehicleModel model = mock(VehicleModel.class); when(model.getModelId()).thenReturn(1); when(model.getCategory()).thenReturn(category);
        when(model.getModelStatus()).thenReturn("ACTIVE"); when(model.getModelName()).thenReturn("Xe X");
        vehicle = new Vehicle(); vehicle.setVehicleId(1); vehicle.setVehicleModel(model); vehicle.setStation(station);
        form = new BookingForm(); form.setVehicleId(1); form.setStationId(1); form.setStartAt(now.plusDays(1)); form.setEndAt(now.plusDays(2));
        when(accounts.findByEmail("a")).thenReturn(Optional.of(account));
        when(customers.findByAccount_Email("a")).thenReturn(Optional.of(customer));
        when(licenses.findByCustomer_Account_Email("a")).thenReturn(Optional.of(license));
        when(vehicles.findForUpdate(1)).thenReturn(Optional.of(vehicle));
        when(pricing.dailyRate(anyInt(), any())).thenReturn(new BigDecimal("1200000"));
        when(rentals.saveAndFlush(any())).thenAnswer(i -> { Rental r = i.getArgument(0); r.setRentalId(8); return r; });
    }

    BookingQuote accepted(BigDecimal rate) {
        return new BookingQuote(form.copy(), "Xe X", null, null, "Trạm A", 1, rate, BigDecimal.ZERO,
            RentalQuoteService.amounts(rate, 1, BigDecimal.ZERO), null, now);
    }

    @Test void createsSnapshotsAndOriginalDeadlineWithoutChangingVehicle() {
        assertEquals(8, service.create("a", accepted(new BigDecimal("1200000"))));
        var captor = org.mockito.ArgumentCaptor.forClass(Rental.class); verify(rentals).saveAndFlush(captor.capture());
        Rental r = captor.getValue(); assertEquals("PENDING_PAYMENT", r.getStatus());
        assertEquals(now.plusMinutes(15), r.getHoldExpiresAt()); assertEquals(new BigDecimal("1200000"), r.getTotalAmount());
        assertEquals("Available", vehicle.getVehicleStatus()); verify(em, times(2)).persist(any());
    }

    @Test void missingQuoteIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.create("a", null));
        verify(rentals, never()).saveAndFlush(any());
    }

    @Test void scheduleConflictIsRejectedEvenWithValidQuote() {
        when(rentals.conflicting(eq(1), any(), any(), any())).thenReturn(List.of(new Rental()));
        assertThrows(IllegalArgumentException.class, () -> service.create("a", accepted(new BigDecimal("1200000"))));
        verify(rentals, never()).saveAndFlush(any());
    }

    @Test void inactiveAndWrongRoleCannotCreate() {
        account.setStatus("INACTIVE");
        assertThrows(ResponseStatusException.class, () -> service.create("a", accepted(BigDecimal.TEN)));
        account.setStatus("ACTIVE"); account.setRole("STAFF");
        assertThrows(ResponseStatusException.class, () -> service.create("a", accepted(BigDecimal.TEN)));
        verify(rentals, never()).saveAndFlush(any());
    }

    @Test void changingLicenseOrPriceAfterQuoteBlocksCreation() {
        license.setVerificationStatus(DrivingLicense.UNVERIFIED);
        assertThrows(IllegalArgumentException.class, () -> service.create("a", accepted(new BigDecimal("1200000"))));
        license.setVerificationStatus(DrivingLicense.VERIFIED);
        assertThrows(IllegalArgumentException.class, () -> service.create("a", accepted(BigDecimal.TEN)));
        verify(rentals, never()).saveAndFlush(any());
    }

    @Test void failureWritingHistoryIsPropagatedForTransactionRollback() {
        doThrow(new IllegalStateException("write failed")).when(em).persist(any(RentalHistory.class));
        assertThrows(IllegalStateException.class, () -> service.create("a", accepted(new BigDecimal("1200000"))));
    }

    @Test void exactDeadlineCancelsOnlyOnceAndPreservesVehicleState() {
        Rental rental = new Rental(); rental.setRentalId(8); rental.setCustomer(customer); rental.setVehicle(vehicle);
        rental.setPickupStation(vehicle.getStation()); rental.setStatus("PENDING_PAYMENT"); rental.setHoldExpiresAt(now);
        vehicle.setVehicleStatus("Booked"); when(rentals.findById(8)).thenReturn(Optional.of(rental));
        service.view("a", 8); service.view("a", 8);
        assertEquals("CANCELLED", rental.getStatus()); assertEquals("Booked", vehicle.getVehicleStatus());
        verify(em, times(2)).persist(any());
    }

    @Test void anotherCustomersOrderIsNotExposedOrCancelled() {
        Account other = new Account(); other.setAccountId(2);
        CustomerProfile owner = new CustomerProfile(); owner.setAccount(other);
        Rental rental = new Rental(); rental.setCustomer(owner); when(rentals.findById(8)).thenReturn(Optional.of(rental));
        assertThrows(ResponseStatusException.class, () -> service.cancel("a", 8));
        verifyNoInteractions(vehicles); verify(em, never()).persist(any());
    }
}
