package com.example.QuanLyChoThueXeDien.payment.services;

import com.example.QuanLyChoThueXeDien.payment.dtos.PaymentFee;
import com.example.QuanLyChoThueXeDien.payment.dtos.PaymentAttempt;
import com.example.QuanLyChoThueXeDien.payment.dtos.VnPayEvent;
import com.example.QuanLyChoThueXeDien.payment.controllers.PaymentController;
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
        var attempt = new PaymentAttempt(3,1,kind,"REF","TEST","VND","invoice",amount,status,now,expiry);
        when(db.findAttemptByRef("REF")).thenReturn(Optional.of(attempt));
        return attempt;
    }

    VnPayEvent event(BigDecimal received, String response) {
        var event = new VnPayEvent("REF","TEST","123",received,"VND","20300101081000",response,response,"signed");
        when(gateway.verifyIpn(payload)).thenReturn(Optional.of(event));
        when(db.recordIpn(eq(event),any())).thenReturn("RECEIVED");
        when(db.claimReceipt(event)).thenReturn(event.signature());
        return event;
    }

    @Test void vp01_29_returnReadsOnlyForActiveOwner() {
        attempt("RENTAL","PENDING");
        when(bookings.readOwned("other",1)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertThrows(ResponseStatusException.class,()->service.getPaymentResult("other","REF"));
        assertEquals("PENDING",service.getPaymentResult("owner","REF").status());
        verify(db,never()).updateAttemptStatus(any(),any(),any(),any());
        verify(bookings,never()).lockForPaymentNotification(any());
    }

    @Test void vp02_24_30_zeroAmountExpiredAndPaidCannotStart() {
        rental.setTotalAmount(BigDecimal.ZERO);
        var invoice = service.getInvoice("owner",1,false);
        assertThrows(IllegalArgumentException.class,()->service.startPayment("owner",1,false,invoice.key(),UUID.randomUUID(),"::1"));
        rental.setHoldExpiresAt(now);
        assertThrows(IllegalArgumentException.class,()->service.getInvoice("owner",1,false));
        rental.setHoldExpiresAt(expiry);
        when(db.isRentalPaid(1)).thenReturn(true);
        assertThrows(IllegalArgumentException.class,()->service.getInvoice("owner",1,false));
        verify(gateway,never()).buildPaymentUrl(any(),any(),any(),any(),any());
    }

    @Test void vp03_04_05_06_snapshotAndRepeatRequestDoNotBookOrExtend() {
        var invoice = service.getInvoice("owner",1,false);
        UUID request = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,()->service.startPayment("owner",1,false,"forged",request,"::1"));
        var a = new PaymentAttempt(3,1,"RENTAL","REF","TEST","VND",invoice.key(),invoice.amount(),"PENDING",now,expiry);
        when(db.findAttemptByRequest(1,request)).thenReturn(Optional.empty(),Optional.of(a));
        service.startPayment("owner",1,false,invoice.key(),request,"::1");
        service.startPayment("owner",1,false,invoice.key(),request,"::1");
        verify(db,times(1)).saveAttempt(eq(1),eq("RENTAL"),anyString(),eq("TEST"),eq(request),eq(invoice.key()),
            eq(invoice.amount()),eq(now),eq(expiry),eq(List.of()));
        verify(gateway).buildPaymentUrl("REF",invoice.amount(),now,expiry,"::1");
        assertTrue(rental.isPending());
    }

    @Test void vp07_invalidSignatureDoesNotReadOrWriteLedger() {
        assertEquals("97",service.handleIpn(payload));
        verifyNoInteractions(bookings,db);
    }

    @Test void vp07_08_14_signedIpnThroughControllerWithoutCustomerSession() throws Exception {
        var realGateway = new VnPayGateway("TEST","fixture-only","","","",0);
        var controller = new PaymentController(new PaymentService(bookings,db,rentals,coupons,realGateway,clock,em));
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        String canonical = "vnp_Amount=1000000&vnp_ResponseCode=00&vnp_TmnCode=TEST&vnp_TransactionNo=123"
            + "&vnp_TransactionStatus=00&vnp_TxnRef=REF";
        for (String field : canonical.split("&")) {
            String[] kv = field.split("=");
            request.addParameter(kv[0],kv[1]);
        }
        request.addParameter("vnp_SecureHash",VnPayGatewayTests.checksum(canonical));
        var event = realGateway.verifyIpn(request.getParameterMap()).orElseThrow();
        when(db.recordIpn(event,now)).thenReturn("RECEIVED");
        when(db.claimReceipt(event)).thenReturn(event.signature());
        assertEquals("01",controller.receiveIpn(request).get("RspCode"));
        verify(db).saveIpnResult(event,"REVIEW_UNKNOWN_REF");
        assertNull(request.getSession(false));
    }

    @Test void vp08_verifiedWrongAmountGoesToReview() {
        var a = attempt("RENTAL","PENDING"); var e = event(BigDecimal.ONE,"00");
        assertEquals("04",service.handleIpn(payload));
        verify(db).updateAttemptStatus(a,e,"NEEDS_RECONCILIATION",now);
        verify(db,never()).saveAllocation(any(),any());
    }

    @Test void vp09_10_18_rentalConsumesReservedCouponOnceAndPreservesRentedVehicle() {
        var a = attempt("RENTAL","PENDING"); var e = event(amount,"00");
        rental.setCouponCode("CODE"); var coupon = new Coupon();
        ReflectionTestUtils.setField(coupon,"remainingQuantity",1);
        when(coupons.findForUpdate("CODE")).thenReturn(Optional.of(coupon));
        when(rentals.activeCouponHolds("CODE",now)).thenReturn(List.of(rental));
        when(db.hasRentedOrder(2)).thenReturn(true);
        when(db.recordIpn(e,now)).thenReturn("RECEIVED","ALLOCATED");
        assertEquals("00",service.handleIpn(payload)); assertEquals("00",service.handleIpn(payload));
        assertEquals("BOOKED",rental.getStatus()); assertEquals("Rented",vehicle.getVehicleStatus());
        assertEquals(0,coupon.getRemainingQuantity());
        verify(db).saveAllocation("RENTAL:1",a); verify(em).persist(any(RentalHistory.class));
        verify(db).sendPaymentNotifications(7,a,now);
    }

    @Test void vp11_otherSuccessfulMethodOrAttemptBlocksSecondAllocation() {
        attempt("RENTAL","PENDING"); var e = event(amount,"00");
        when(db.isRentalPaid(1)).thenReturn(true);
        service.handleIpn(payload);
        verify(db).saveIpnResult(e,"REVIEW_RENTAL_INACTIVE");
        verify(db,never()).saveAllocation(any(),any());
    }

    @Test void vp11_preservePaymentAlreadyRecordedBeforeMigration() {
        attempt("RENTAL","PENDING"); var e = event(amount,"00");
        when(db.hasLegacyReceipt(e.providerId())).thenReturn(true);
        assertEquals("00",service.handleIpn(payload));
        verify(db).saveIpnResult(e,"REVIEW_PROVIDER_ID");
        verify(db,never()).saveAllocation(any(),any());
        assertTrue(rental.isPending());
    }

    @Test void vp12_13_failureCannotOverwriteSuccessfulAttempt() {
        var a = attempt("RENTAL","PENDING"); var e = event(amount,"24");
        service.handleIpn(payload);
        verify(db).updateAttemptStatus(a,e,"FAILED",now); assertTrue(rental.isPending());
        attempt("RENTAL","SUCCESS");
        service.handleIpn(payload);
        verify(db,times(1)).updateAttemptStatus(any(),any(),any(),any());
    }

    @Test void vp15_16_exactDeadlineAndCancelledGoToReview() {
        var a = attempt("RENTAL","PENDING"); var e = event(amount,"00");
        rental.setHoldExpiresAt(now);
        service.handleIpn(payload);
        rental.setStatus("CANCELLED"); rental.setHoldExpiresAt(expiry);
        service.handleIpn(payload);
        verify(db,times(2)).saveIpnResult(e,"REVIEW_RENTAL_INACTIVE");
        verify(db,never()).saveAllocation(any(),eq(a));
        assertEquals("CANCELLED",rental.getStatus());
    }

    @Test void vp19_cannotInvoiceFeesWithoutReturnRecord() {
        rental.setStatus("RENTED");
        assertThrows(IllegalArgumentException.class,()->service.getInvoice("owner",1,true));
        verify(db,never()).findRentalFees(1);
    }

    @Test void vp20_21_22_payOnlySnapshottedFeesEvenWhenRentalHoldExpired() {
        rental.setStatus("RENTED"); rental.setHoldExpiresAt(now.minusDays(3));
        var a = attempt("EXTRA_FEE","PENDING"); var e = event(amount,"00");
        var later = new PaymentFee(9,BigDecimal.TEN,"LATE","Trả trễ","UNPAID");
        when(db.hasReturnReport(1)).thenReturn(true); when(db.findRentalFees(1)).thenReturn(List.of(fee,later));
        when(db.findAttemptFees("REF")).thenReturn(List.of(fee));
        service.handleIpn(payload);
        verify(db).markFeePaid(a,fee); verify(db,never()).markFeePaid(a,later);
        verify(db).updateAttemptStatus(a,e,"SUCCESS",now); assertEquals("RENTED",rental.getStatus());
    }

    @Test void vp23_changedRemovedOrPreviouslyPaidFeeCannotSettle() {
        assertFalse(PaymentService.feesMatchInvoice(List.of(fee),List.of(),amount));
        for (var changed : List.of(new PaymentFee(4,amount,"DAMAGE","Đèn sau","UNPAID"),
            new PaymentFee(4,BigDecimal.ONE,"DAMAGE","Đèn trước","UNPAID"),new PaymentFee(4,amount,"DAMAGE","Đèn trước","PAID")))
            assertFalse(PaymentService.feesMatchInvoice(List.of(fee),List.of(changed),amount));
    }
}
