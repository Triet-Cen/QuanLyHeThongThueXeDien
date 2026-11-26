package com.example.QuanLyChoThueXeDien.payment.services;

import com.example.QuanLyChoThueXeDien.payment.dtos.*;
import com.example.QuanLyChoThueXeDien.payment.repositories.PaymentRepository;
import com.example.QuanLyChoThueXeDien.auth.entities.*;
import com.example.QuanLyChoThueXeDien.rental.entities.*;
import com.example.QuanLyChoThueXeDien.rental.repositories.*;
import com.example.QuanLyChoThueXeDien.rental.services.*;
import com.example.QuanLyChoThueXeDien.vehicle.entities.Vehicle;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentServiceTests {
    final BookingService bookings = mock();
    final PaymentRepository db = mock();
    final RentalRepository rentals = mock();
    final CouponRepository coupons = mock();
    final VnPayGateway gateway = mock();
    final RentalClock clock = mock();
    final EntityManager em = mock();
    final PaymentService service = new PaymentService(bookings,db,rentals,coupons,gateway,clock,em);
    final LocalDateTime now = LocalDateTime.of(2030,1,1,8,10);
    final LocalDateTime expiry = now.plusMinutes(5);
    final BigDecimal amount = new BigDecimal("10000.00");
    final Rental rental = new Rental();
    final Vehicle vehicle = new Vehicle();
    final Map<String,String[]> payload = Map.of();
    final PaymentFee fee = new PaymentFee(4,amount,"DAMAGE","Đèn trước","UNPAID");
    String ref;

    @BeforeEach void setup() {
        rental.setRentalId(1); rental.setStatus("PENDING_PAYMENT"); rental.setTotalAmount(amount);
        rental.setHoldExpiresAt(expiry); rental.setStartAt(now.plusDays(1)); rental.setEndAt(now.plusDays(2));
        vehicle.setVehicleId(2); rental.setVehicle(vehicle);
        var owner = new Account(); owner.setAccountId(7);
        var customer = new CustomerProfile(); customer.setAccount(owner); rental.setCustomer(customer);
        when(bookings.view("owner",1)).thenReturn(rental);
        when(bookings.readOwned("owner",1)).thenReturn(rental);
        when(bookings.lockForPaymentNotification(1)).thenReturn(rental);
        when(clock.now()).thenReturn(now); when(gateway.merchant()).thenReturn("TEST");
    }

    PaymentAttempt attempt(String kind, String status) {
        boolean fees = "EXTRA_FEE".equals(kind);
        if (fees) {
            rental.setStatus("RENTED"); when(db.hasReturnReport(1)).thenReturn(true);
            when(db.findRentalFees(1)).thenReturn(List.of(fee));
        }
        String invoice = service.getInvoice("owner",1,fees).key();
        ref = new PaymentReference(3,expiry,invoice).encode();
        var a = new PaymentAttempt(3,1,kind,ref,"","VND",invoice,amount,status,now,expiry);
        when(db.findAttemptByRef(ref)).thenReturn(Optional.of(a));
        return a;
    }

    VnPayEvent event(BigDecimal received, String response) {
        var e = new VnPayEvent(ref,"TEST","123",received,"VND","20300101081000",response,response,"signed");
        when(gateway.verifyIpn(payload)).thenReturn(Optional.of(e)); return e;
    }

    @Test void referenceRoundTripsWithoutAnyAdditionalColumns() {
        String invoice = "a".repeat(64);
        var token = new PaymentReference(Integer.MAX_VALUE,expiry,invoice);
        assertEquals(token,PaymentReference.parse(token.encode()).orElseThrow());
        assertTrue(token.encode().matches("[A-Za-z0-9]{1,100}"));
        for(String invalid:List.of("3","V0T1893485700H"+invoice,"V9999999999T1893485700H"+invoice,"forged"))
            assertTrue(PaymentReference.parse(invalid).isEmpty());
    }

    @Test void startCreatesAnAttemptAndUsesGeneratedIdInSignedGatewayUrl() {
        String key = service.getInvoice("owner",1,false).key();
        when(db.saveAttempt(1,"RENTAL",new BigDecimal("10000"),now)).thenReturn(3);
        String expected = new PaymentReference(3,expiry,key).encode();
        when(gateway.buildPaymentUrl(expected,new BigDecimal("10000"),now,expiry,"::1")).thenReturn("https://gateway.invalid/pay");
        assertEquals("https://gateway.invalid/pay",service.startPayment("owner",1,false,key,"::1"));
        verify(bookings).readOwned("owner",1); verify(bookings).lockForPaymentNotification(1);
        assertTrue(rental.isPending()); verify(em,never()).persist(any());
    }

    @Test void changedInvoiceAndZeroAmountCannotStart() {
        assertThrows(IllegalArgumentException.class,()->service.startPayment("owner",1,false,"forged","::1"));
        rental.setTotalAmount(BigDecimal.ZERO);
        String key=service.getInvoice("owner",1,false).key();
        assertThrows(IllegalArgumentException.class,()->service.startPayment("owner",1,false,key,"::1"));
        verify(db,never()).saveAttempt(anyInt(),anyString(),any(),any());
    }

    @Test void invalidSignatureDoesNotAccessDatabase() {
        assertEquals("97",service.handleIpn(payload)); verifyNoInteractions(db,bookings);
    }

    @Test void wrongMerchantDoesNotAccessDatabase() {
        var e=new VnPayEvent("unknown","OTHER","123",amount,"VND",null,"00","00","signed");
        when(gateway.verifyIpn(payload)).thenReturn(Optional.of(e));
        assertEquals("97",service.handleIpn(payload)); verifyNoInteractions(db,bookings);
    }

    @Test void unknownReferenceReturnsOrderNotFound() {
        ref="unknown";event(amount,"00"); assertEquals("01",service.handleIpn(payload));
        verify(db,never()).updateAttemptStatus(any(),any(),any(),any());
    }

    @Test void wrongAmountGoesToReviewWithoutBooking() {
        var a=attempt("RENTAL","PENDING");var e=event(BigDecimal.ONE,"00");
        assertEquals("04",service.handleIpn(payload));
        verify(db).updateAttemptStatus(a,e,"NEEDS_RECONCILIATION",now);
        verify(db).recordReview(a,e,"REVIEW_AMOUNT",now); assertTrue(rental.isPending());
    }

    @Test void rentalConsumesCouponOnceAndDuplicateCallbackReturns02() {
        var a=attempt("RENTAL","PENDING");var e=event(amount,"00");
        rental.setCouponCode("CODE");var coupon=new Coupon();ReflectionTestUtils.setField(coupon,"remainingQuantity",1);
        when(coupons.findForUpdate("CODE")).thenReturn(Optional.of(coupon));
        when(rentals.activeCouponHolds("CODE",now)).thenReturn(List.of(rental));
        when(db.hasRentedOrder(2)).thenReturn(true);
        assertEquals("00",service.handleIpn(payload));
        assertEquals("BOOKED",rental.getStatus());assertEquals("Rented",vehicle.getVehicleStatus());
        var paid=new PaymentAttempt(a.id(),a.rental(),a.kind(),a.ref(),"","VND",a.invoice(),a.amount(),"SUCCESS",now,expiry);
        when(db.findAttemptByRef(ref)).thenReturn(Optional.of(paid));when(db.hasSameReceipt(3,"123")).thenReturn(true);
        assertEquals("02",service.handleIpn(payload));assertEquals(0,coupon.getRemainingQuantity());
        verify(db).updateAttemptStatus(a,e,"SUCCESS",now);verify(db).sendPaymentNotifications(7,a,now);
        verify(em).persist(any(RentalHistory.class));
    }

    @Test void providerReceiptCannotBeUsedForAnotherOrder() {
        var a=attempt("RENTAL","PENDING");var e=event(amount,"00");when(db.hasOtherReceipt(3,"123")).thenReturn(true);
        assertEquals("00",service.handleIpn(payload));verify(db).recordReview(a,e,"REVIEW_PROVIDER_ID",now);
        assertTrue(rental.isPending());
    }

    @Test void failureCannotOverwriteSuccessfulPayment() {
        var a=attempt("RENTAL","PENDING");var e=event(amount,"24");service.handleIpn(payload);
        verify(db).updateAttemptStatus(a,e,"FAILED",now);
        attempt("RENTAL","SUCCESS");assertEquals("02",service.handleIpn(payload));
        verify(db,times(1)).updateAttemptStatus(any(),any(),any(),any());
    }

    @Test void expiredOrCancelledRentalCannotBeRestored() {
        var a=attempt("RENTAL","PENDING");var e=event(amount,"00");rental.setHoldExpiresAt(now);
        assertEquals("00",service.handleIpn(payload));rental.setStatus("CANCELLED");rental.setHoldExpiresAt(expiry);
        service.handleIpn(payload);verify(db,times(2)).recordReview(a,e,"REVIEW_RENTAL_INACTIVE",now);
        assertEquals("CANCELLED",rental.getStatus());verify(db,never()).sendPaymentNotifications(anyInt(),any(),any());
    }

    @Test void unchangedFeesArePaidWithoutSnapshotTable() {
        var a=attempt("EXTRA_FEE","PENDING");var e=event(amount,"00");rental.setHoldExpiresAt(now.minusDays(3));
        assertEquals("00",service.handleIpn(payload));verify(db).markFeePaid(a,fee);
        verify(db).updateAttemptStatus(a,e,"SUCCESS",now);assertEquals("RENTED",rental.getStatus());
    }

    @Test void changedFeeDescriptionOrAddedFeeRequiresReview() {
        var a=attempt("EXTRA_FEE","PENDING");var e=event(amount,"00");
        when(db.findRentalFees(1)).thenReturn(List.of(new PaymentFee(4,amount,"DAMAGE","Đèn sau","UNPAID")));
        service.handleIpn(payload);verify(db).recordReview(a,e,"REVIEW_FEE_CHANGED",now);
        when(db.findRentalFees(1)).thenReturn(List.of(fee,new PaymentFee(9,BigDecimal.TEN,"LATE","Trả trễ","UNPAID")));
        service.handleIpn(payload);verify(db,times(2)).recordReview(a,e,"REVIEW_FEE_CHANGED",now);
        verify(db,never()).markFeePaid(any(),any());
    }

    @Test void expiredFeeAttemptRequiresReview() {
        var a=attempt("EXTRA_FEE","PENDING");var e=event(amount,"00");when(clock.now()).thenReturn(expiry);
        service.handleIpn(payload);verify(db).recordReview(a,e,"REVIEW_EXPIRED_ATTEMPT",expiry);
        verify(db,never()).markFeePaid(any(),any());
    }

    @Test void returnRequiresOwnerAndAuthenticPayload() {
        attempt("RENTAL","PENDING");
        when(bookings.readOwned("other",1)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertThrows(ResponseStatusException.class,()->service.handleReturn("other",ref,payload));
        assertEquals("PENDING",service.handleReturn("owner",ref,Map.of("vnp_ResponseCode",new String[]{"00"})).status());
        verify(db,never()).updateAttemptStatus(any(),any(),any(),any());
    }

    @Test void migratedPendingReferenceIsReviewedRatherThanGuessingItsInvoice() {
        ref="a".repeat(32);var a=new PaymentAttempt(3,1,"RENTAL",ref,"","VND","",amount,"PENDING",now,LocalDateTime.MIN);
        when(db.findAttemptByRef(ref)).thenReturn(Optional.of(a));var e=event(amount,"00");
        assertEquals("00",service.handleIpn(payload));verify(db).recordReview(a,e,"REVIEW_LEGACY_ATTEMPT",now);
        assertTrue(rental.isPending());
    }
}