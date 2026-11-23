package com.example.QuanLyChoThueXeDien.rental.repositories;

import com.example.QuanLyChoThueXeDien.rental.entities.Rental;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;

public interface RentalRepository extends JpaRepository<Rental, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Rental r where r.rentalId = :id")
    Optional<Rental> findForUpdate(@Param("id") Integer id);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select r from Rental r where r.vehicle.vehicleId = :vehicleId
        and r.startAt < :endAt and r.endAt > :startAt
        and (upper(r.status) in ('BOOKED','RENTED') or
          (upper(r.status) in ('PENDING_PAYMENT','CHỜ THANH TOÁN') and r.holdExpiresAt > :now))
        """)
    List<Rental> conflicting(@Param("vehicleId") Integer vehicleId, @Param("startAt") LocalDateTime startAt,
        @Param("endAt") LocalDateTime endAt, @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select r from Rental r where r.couponCode = :code
        and upper(r.status) in ('PENDING_PAYMENT','CHỜ THANH TOÁN') and r.holdExpiresAt > :now
        """)
    List<Rental> activeCouponHolds(@Param("code") String code, @Param("now") LocalDateTime now);

    @Query("""
        select count(r) from Rental r where r.couponCode = :code
        and upper(r.status) in ('PENDING_PAYMENT','CHỜ THANH TOÁN') and r.holdExpiresAt > :now
        """)
    long countCouponHolds(@Param("code") String code, @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select r from Rental r where r.vehicle.vehicleId = :vehicleId
        and upper(r.status) = 'RENTED' and r.endAt <= :now
        """)
    List<Rental> overdueRentals(@Param("vehicleId") Integer vehicleId, @Param("now") LocalDateTime now);

    @Query("""
        select r.rentalId from Rental r where upper(r.status) in ('PENDING_PAYMENT','CHỜ THANH TOÁN')
        and r.holdExpiresAt <= :now order by r.rentalId
        """)
    List<Integer> expiredIds(@Param("now") LocalDateTime now, Pageable page);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.vehicleModel", "pickupStation"})
    @Query("select r from Rental r where r.customer.CustomerId = :customerId order by r.rentalId desc")
    List<Rental> findForCustomer(@Param("customerId") Integer customerId);
}
