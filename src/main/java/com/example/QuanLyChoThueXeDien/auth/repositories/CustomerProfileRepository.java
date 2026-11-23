package com.example.QuanLyChoThueXeDien.auth.repositories;

import com.example.QuanLyChoThueXeDien.auth.entities.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, Integer> {

    Optional<CustomerProfile> findByAccount_Email(String email);

}
