package com.example.QuanLyChoThueXeDien.license;
import com.example.QuanLyChoThueXeDien.auth.entities.CustomerProfile;
import com.example.QuanLyChoThueXeDien.auth.repositories.CustomerProfileRepository;
import com.example.QuanLyChoThueXeDien.license.dtos.DrivingLicenseForm;
import com.example.QuanLyChoThueXeDien.license.entities.*;
import com.example.QuanLyChoThueXeDien.license.repositories.*;
import com.example.QuanLyChoThueXeDien.license.services.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.*;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DrivingLicenseServiceTests {
    DrivingLicenseRepository licenses = mock(DrivingLicenseRepository.class);
    CustomerProfileRepository customers = mock(CustomerProfileRepository.class);
    LicenseVerifierRepository staff = mock(LicenseVerifierRepository.class);
    LicenseImageStorage images = mock(LicenseImageStorage.class);
    com.example.QuanLyChoThueXeDien.auth.repositories.AccountRepository accounts = mock(com.example.QuanLyChoThueXeDien.auth.repositories.AccountRepository.class);
    DrivingLicenseService service = new DrivingLicenseService(licenses, customers, staff, images, mock(EntityManager.class), accounts);
    CustomerProfile customer = new CustomerProfile();
    DrivingLicense license = new DrivingLicense();
    DrivingLicenseForm form = new DrivingLicenseForm();
    @BeforeEach void setup() {
        var reviewer = new com.example.QuanLyChoThueXeDien.auth.entities.Account();
        reviewer.setRole("STAFF"); reviewer.setStatus("ACTIVE");
        when(accounts.findByEmail("staff")).thenReturn(Optional.of(reviewer));
        customer.setPhone("0901234567");
        license.setLicenseId(1); license.setCustomer(customer); license.setLicenseNumber("123");
        license.setLicenseClass("B"); license.setIssueDate(LocalDate.of(2020,1,1));
        license.setExpiryDate(LocalDate.now().plusYears(1)); license.setImagePath("old.jpg");
        license.setVerificationStatus(DrivingLicense.VERIFIED);
        when(customers.findByAccount_Email("customer")).thenReturn(Optional.of(customer));
        when(licenses.findByCustomer_Account_Email("customer")).thenReturn(Optional.of(license));
        when(licenses.findForUpdate(1)).thenReturn(Optional.of(license));
        when(staff.findByAccount_Email("staff")).thenReturn(Optional.of(new LicenseVerifier()));
        form.setLicenseNumber("123"); form.setLicenseClass("B");
        form.setIssueDate(LocalDate.of(2021,1,1)); form.setExpiryDate(LocalDate.of(2030,1,1));
    }
    @Test void replacementResetsVerificationAndCleansImagesOnlyAfterCommit() {
        when(images.save(null)).thenReturn("new.jpg");
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.submit("customer", form);
            assertEquals(DrivingLicense.UNVERIFIED, license.getVerificationStatus());
            assertNull(license.getVerifiedBy()); assertNull(license.getVerifiedAt());
            verify(images, never()).delete(anyString());
            TransactionSynchronizationManager.getSynchronizations().getFirst().afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            verify(images).delete("old.jpg");
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
    @Test void rollbackRemovesNewImageAndKeepsOldImage() {
        when(images.save(null)).thenReturn("new.jpg");
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.submit("customer", form);
            TransactionSynchronizationManager.getSynchronizations().getFirst().afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            verify(images).delete("new.jpg"); verify(images, never()).delete("old.jpg");
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
    @Test void duplicateNumberPreservesCurrentLicense() {
        DrivingLicense other = new DrivingLicense(); other.setLicenseId(2);
        when(licenses.findByLicenseNumber("123")).thenReturn(Optional.of(other));
        assertThrows(IllegalArgumentException.class, () -> service.submit("customer", form));
        assertEquals("old.jpg", license.getImagePath()); verifyNoInteractions(images);
    }
    @Test void equalDatesAndWrongClassAreRejected() {
        form.setExpiryDate(form.getIssueDate());
        assertThrows(IllegalArgumentException.class, () -> service.submit("customer", form));
        form.setExpiryDate(LocalDate.of(2030,1,1)); form.setLicenseClass("A");
        assertThrows(IllegalArgumentException.class, () -> service.submit("customer", form));
        verifyNoInteractions(images);
    }
    @Test void staleReviewDoesNotApproveReplacement() {
        license.setVerificationStatus(DrivingLicense.UNVERIFIED);
        assertThrows(IllegalArgumentException.class, () -> service.verify(1,"staff","stale.jpg",DrivingLicense.VERIFIED));
        assertEquals(DrivingLicense.UNVERIFIED, license.getVerificationStatus());
    }
    @Test void expiredLicenseCannotBeApprovedButCanBeRejected() {
        license.setVerificationStatus(DrivingLicense.UNVERIFIED);
        license.setExpiryDate(LocalDate.now().minusDays(2));
        assertThrows(IllegalArgumentException.class, () -> service.verify(1,"staff","old.jpg",DrivingLicense.VERIFIED));
        service.verify(1,"staff","old.jpg",DrivingLicense.REJECTED);
        assertEquals(DrivingLicense.REJECTED, license.getVerificationStatus());
        assertNotNull(license.getVerifiedBy()); assertNotNull(license.getVerifiedAt());
    }
    @Test void approvalRecordsStaffAndTime() {
        license.setVerificationStatus(DrivingLicense.UNVERIFIED);
        service.verify(1,"staff","old.jpg",DrivingLicense.VERIFIED);
        assertEquals(DrivingLicense.VERIFIED, license.getVerificationStatus());
        assertNotNull(license.getVerifiedAt()); assertNotNull(license.getVerifiedBy());
    }
    @Test void missingStaffProfileIsForbidden() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.list("unknown"));
    }
    @Test void rentalMustBeCoveredThroughEndDate() {
        assertTrue(service.eligibleForRental("customer", LocalDate.now().plusDays(1)));
        assertFalse(service.eligibleForRental("customer", LocalDate.now().plusYears(2)));
        license.setVerificationStatus(DrivingLicense.UNVERIFIED);
        assertFalse(service.eligibleForRental("customer", LocalDate.now().plusDays(1)));
    }
}
