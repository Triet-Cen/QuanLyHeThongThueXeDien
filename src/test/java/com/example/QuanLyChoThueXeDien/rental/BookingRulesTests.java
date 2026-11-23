package com.example.QuanLyChoThueXeDien.rental;

import com.example.QuanLyChoThueXeDien.auth.entities.CustomerProfile;
import com.example.QuanLyChoThueXeDien.license.entities.DrivingLicense;
import com.example.QuanLyChoThueXeDien.pricing.entities.RentalPrice;
import com.example.QuanLyChoThueXeDien.pricing.services.RentalQuoteService;
import com.example.QuanLyChoThueXeDien.rental.dtos.BookingForm;
import com.example.QuanLyChoThueXeDien.rental.entities.*;
import com.example.QuanLyChoThueXeDien.rental.services.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingRulesTests {
    final LocalDateTime start = LocalDateTime.of(2030, 1, 2, 8, 0);

    @Test void billByFullDurationIncludingFractions() {
        assertEquals(1, RentalQuoteService.rentalDays(start, start.plusMinutes(1)));
        assertEquals(1, RentalQuoteService.rentalDays(start, start.plusHours(24)));
        assertEquals(2, RentalQuoteService.rentalDays(start, start.plusHours(24).plusSeconds(1)));
        assertEquals(2, RentalQuoteService.rentalDays(start, start.plusHours(24).plusNanos(1)));
        assertEquals(2, RentalQuoteService.rentalDays(start, start.plusHours(48)));
        assertThrows(IllegalArgumentException.class, () -> RentalQuoteService.rentalDays(start, start));
    }

    @Test void priceExampleAndHalfUpRounding() {
        var price = RentalQuoteService.amounts(new BigDecimal("1200000"), 2, new BigDecimal("10"));
        assertEquals(new BigDecimal("2400000"), price.rentalAmount());
        assertEquals(new BigDecimal("240000"), price.discountAmount());
        assertEquals(new BigDecimal("2160000"), price.totalAmount());
        assertEquals(new BigDecimal("1"), RentalQuoteService.amounts(new BigDecimal("5"), 1, new BigDecimal("10")).discountAmount());
        assertThrows(IllegalArgumentException.class, () -> RentalQuoteService.amounts(BigDecimal.TEN, 1, new BigDecimal("101")));
    }

    @Test void missingOrAmbiguousPricesFail() {
        RentalPrice price = new RentalPrice(); price.setDailyRate(BigDecimal.TEN);
        assertThrows(IllegalArgumentException.class, () -> RentalQuoteService.requireRate(List.of()));
        assertThrows(IllegalArgumentException.class, () -> RentalQuoteService.requireRate(List.of(price, price)));
        price.setDailyRate(BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> RentalQuoteService.requireRate(List.of(price)));
    }

    @Test void pendingExpiresAtExactDeadlineWithoutScheduler() {
        Rental rental = new Rental(); rental.setStatus("PENDING_PAYMENT"); rental.setHoldExpiresAt(start.plusMinutes(15));
        assertTrue(rental.hasActiveHold(start.plusMinutes(15).minusNanos(1)));
        assertFalse(rental.hasActiveHold(start.plusMinutes(15)));
        rental.setStatus("CANCELLED"); assertFalse(rental.hasActiveHold(start));
    }

    @Test void licenseMustBeVerifiedAndCoverEndDate() {
        CustomerProfile customer = new CustomerProfile(); customer.setPhone("0900000000");
        DrivingLicense license = new DrivingLicense(); license.setLicenseClass("B");
        license.setVerificationStatus(DrivingLicense.VERIFIED); license.setExpiryDate(start.toLocalDate());
        assertNull(BookingService.eligibility(customer, license, start.toLocalDate().atTime(23, 59, 59)));
        assertNotNull(BookingService.eligibility(customer, license, start.plusDays(1)));
        license.setVerificationStatus(DrivingLicense.UNVERIFIED);
        assertNotNull(BookingService.eligibility(customer, license, start));
        license.setVerificationStatus(DrivingLicense.VERIFIED); license.setLicenseClass("A");
        assertNotNull(BookingService.eligibility(customer, license, start));
        customer.setPhone(" "); assertNotNull(BookingService.eligibility(customer, null, start));
    }

    @Test void rejectInvalidTimeAndReadBusinessTimezone() {
        BookingForm f = new BookingForm(); f.setStationId(1); f.setStartAt(start); f.setEndAt(start.plusHours(1));
        assertDoesNotThrow(() -> BookingService.validateTime(f, start));
        assertThrows(IllegalArgumentException.class, () -> BookingService.validateTime(f, start.plusSeconds(1)));
        f.setEndAt(start); assertThrows(IllegalArgumentException.class, () -> BookingService.validateTime(f, start));
        var clock = new RentalClock(Clock.fixed(Instant.parse("2030-01-02T01:00:00Z"), ZoneOffset.UTC));
        assertEquals(start, clock.now());
    }

    @Test void invalidCouponCannotSilentlyDisappear() {
        Coupon coupon = mock(Coupon.class);
        when(coupon.getStatus()).thenReturn("ACTIVE"); when(coupon.getStartsAt()).thenReturn(start.minusDays(1));
        when(coupon.getEndsAt()).thenReturn(start.plusDays(1)); when(coupon.getRemainingQuantity()).thenReturn(1);
        when(coupon.getPercent()).thenReturn(BigDecimal.TEN);
        assertDoesNotThrow(() -> BookingService.validateCoupon(coupon, start));
        when(coupon.getRemainingQuantity()).thenReturn(0);
        assertThrows(IllegalArgumentException.class, () -> BookingService.validateCoupon(coupon, start));
        assertNull(BookingService.normalizeCoupon("  "));
        assertEquals("SALE", BookingService.normalizeCoupon(" SALE "));
    }
}
