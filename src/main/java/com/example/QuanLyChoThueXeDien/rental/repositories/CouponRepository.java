package com.example.QuanLyChoThueXeDien.rental.repositories;

import com.example.QuanLyChoThueXeDien.rental.entities.Coupon;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.code = :code")
    Optional<Coupon> findForUpdate(@Param("code") String code);
}
