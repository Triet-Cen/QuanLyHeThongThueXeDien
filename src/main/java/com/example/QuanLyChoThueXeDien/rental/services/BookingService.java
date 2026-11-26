package com.example.QuanLyChoThueXeDien.rental.services;

import com.example.QuanLyChoThueXeDien.auth.entities.*;
import com.example.QuanLyChoThueXeDien.auth.repositories.*;
import com.example.QuanLyChoThueXeDien.license.entities.DrivingLicense;
import com.example.QuanLyChoThueXeDien.license.repositories.DrivingLicenseRepository;
import com.example.QuanLyChoThueXeDien.pricing.services.RentalQuoteService;
import com.example.QuanLyChoThueXeDien.rental.dtos.*;
import com.example.QuanLyChoThueXeDien.rental.entities.*;
import com.example.QuanLyChoThueXeDien.rental.repositories.*;
import com.example.QuanLyChoThueXeDien.vehicle.entities.Vehicle;
import com.example.QuanLyChoThueXeDien.vehicle.repositories.VehicleRepository;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class BookingService {
    private final AccountRepository accounts;
    private final CustomerProfileRepository customers;
    private final DrivingLicenseRepository licenses;
    private final VehicleRepository vehicles;
    private final RentalRepository rentals;
    private final CouponRepository coupons;
    private final RentalQuoteService pricing;
    private final RentalClock clock;
    private final EntityManager em;

    @Transactional
    public BookingQuote quote(String email, BookingForm form) {
        requireCustomer(email, false);
        CustomerProfile customer = customers.findByAccount_Email(email)
            .orElseThrow(() -> new IllegalArgumentException("Chưa có hồ sơ khách hàng. Vui lòng hoàn thiện hồ sơ."));
        DrivingLicense license = licenses.findByCustomer_Account_Email(email).orElse(null);
        Vehicle vehicle = findVehicle(form);
        return calculate(form, vehicle, eligibility(customer, license, form.getEndAt()), false);
    }

    @Transactional
    public Integer create(String email, BookingQuote accepted) {
        if (accepted == null)
            throw new IllegalArgumentException("Phiên xác nhận không hợp lệ. Vui lòng xem lại báo giá.");
        Account account = requireCustomer(email, true);
        CustomerProfile customer = customers.findByAccount_Email(email)
            .orElseThrow(() -> new IllegalArgumentException("Vui lòng hoàn thiện hồ sơ."));
        em.refresh(customer, LockModeType.PESSIMISTIC_WRITE);
        BookingForm form = accepted.selection();
        DrivingLicense license = licenses.findByCustomer_Account_Email(email).orElse(null);
        if (license != null) em.refresh(license, LockModeType.PESSIMISTIC_WRITE);
        Vehicle vehicle = vehicles.findForUpdate(form.getVehicleId())
            .orElseThrow(() -> new IllegalArgumentException("Xe không còn tồn tại."));
        em.refresh(vehicle);
        String eligibility = eligibility(customer, license, form.getEndAt());
        if (eligibility != null) throw new IllegalArgumentException(eligibility);
        // Customer -> license -> vehicle -> coupon -> existing rentals. All hold transitions use this order.
        BookingQuote current = calculate(form, vehicle, null, true);
        if (!accepted.samePrice(current))
            throw new IllegalArgumentException("Giá hoặc mã giảm đã thay đổi. Vui lòng xem và xác nhận báo giá mới.");
        if (current.amounts().totalAmount().signum() == 0)
            throw new IllegalArgumentException("Đơn có tổng tiền 0 đồng cần chốt chính sách xác nhận; vui lòng đổi hoặc bỏ mã giảm.");
        LocalDateTime now = clock.now();
        validateTime(form, now);
        if (!rentals.conflicting(vehicle.getVehicleId(), form.getStartAt(), form.getEndAt(), now).isEmpty())
            throw new IllegalArgumentException("Xe vừa được đặt trong khoảng giờ này. Vui lòng chọn xe hoặc lịch khác.");
        Rental rental = new Rental();
        rental.setCustomer(customer); rental.setVehicle(vehicle); rental.setPickupStation(vehicle.getStation());
        rental.setStartAt(form.getStartAt()); rental.setEndAt(form.getEndAt());
        rental.setRentalDays(Math.toIntExact(current.days())); rental.setDailyRate(current.dailyRate());
        rental.setRentalAmount(current.amounts().rentalAmount()); rental.setDiscountAmount(current.amounts().discountAmount());
        rental.setTotalAmount(current.amounts().totalAmount()); rental.setCouponCode(normalizeCoupon(form.getCouponCode()));

        rental.setCreatedAt(now); rental.setHoldExpiresAt(now.plusMinutes(15)); rental.setStatus("PENDING_PAYMENT");
        //Nơi để sửa số phút giữ đơn xe
        rentals.saveAndFlush(rental);
        record(rental, null, account.getAccountId(), "BOOKING_CREATED", now);
        em.flush();
        return rental.getRentalId();
    }

    @Transactional
    public Rental view(String email, Integer id) {
        return transition(email, id, false);
    }

    @Transactional
    public void cancel(String email, Integer id) {
        transition(email, id, true);
    }

    @Transactional
    public void expire(Integer id) {
        transition(null, id, false);
    }

    /** Join the caller's IPN transaction and serialize with cancellation and timeout. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Rental lockForPaymentNotification(Integer id) {
        return lockRental(null,id);
    }

    @Transactional(readOnly = true)
    public Rental readOwned(String email, Integer id) {
        Account account = requireCustomer(email, false);
        return rentals.findById(id)
            .filter(r -> r.getCustomer().getAccount().getAccountId().equals(account.getAccountId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<Rental> own(String email) {
        requireCustomer(email, false);
        CustomerProfile customer = customers.findByAccount_Email(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return rentals.findForCustomer(customer.getCustomerId());
    }

    private Rental lockRental(Account actor, Integer id) {
        // Read ownership before acquiring any lock; refresh under locks before deciding a transition.
        Rental rental = rentals.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        CustomerProfile customer = rental.getCustomer();
        if (actor != null && !customer.getAccount().getAccountId().equals(actor.getAccountId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        em.refresh(customer, LockModeType.PESSIMISTIC_WRITE);
        vehicles.findForUpdate(rental.getVehicle().getVehicleId()).orElseThrow();
        if (rental.getCouponCode() != null) coupons.findForUpdate(rental.getCouponCode());
        em.refresh(rental, LockModeType.PESSIMISTIC_WRITE);
        return rental;
    }

    private Rental transition(String email, Integer id, boolean cancel) {
        Account actor = email == null ? null : requireCustomer(email, true);
        Rental rental = lockRental(actor,id);
        LocalDateTime now = clock.now();
        boolean expired = rental.isPending() && !rental.hasActiveHold(now);
        if (expired || (cancel && rental.isPending())) {
            String previous = rental.getStatus();
            rental.setStatus("CANCELLED");
            record(rental, previous, expired || actor == null ? null : actor.getAccountId(),
                expired ? "PAYMENT_TIMEOUT" : "CUSTOMER_CANCELLED", now);
        } else if (cancel && "BOOKED".equalsIgnoreCase(rental.getStatus())) {
            boolean beforePickup = now.isBefore(rental.getStartAt());
            cancelBooked(rental, actor, beforePickup ? "BR39" : "XN01: cần xác định quyền hoàn", now);
        } else if (cancel && !"CANCELLED".equalsIgnoreCase(rental.getStatus())) {
            throw new IllegalArgumentException("Chỉ được hủy đơn đang chờ thanh toán.");
        }
        // Initialize display data inside the transaction; the template never needs another customer's data.
        rental.getVehicle().getVehicleModel().getModelName(); rental.getPickupStation().getStationName();
        return rental;
    }

    @Transactional
    public void cancelForNoReplacement(String email, int id, String reason) {
        Account actor = accounts.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        em.refresh(actor, LockModeType.PESSIMISTIC_WRITE);
        if (!"ADMIN".equalsIgnoreCase(actor.getRole()) || !"ACTIVE".equalsIgnoreCase(actor.getStatus()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        Rental rental = lockRental(null,id);
        if ("CANCELLED".equalsIgnoreCase(rental.getStatus())) return;
        if (reason == null || reason.isBlank() || reason.length()>500)
            throw new IllegalArgumentException("Vui lòng nhập lý do hủy, tối đa 500 ký tự.");
        var incidents = em.createNativeQuery("""
            SELECT MaSuCo FROM bao_cao_su_co WHERE MaDonThue=:id AND MaXe=:vehicle
            AND TrangThaiXuLy='NO_REPLACEMENT' ORDER BY MaSuCo DESC LIMIT 1 FOR UPDATE
            """).setParameter("id",id).setParameter("vehicle",rental.getVehicle().getVehicleId()).getResultList();
        if (incidents.isEmpty()) throw new IllegalArgumentException("Cần kết luận của Staff về việc không có xe thay.");
        cancelBooked(rental,actor,"BR41 – sự cố #"+incidents.get(0)+" – "+reason.trim(),clock.now());
    }

    private void cancelBooked(Rental rental, Account actor, String reason, LocalDateTime now) {
        int id = rental.getRentalId();
        if (!"BOOKED".equalsIgnoreCase(rental.getStatus()) || !em.createNativeQuery(
            "SELECT MaBBGiao FROM bien_ban_giao_xe WHERE MaDonThue=:id FOR UPDATE")
            .setParameter("id",id).getResultList().isEmpty())
            throw new IllegalArgumentException("Chỉ hủy được đơn Đã đặt và chưa bàn giao.");
        var sources = em.createNativeQuery("""
            SELECT g.MaGD,g.SoTien FROM giao_dich_thanh_toan g
            WHERE g.MaDonThue=:id AND g.TrangThaiGD IN ('SUCCESS','THÀNH CÔNG')
            AND g.LoaiThanhToan IN ('RENTAL','TIỀN THUÊ') FOR UPDATE
            """).setParameter("id",id).getResultList();
        if (!sources.isEmpty()) {
            if (rental.getCouponCode()!=null) coupons.findForUpdate(rental.getCouponCode()).orElseThrow().restoreReservedUse();
        } else {
            reason += " – Cần đối soát nguồn thanh toán lịch sử";
        }
        Vehicle vehicle = rental.getVehicle();
        var other = em.createNativeQuery("""
            SELECT UPPER(TrangThaiDon) FROM don_thue_xe WHERE MaXe=:vehicle AND MaDonThue<>:id
            AND TrangThaiDon IN ('BOOKED','RENTED') FOR UPDATE
            """).setParameter("vehicle",vehicle.getVehicleId()).setParameter("id",id).getResultList();
        boolean maintenance = "Maintenance".equalsIgnoreCase(vehicle.getVehicleStatus()) || !em.createNativeQuery(
            "SELECT MaBaoTri FROM bao_tri_xe WHERE MaXe=:vehicle AND NgayHoanTat IS NULL FOR UPDATE")
            .setParameter("vehicle",vehicle.getVehicleId()).getResultList().isEmpty();
        vehicle.setVehicleStatus(other.contains("RENTED") ? "Rented" : maintenance ? "Maintenance" : other.contains("BOOKED") ? "Booked" : "Available");
        rental.setStatus("CANCELLED");
        record(rental,"BOOKED",actor.getAccountId(),reason,now);
    }

    private BookingQuote calculate(BookingForm form, Vehicle vehicle, String eligibility, boolean locked) {
        LocalDateTime now = clock.now();
        validateTime(form, now);
        if (locked) {
            em.refresh(vehicle.getStation(), LockModeType.PESSIMISTIC_READ);
            em.refresh(vehicle.getVehicleModel(), LockModeType.PESSIMISTIC_READ);
            em.refresh(vehicle.getVehicleModel().getCategory(), LockModeType.PESSIMISTIC_READ);
        }
        validateVehicle(form, vehicle);
        BigDecimal percent = BigDecimal.ZERO;
        String code = normalizeCoupon(form.getCouponCode());
        if (code != null) {
            Coupon coupon = (locked ? coupons.findForUpdate(code) : coupons.findById(code))
                .orElseThrow(() -> new IllegalArgumentException("Mã giảm không tồn tại. Vui lòng đổi hoặc chủ động bỏ mã."));
            now = clock.now();
            validateCoupon(coupon, now);
            long held = locked ? rentals.activeCouponHolds(code, now).size() : activeCouponCount(code, now);
            if (coupon.getRemainingQuantity() <= held)
                throw new IllegalArgumentException("Mã giảm đã hết quyền sử dụng. Vui lòng đổi hoặc chủ động bỏ mã.");
            percent = coupon.getPercent();
        }
        if (!locked && vehicles.searchAvailable(form.getStationId(), form.getCategoryId(), form.getModelId(),
            form.getStartAt(), form.getEndAt(), now).stream().noneMatch(v -> v.getVehicleId().equals(vehicle.getVehicleId()))) {
            throw new IllegalArgumentException("Xe không còn phù hợp với lịch đã chọn. Vui lòng tìm lại.");
        }
        if (locked && !rentals.overdueRentals(vehicle.getVehicleId(), now).isEmpty())
            throw new IllegalArgumentException("Xe đang quá hạn trả và chưa hoàn tất. Vui lòng chọn xe khác hoặc liên hệ trạm.");
        long days = RentalQuoteService.rentalDays(form.getStartAt(), form.getEndAt());
        if (days > Integer.MAX_VALUE) throw new IllegalArgumentException("Khoảng thuê vượt khả năng xử lý.");
        BigDecimal rate = pricing.dailyRate(vehicle.getVehicleModel().getModelId(), form.getStartAt().toLocalDate());
        var amounts = RentalQuoteService.amounts(rate, days, percent);
        return new BookingQuote(form.copy(), vehicle.getVehicleModel().getModelName(), vehicle.getVehicleModel().getImageUrl(),
            vehicle.getVehicleModel().getDescription(), vehicle.getStation().getStationName(), days, rate, percent, amounts, eligibility, now);
    }

    private long activeCouponCount(String code, LocalDateTime now) { return rentals.countCouponHolds(code, now); }

    private Account requireCustomer(String email, boolean lock) {
        Account account = accounts.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        if (lock) em.refresh(account, LockModeType.PESSIMISTIC_WRITE);
        if (!"CUSTOMER".equalsIgnoreCase(account.getRole()) || !"ACTIVE".equalsIgnoreCase(account.getStatus()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản không được phép đặt xe.");
        return account;
    }

    private Vehicle findVehicle(BookingForm form) {
        if (form.getVehicleId() == null) throw new IllegalArgumentException("Vui lòng chọn xe từ kết quả tìm kiếm.");
        return vehicles.findByVehicleId(form.getVehicleId())
            .orElseThrow(() -> new IllegalArgumentException("Xe không tồn tại."));
    }

    public static void validateTime(BookingForm form, LocalDateTime now) {
        if (form.getStationId() == null || form.getStartAt() == null || form.getEndAt() == null)
            throw new IllegalArgumentException("Vui lòng chọn trạm và nhập đủ ngày giờ nhận, trả.");
        if (form.getStartAt().isBefore(now)) throw new IllegalArgumentException("Giờ nhận xe không được nằm trong quá khứ.");
        if (!form.getEndAt().isAfter(form.getStartAt())) throw new IllegalArgumentException("Giờ trả xe phải sau giờ nhận.");
        if (form.getStartAt().getNano() % 1000 != 0 || form.getEndAt().getNano() % 1000 != 0)
            throw new IllegalArgumentException("Độ chính xác thời gian không hợp lệ. Vui lòng chọn lại ngày giờ.");
    }

    private void validateVehicle(BookingForm f, Vehicle v) {
        var model = v.getVehicleModel();
        if (!Objects.equals(v.getStation().getStationId(), f.getStationId())
            || !"ACTIVE".equalsIgnoreCase(v.getStation().getStationStatus())
            || !"ACTIVE".equalsIgnoreCase(model.getModelStatus())
            || !"ACTIVE".equalsIgnoreCase(model.getCategory().getCategoryStatus())
            || "MAINTENANCE".equalsIgnoreCase(v.getVehicleStatus())
            || (f.getModelId() != null && !f.getModelId().equals(model.getModelId()))
            || (f.getCategoryId() != null && !f.getCategoryId().equals(model.getCategory().getCategoryId())))
            throw new IllegalArgumentException("Xe không phù hợp với trạm, loại, mẫu hoặc đang bảo trì.");
    }

    public static String eligibility(CustomerProfile customer, DrivingLicense license, LocalDateTime end) {
        if (customer.getPhone() == null || customer.getPhone().isBlank()) return "Vui lòng bổ sung số điện thoại trong hồ sơ.";
        if (license == null) return "Vui lòng khai GPLX và yêu cầu xác minh trước khi đặt xe.";
        if (!"B".equals(license.getLicenseClass())) return "Cần GPLX hạng B để thuê xe.";
        if (!DrivingLicense.VERIFIED.equals(license.getVerificationStatus())) return "GPLX chưa được xác minh. Vui lòng chờ hoặc liên hệ nhân viên xác minh.";
        if (end == null || license.getExpiryDate() == null || license.getExpiryDate().isBefore(end.toLocalDate()))
            return "GPLX phải còn hiệu lực đến hết thời gian thuê.";
        return null;
    }

    public static void validateCoupon(Coupon coupon, LocalDateTime now) {
        if (!"ACTIVE".equalsIgnoreCase(coupon.getStatus()) || coupon.getStartsAt() == null || coupon.getEndsAt() == null
            || now.isBefore(coupon.getStartsAt()) || now.isAfter(coupon.getEndsAt())
            || coupon.getRemainingQuantity() == null || coupon.getRemainingQuantity() <= 0
            || coupon.getPercent() == null || coupon.getPercent().signum() < 0 || coupon.getPercent().compareTo(BigDecimal.valueOf(100)) > 0)
            throw new IllegalArgumentException("Mã giảm không còn hợp lệ. Vui lòng đổi hoặc chủ động bỏ mã.");
    }

    public static String normalizeCoupon(String code) {
        if (code == null || code.isBlank()) return null;
        if (code.trim().length() > 50) throw new IllegalArgumentException("Mã giảm tối đa 50 ký tự.");
        return code.trim();
    }

    private void record(Rental rental, String previous, Integer actor, String reason, LocalDateTime now) {
        RentalHistory history = new RentalHistory();
        history.setRental(rental); history.setPreviousStatus(previous); history.setNewStatus(rental.getStatus());
        history.setActorId(actor); history.setReason(reason); history.setChangedAt(now); em.persist(history);
        BookingNotification notification = new BookingNotification();
        notification.setAccountId(rental.getCustomer().getAccount().getAccountId());
        notification.setTitle(previous == null ? "Đã tạo đơn chờ thanh toán" : "Đơn thuê đã hủy");
        notification.setContent("Đơn #" + rental.getRentalId() + (previous == null
            ? ": vui lòng thanh toán trước " + rental.getHoldExpiresAt().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy"))
            : ("PAYMENT_TIMEOUT".equals(reason) ? ": đã hết hạn thanh toán." : ": đã hủy. " + reason)));
        notification.setSentAt(now); em.persist(notification);
    }
}
