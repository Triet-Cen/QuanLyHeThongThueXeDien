package com.example.QuanLyChoThueXeDien.payment.services;

import com.example.QuanLyChoThueXeDien.payment.dtos.PaymentInvoice;
import com.example.QuanLyChoThueXeDien.payment.dtos.PaymentResult;
import com.example.QuanLyChoThueXeDien.payment.dtos.PaymentFee;
import com.example.QuanLyChoThueXeDien.payment.dtos.PaymentAttempt;
import com.example.QuanLyChoThueXeDien.payment.dtos.PaymentReference;
import com.example.QuanLyChoThueXeDien.payment.dtos.VnPayEvent;
import com.example.QuanLyChoThueXeDien.payment.repositories.PaymentRepository;
import com.example.QuanLyChoThueXeDien.rental.entities.*;
import com.example.QuanLyChoThueXeDien.rental.repositories.*;
import com.example.QuanLyChoThueXeDien.rental.services.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class PaymentService {
    private final BookingService bookings;
    private final PaymentRepository payments;
    private final RentalRepository rentals;
    private final CouponRepository coupons;
    private final VnPayGateway gateway;
    private final RentalClock clock;
    private final EntityManager em;

    public PaymentInvoice getInvoice(String email, int id, boolean fees) {
        Rental rental = bookings.view(email,id);
        return buildInvoice(rental,fees);
    }

    private PaymentInvoice buildInvoice(Rental r, boolean fees) {
        List<PaymentFee> lines = List.of();
        BigDecimal total;
        if (fees) {
            if (!"RENTED".equalsIgnoreCase(r.getStatus()) || !payments.hasReturnReport(r.getRentalId()))
                throw new IllegalArgumentException("Chỉ thu phí sau khi lập biên bản trả xe cho đơn đang thuê.");
            lines = payments.findRentalFees(r.getRentalId());
            total = lines.stream().filter(PaymentFee::unpaid).map(PaymentFee::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        } else {
            if (!r.hasActiveHold(clock.now()) || payments.isRentalPaid(r.getRentalId()))
                throw new IllegalArgumentException("Đơn đã hết hạn hoặc không còn chờ thu tiền thuê.");
            return rentalInvoice(r);
        }
        BigDecimal amount = total.setScale(0,RoundingMode.HALF_UP);
        return new PaymentInvoice(r,fees,lines,amount,buildInvoiceKey(r.getRentalId(),fees,amount,lines));
    }

    public static PaymentInvoice rentalInvoice(Rental rental) {
        BigDecimal amount = rental.getTotalAmount().setScale(0,RoundingMode.HALF_UP);
        return new PaymentInvoice(rental,false,List.of(),amount,buildInvoiceKey(rental.getRentalId(),false,amount,List.of()));
    }

    // Length-prefix variable text so fee descriptions cannot collide at a field boundary.
    private static String buildInvoiceKey(int rental, boolean fees, BigDecimal amount, List<PaymentFee> lines) {
        StringBuilder data = new StringBuilder().append(rental).append('|').append(fees).append('|').append(amount);
        for (PaymentFee fee : lines) if (fee.unpaid()) {
            for (String field : List.of(String.valueOf(fee.id()),fee.amount().toPlainString(),fee.type(),fee.reason()))
                data.append('|').append(field.length()).append(':').append(field);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(data.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public String startPayment(String email, int id, boolean fees, String accepted, String ip) {
        // A fresh submission has its own MaGD; callbacks serialize against cancel/timeout.
        PaymentInvoice invoice = getInvoice(email,id,fees);
        if (!invoice.key().equals(accepted)) throw new IllegalArgumentException("Hóa đơn đã thay đổi. Vui lòng xem lại.");
        if (invoice.amount().signum() <= 0) throw new IllegalArgumentException("Không có khoản tiền cần thanh toán.");
        LocalDateTime now = clock.now().withNano(0);
        var expires = (fees ? gateway.getFeePaymentDeadline(now) : invoice.rental().getHoldExpiresAt()).withNano(0);
        if (!now.isBefore(expires)) throw new IllegalArgumentException("Đã hết thời hạn thanh toán.");
        int transaction = payments.saveAttempt(id,fees ? "EXTRA_FEE" : "RENTAL",invoice.amount(),now);
        String ref = new PaymentReference(transaction,expires,accepted).encode();
        return gateway.buildPaymentUrl(ref,invoice.amount(),now,expires,ip);
    }
    public String handleIpn(Map<String,String[]> payload) {
        var verified = gateway.verifyIpn(payload);
        if (verified.isEmpty()) {
            log.warn("Rejected malformed or unauthenticated VNPay IPN");
            return "97";
        }
        return processNotification(verified.get());
    }

    public PaymentResult handleReturn(String email, String ref, Map<String,String[]> payload) {
        PaymentResult current = getPaymentResult(email,ref);
        if (!payload.containsKey("vnp_SecureHash")) return current;

        var event = gateway.verifyIpn(payload)
            .filter(callback -> ref.equals(callback.ref()) && callback.successful());
        if (event.isEmpty()) return current;

        processNotification(event.get());
        return getPaymentResult(email,ref);
    }

    private String processNotification(VnPayEvent event) {
        if (!gateway.merchant().equals(event.merchant())) return "97";
        var lookup = payments.findAttemptByRef(event.ref());
        if (lookup.isEmpty()) {
            log.warn("Verified VNPay notification has unknown reference: {}",event.ref());
            return "01";
        }
        Rental rental = bookings.lockForPaymentNotification(lookup.get().rental());
        payments.lockReceiptProcessing();
        PaymentAttempt a = payments.findAttemptByRef(event.ref()).orElseThrow();
        if (a.amount().compareTo(event.amount()) != 0 || !"VND".equals(event.currency())) {
            review(a,event,"REVIEW_AMOUNT"); return "04";
        }
        if ("SUCCESS".equals(a.status()) || "THÀNH CÔNG".equals(a.status())) {
            if (!event.successful()) return "02";
            if (payments.hasSameReceipt(a.id(),event.providerId())) return "02";
            review(a,event,"REVIEW_SECOND_RECEIPT"); return "00";
        }
        if ("NEEDS_RECONCILIATION".equals(a.status()) && payments.hasSameReceipt(a.id(),event.providerId())) return "00";
        String reason;
        if (event.successful() && payments.hasOtherReceipt(a.id(),event.providerId())) reason = "REVIEW_PROVIDER_ID";
        else if (event.successful() && PaymentReference.parse(a.ref()).isEmpty()) reason = "REVIEW_LEGACY_ATTEMPT";
        else if ("01".equals(event.status())) return "00";
        else if (Set.of("04","05","06","07","09").contains(event.status()) ||
            "00".equals(event.response()) != "00".equals(event.status())) reason = "REVIEW_PROVIDER_STATUS";
        else if (!event.successful()) {
            if ("PENDING".equals(a.status())) payments.updateAttemptStatus(a,event,"FAILED",clock.now());
            return "00";
        } else reason = applyPayment(rental,a,event);
        if (reason.startsWith("REVIEW")) review(a,event,reason);
        return "00";
    }

    private void review(PaymentAttempt a, VnPayEvent event, String reason) {
        // Retain the first receipt under review; additional receipts stay in the existing order history.
        if (!Set.of("SUCCESS","THÀNH CÔNG","NEEDS_RECONCILIATION","CẦN ĐỐI SOÁT").contains(a.status()))
            payments.updateAttemptStatus(a,event,"NEEDS_RECONCILIATION",clock.now());
        payments.recordReview(a,event,reason,clock.now());
    }
    private String applyPayment(Rental r, PaymentAttempt a, VnPayEvent event) {
        if ("SUCCESS".equals(a.status())) return "REVIEW_SECOND_RECEIPT";
        if ("RENTAL".equals(a.kind())) {
            String conflict = confirmRentalPayment(r,a);
            if (conflict != null) return conflict;
        } else if ("EXTRA_FEE".equals(a.kind())) {
            if (!"RENTED".equalsIgnoreCase(r.getStatus()) || !payments.hasReturnReport(a.rental())) return "REVIEW_RETURN";
            var current = payments.findRentalFees(a.rental());
            var selected = current.stream().filter(PaymentFee::unpaid).toList();
            BigDecimal total = selected.stream().map(PaymentFee::amount).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(0,RoundingMode.HALF_UP);
            if (selected.isEmpty() || total.compareTo(a.amount()) != 0 ||
                !a.invoice().equals(buildInvoiceKey(a.rental(),true,total,current))) return "REVIEW_FEE_CHANGED";
            if (!clock.now().isBefore(a.expires())) return "REVIEW_EXPIRED_ATTEMPT";
            for (PaymentFee fee : selected) payments.markFeePaid(a,fee);
        } else return "REVIEW_KIND";
        var now = clock.now();
        payments.updateAttemptStatus(a,event,"SUCCESS",now);
        payments.sendPaymentNotifications(r.getCustomer().getAccount().getAccountId(),a,now);
        return "ALLOCATED";
    }

    private String confirmRentalPayment(Rental r, PaymentAttempt a) {
        if (!r.hasActiveHold(clock.now()) || payments.isRentalPaid(a.rental())) return "REVIEW_RENTAL_INACTIVE";
        if (!rentalInvoice(r).key().equals(a.invoice()) || r.getTotalAmount().setScale(0,RoundingMode.HALF_UP).compareTo(a.amount()) != 0) return "REVIEW_SNAPSHOT";
        var vehicle = r.getVehicle();
        int id = vehicle.getVehicleId();
        if ("MAINTENANCE".equalsIgnoreCase(vehicle.getVehicleStatus()) || payments.hasOpenMaintenance(id))
            return "REVIEW_MAINTENANCE";
        if (!rentals.overdueRentals(id,clock.now()).isEmpty() ||
            rentals.conflicting(id,r.getStartAt(),r.getEndAt(),clock.now()).stream()
                .anyMatch(other -> !other.getRentalId().equals(r.getRentalId()))) return "REVIEW_SCHEDULE";
        Coupon coupon = null;
        if (r.getCouponCode() != null) {
            coupon = coupons.findForUpdate(r.getCouponCode()).orElse(null);
            if (coupon == null || coupon.getRemainingQuantity() == null ||
                coupon.getRemainingQuantity() < rentals.activeCouponHolds(r.getCouponCode(),clock.now()).size())
                return "REVIEW_COUPON";
        }
        boolean rented = payments.hasRentedOrder(id) || "RENTED".equalsIgnoreCase(vehicle.getVehicleStatus());
        var now = clock.now();
        if (!r.hasActiveHold(now) || !now.isBefore(a.expires())) return "REVIEW_RENTAL_INACTIVE";

        if (coupon != null) coupon.consumeReservedUse();
        RentalHistory history = new RentalHistory();
        history.setRental(r);
        history.setPreviousStatus(r.getStatus());
        history.setNewStatus("BOOKED");
        history.setChangedAt(now);
        history.setReason("VNPAY " + a.ref());
        em.persist(history);
        r.setStatus("BOOKED");
        vehicle.setVehicleStatus(rented ? "Rented" : "Booked");
        return null;
    }

    @Transactional(readOnly = true)
    public PaymentResult getPaymentResult(String email, String ref) {
        var a = payments.findAttemptByRef(ref).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Rental rental = bookings.readOwned(email,a.rental());
        return new PaymentResult(a.rental(),a.ref(),a.status(),rental.getStatus(),a.amount());
    }

}
