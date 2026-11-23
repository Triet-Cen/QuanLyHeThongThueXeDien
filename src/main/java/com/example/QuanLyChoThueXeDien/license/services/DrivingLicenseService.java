package com.example.QuanLyChoThueXeDien.license.services;
import com.example.QuanLyChoThueXeDien.auth.entities.CustomerProfile;
import com.example.QuanLyChoThueXeDien.auth.repositories.CustomerProfileRepository;
import com.example.QuanLyChoThueXeDien.license.dtos.DrivingLicenseForm;
import com.example.QuanLyChoThueXeDien.license.entities.*;
import com.example.QuanLyChoThueXeDien.license.repositories.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;
import com.example.QuanLyChoThueXeDien.auth.entities.Account;
import com.example.QuanLyChoThueXeDien.auth.repositories.AccountRepository;

@Service
public class DrivingLicenseService {
    private final DrivingLicenseRepository repository;
    private final CustomerProfileRepository customers;
    private final LicenseVerifierRepository verifiers;
    private final LicenseImageStorage images;
    private final EntityManager entityManager;
    private final AccountRepository accounts;


    public DrivingLicenseService(DrivingLicenseRepository repository
            , CustomerProfileRepository customers,
            LicenseVerifierRepository verifiers
            , LicenseImageStorage images
            , EntityManager entityManager
    , AccountRepository accounts) {
        this.repository = repository;
        this.customers = customers;
        this.verifiers = verifiers;
        this.images = images;
        this.entityManager = entityManager;
        this.accounts= accounts;
    }


    @Transactional(readOnly = true)
    public DrivingLicense own(String email) {
        return repository.findByCustomer_Account_Email(email).orElse(null);
    }

    @Transactional
    public void submit(String email, DrivingLicenseForm form) {
        CustomerProfile customer = customers.findByAccount_Email(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ."));
        // Sắp xếp theo trình tự thời gian đối với lần tạo mới đầu tiên và các lần thay thế cho cùng một khách hàng.
        entityManager.lock(customer, LockModeType.PESSIMISTIC_WRITE);
        if (form.getLicenseNumber() == null || form.getLicenseNumber().isBlank()
                || form.getLicenseNumber().trim().length() > 255 || !"B".equals(form.getLicenseClass()))
            throw new IllegalArgumentException("Vui lòng nhập số GPLX và chọn hạng B.");

        if (form.getIssueDate() == null || form.getExpiryDate() == null
                || !form.getExpiryDate().isAfter(form.getIssueDate()))
            throw new IllegalArgumentException("Ngày hết hạn phải sau ngày cấp.");

        DrivingLicense current = own(email);

        if (current != null) entityManager.lock(current, LockModeType.PESSIMISTIC_WRITE);

        String number = form.getLicenseNumber().trim();
        repository.findByLicenseNumber(number).filter(other ->
            current == null || !other.getLicenseId().equals(current.getLicenseId()))
            .ifPresent(other -> { throw new IllegalArgumentException("Số GPLX đã được khách hàng khác sử dụng."); });
        String image = images.save(form.getImage());
        String oldImage = current == null ? null : current.getImagePath();
        // Keep old image on rollback; remove new image if the DB write fails.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) images.delete(oldImage);
                else images.delete(image);
            }
        });
        DrivingLicense license = current == null ? new DrivingLicense() : current;
        license.setCustomer(customer);
        license.setLicenseNumber(number);
        license.setLicenseClass("B");
        license.setIssueDate(form.getIssueDate());
        license.setExpiryDate(form.getExpiryDate());
        license.setImagePath(image);
        license.setVerificationStatus(DrivingLicense.UNVERIFIED);
        license.setReviewedBy(null);
        license.setVerifiedBy(null);
        license.setVerifiedAt(null);
        repository.saveAndFlush(license);
    }


    @Transactional(readOnly = true)
    public List<DrivingLicense> list(String staffEmail) {
        requireVerifier(staffEmail);
        return repository.findAllByOrderByLicenseIdDesc();
    }


    @Transactional(readOnly = true)
    public DrivingLicense detail(Integer id, String staffEmail) {
        requireVerifier(staffEmail);
        DrivingLicense license = find(id);
        // Populate the customer display fields while the transaction is open.
        license.getCustomer().getFullName();
        license.getCustomer().getAccount().getEmail();
        if (license.getReviewedBy() != null) {
            license.getReviewedBy().getEmail();
        }
        return license;
    }



    @Transactional
    public void verify(Integer id, String staffEmail, String expectedImage, String decision) {
        Account reviewer = requireVerifier(staffEmail);
        DrivingLicense license = repository.findForUpdate(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!Objects.equals(expectedImage, license.getImagePath()))
            throw new IllegalArgumentException("GPLX đã được thay thế. Vui lòng tải lại và kiểm tra bản mới.");
        if (!DrivingLicense.VERIFIED.equals(decision) && !DrivingLicense.REJECTED.equals(decision))
            throw new IllegalArgumentException("Kết quả xác minh không hợp lệ.");
        // Staff may reject a previously verified license found invalid during later checks.
        if (!DrivingLicense.UNVERIFIED.equals(license.getVerificationStatus())
                && !(DrivingLicense.VERIFIED.equals(license.getVerificationStatus()) && DrivingLicense.REJECTED.equals(decision)))
            throw new IllegalArgumentException("Hồ sơ này đã được xử lý. Vui lòng tải lại trang.");
        if (DrivingLicense.VERIFIED.equals(decision)
                && (!"B".equals(license.getLicenseClass()) || license.getExpiryDate().isBefore(today())))
            throw new IllegalArgumentException("GPLX không phải hạng B hoặc đã hết hạn; không thể duyệt.");
        license.setVerificationStatus(decision);
        license.setVerifiedBy(
                "STAFF".equalsIgnoreCase(reviewer.getRole())
                        ? verifiers.findByAccount_Email(staffEmail).orElseThrow()
                        : null
        );
        license.setVerifiedAt(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")));
    }


    @Transactional(readOnly = true)
    public ImageData ownImage(String email) {
        DrivingLicense license = own(email);
        if (license == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return image(license);
    }


    @Transactional(readOnly = true)
    public ImageData staffImage(Integer id, String email) {
        requireVerifier(email);
        return image(find(id));
    }



    @Transactional(readOnly = true)
    public boolean eligibleForRental(String email, LocalDate rentalEnd) {
        DrivingLicense license = own(email);
        return rentalEnd != null && license != null
            && license.getCustomer().getPhone() != null && !license.getCustomer().getPhone().isBlank()
            && "B".equals(license.getLicenseClass())
            && DrivingLicense.VERIFIED.equals(license.getVerificationStatus())
            && !license.getExpiryDate().isBefore(today()) && !license.getExpiryDate().isBefore(rentalEnd);
    }


    private DrivingLicense find(Integer id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }


    private Account requireVerifier(String email) {
        Account account = accounts.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Không tìm thấy tài khoản xét duyệt."
                ));

        boolean isAdmin = "ADMIN".equalsIgnoreCase(account.getRole());
        boolean isStaff = "STAFF".equalsIgnoreCase(account.getRole());

        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())
                || (!isAdmin && !isStaff)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Bạn không có quyền xét duyệt GPLX."
            );
        }

        // Giữ điều kiện Staff phải có hồ sơ nhân viên như trước.
        if (isStaff && verifiers.findByAccount_Email(email).isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Tài khoản chưa có hồ sơ nhân viên."
            );
        }

        return account;
    }


    private ImageData image(DrivingLicense license) {
        return new ImageData(images.read(license.getImagePath()),
            license.getImagePath().endsWith(".png") ? "image/png" : "image/jpeg");
    }
    private LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")); }

    public record ImageData(byte[] bytes, String contentType) {}
}
