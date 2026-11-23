package com.example.QuanLyChoThueXeDien.license.repositories;
import com.example.QuanLyChoThueXeDien.license.entities.LicenseVerifier;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface LicenseVerifierRepository extends JpaRepository<LicenseVerifier, Integer> {
    Optional<LicenseVerifier> findByAccount_Email(String email);
}
