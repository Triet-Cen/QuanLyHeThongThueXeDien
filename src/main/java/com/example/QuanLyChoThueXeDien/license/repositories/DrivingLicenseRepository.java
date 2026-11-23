package com.example.QuanLyChoThueXeDien.license.repositories;
import com.example.QuanLyChoThueXeDien.license.entities.DrivingLicense;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface DrivingLicenseRepository extends JpaRepository<DrivingLicense, Integer> {
    Optional<DrivingLicense> findByCustomer_Account_Email(String email);

    Optional<DrivingLicense> findByLicenseNumber(String number);

    @EntityGraph(attributePaths = {"customer", "customer.account"})
    List<DrivingLicense> findAllByOrderByLicenseIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from DrivingLicense l where l.licenseId = :id")
    Optional<DrivingLicense> findForUpdate(@Param("id") Integer id);
}
