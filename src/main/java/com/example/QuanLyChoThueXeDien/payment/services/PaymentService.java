package com.example.QuanLyChoThueXeDien.payment.services;

import com.example.QuanLyChoThueXeDien.payment.dtos.PaymentInvoice;
import com.example.QuanLyChoThueXeDien.payment.dtos.PaymentResult;
import com.example.QuanLyChoThueXeDien.payment.dtos.PaymentFee;
import com.example.QuanLyChoThueXeDien.payment.dtos.PaymentAttempt;
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
            total = r.getTotalAmount();
        }
        BigDecimal amount = total.setScale(0,RoundingMode.HALF_UP);
        return new PaymentInvoice(r,fees,lines,amount,buildInvoiceKey(r.getRentalId(),fees,amount,lines));
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

    public String startPayment(String email, int id, boolean fees, String accepted, UUID request, String ip) {
        PaymentInvoice invoice = getInvoice(email,id,fees);
        if (!invoice.key().equals(accepted)) throw new IllegalArgumentException("Hóa đơn đã thay đổi. Vui lòng xem lại.");
        if (invoice.amount().signum() <= 0) throw new IllegalArgumentException(fees ?
            "Không có phí cần thanh toán." : "Đơn 0 đồng cần chính sách xác nhận miễn phí (XN09).");
        LocalDateTime now = clock.now();
        var previous = payments.findAttemptByRequest(id,request);
        if (previous.isPresent()) {
            PaymentAttempt a = previous.get();
            if (!accepted.equals(a.invoice()) || !"PENDING".equals(a.status()) || !now.isBefore(a.expires())
                || !gateway.merchant().equals(a.merchant())) throw new IllegalArgumentException("Vui lòng mở lại hóa đơn để thử lại.");
            return gateway.buildPaymentUrl(a.ref(),a.amount(),a.created(),a.expires(),ip);
        }
        String ref = UUID.randomUUID().toString().replace("-","");
        var expires = fees ? gateway.getFeePaymentDeadline(now) : invoice.rental().getHoldExpiresAt();
        if (!now.isBefore(expires)) throw new IllegalArgumentException("Đã hết thời hạn thanh toán.");
        String url = gateway.buildPaymentUrl(ref,invoice.amount(),now,expires,ip);
        payments.saveAttempt(id,fees ? "EXTRA_FEE" : "RENTAL",ref,gateway.merchant(),request,accepted,invoice.amount(),now,
            expires,invoice.lines().stream().filter(PaymentFee::unpaid).toList());
        return url;
    }

    public String handleIpn(Map<String,String[]> payload) {
        var verified = gateway.verifyIpn(payload);
        if (verified.isEmpty()) {
            log.warn("Rejected malformed or unauthenticated VNPay IPN");
            return "97";
        }
        var event = verified.get();
        var lookup = payments.findAttemptByRef(event.ref());
        Rental rental = lookup.isEmpty() ? null : bookings.lockForPaymentNotification(lookup.get().rental());
        // Read again after acquiring the same locks as timeout/cancel.
        PaymentAttempt a = rental == null ? null : payments.findAttemptByRef(event.ref()).orElseThrow();
        String previous = payments.recordIpn(event,clock.now());
        if (!"RECEIVED".equals(previous)) return getIpnResponseCode(previous);
        String reason;
        if (!gateway.merchant().equals(event.merchant()) || a != null && !a.merchant().equals(event.merchant()))
            reason = "REVIEW_MERCHANT";
        else if (event.successful() && !payments.claimReceipt(event).equals(event.signature()))
            reason = payments.isSamePaidReceipt(event) ? "DUPLICATE" : "REVIEW_PROVIDER_ID";
        else if (a == null) reason = "REVIEW_UNKNOWN_REF";
        else if (a.amount().compareTo(event.amount()) != 0 || !a.currency().equals(event.currency()))
            reason = "REVIEW_AMOUNT";
        else if (event.successful() && payments.hasLegacyReceipt(event.providerId())) reason = "REVIEW_PROVIDER_ID";
        else if ("01".equals(event.status())) reason = "PROVIDER_PENDING";
        else if (Set.of("04","05","06","07","09").contains(event.status()) ||
            "00".equals(event.response()) != "00".equals(event.status()))
            reason = "REVIEW_PROVIDER_STATUS";
        else if (!event.successful()) {
            if ("PENDING".equals(a.status())) payments.updateAttemptStatus(a,event,"FAILED",clock.now());
            reason = "PROVIDER_FAILURE";
        } else {
            reason = applyPayment(rental,a,event);
        }
        if (reason.startsWith("REVIEW") && !"REVIEW_MERCHANT".equals(reason) && a != null &&
            (event.successful() || "REVIEW_PROVIDER_STATUS".equals(reason)))
            payments.updateAttemptStatus(a,event,"NEEDS_RECONCILIATION",clock.now());
        payments.saveIpnResult(event,reason);
        return getIpnResponseCode(reason);
    }

    static String getIpnResponseCode(String reason) {
        return switch (reason) {
            case "REVIEW_MERCHANT" -> "97";
            case "REVIEW_UNKNOWN_REF" -> "01";
            case "REVIEW_AMOUNT" -> "04";
            case "DUPLICATE" -> "02";
            default -> "00";
        };
    }

    private String applyPayment(Rental r, PaymentAttempt a, VnPayEvent event) {
        if ("SUCCESS".equals(a.status())) return "REVIEW_SECOND_RECEIPT";
        if ("RENTAL".equals(a.kind())) {
            String conflict = confirmRentalPayment(r,a);
            if (conflict != null) return conflict;
        } else if ("EXTRA_FEE".equals(a.kind())) {
            if (!"RENTED".equalsIgnoreCase(r.getStatus()) || !payments.hasReturnReport(a.rental())) return "REVIEW_RETURN";
            var current = payments.findRentalFees(a.rental());
            var selected = payments.findAttemptFees(a.ref());
            if (!feesMatchInvoice(selected,current,a.amount()) || selected.stream().anyMatch(f -> payments.isFeeAllocated(f.id())))
                return "REVIEW_FEE_CHANGED";
            if (!clock.now().isBefore(a.expires())) return "REVIEW_EXPIRED_ATTEMPT";
            for (PaymentFee fee : selected) payments.markFeePaid(a,fee);
        } else return "REVIEW_KIND";
        var now = clock.now();
        payments.updateAttemptStatus(a,event,"SUCCESS",now);
        payments.sendPaymentNotifications(r.getCustomer().getAccount().getAccountId(),a,now);
        return "ALLOCATED";
    }

    static boolean feesMatchInvoice(List<PaymentFee> selected, List<PaymentFee> current, BigDecimal expected) {
        var remaining = new HashMap<Integer,PaymentFee>();
        for (PaymentFee f : current) if (f.unpaid()) remaining.put(f.id(),f);
        BigDecimal sum = BigDecimal.ZERO;
        for (PaymentFee f : selected) {
            PaymentFee live = remaining.remove(f.id());
            if (live == null || live.amount().compareTo(f.amount()) != 0 ||
                !live.type().equals(f.type()) || !live.reason().equals(f.reason())) return false;
            sum = sum.add(f.amount());
        }
        return !selected.isEmpty() && sum.setScale(0,RoundingMode.HALF_UP).compareTo(expected) == 0;
    }

    private String confirmRentalPayment(Rental r, PaymentAttempt a) {
        if (!r.hasActiveHold(clock.now()) || payments.isRentalPaid(a.rental())) return "REVIEW_RENTAL_INACTIVE";
        if (r.getTotalAmount().setScale(0,RoundingMode.HALF_UP).compareTo(a.amount()) != 0) return "REVIEW_SNAPSHOT";
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
        payments.saveAllocation("RENTAL:"+a.rental(),a);
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

    @Transactional(readOnly = true)
    public List<Map<String,Object>> listPaymentsForReview() { return payments.findPaymentsForReview(); }

    public void saveReviewNote(long event, String email, String note) {
        if (note.isBlank() || note.length() > 1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if (!payments.addReviewNote(event,email,note.strip(),clock.now())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
