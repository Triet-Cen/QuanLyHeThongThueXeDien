package com.example.QuanLyChoThueXeDien.pricing.repositories;

import com.example.QuanLyChoThueXeDien.pricing.entities.RentalPrice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface RentalPriceRepository extends JpaRepository<RentalPrice, Integer> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @Query("""
        select p from RentalPrice p where p.vehicleModel.modelId = :modelId
        and p.startDate <= :date and (p.endDate is null or p.endDate >= :date)
        """)
    List<RentalPrice> findEffectiveForModel(@Param("modelId") Integer modelId, @Param("date") LocalDate date);

    @EntityGraph(attributePaths = "vehicleModel")
    List<RentalPrice> findAllByOrderByStartDateDescPriceIdDesc();

    @Query("""
            select p from RentalPrice p
            where p.vehicleModel.modelId = :modelId
              and (p.endDate is null or p.endDate >= :startDate)
              and (:endDate is null or p.startDate <= :endDate)
            """)
    List<RentalPrice> findOverlapping(
            @Param("modelId") Integer modelId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
    //findOverlapping() tìm các giá của cùng mẫu xe có ít nhất một ngày trùng với kỳ mới


    @Query("""
        select p from RentalPrice p
        join fetch p.vehicleModel
        where p.startDate <= :today
          and (p.endDate is null or p.endDate >= :today)
        order by p.startDate desc, p.priceId desc
        """)
    List<RentalPrice> findEffectivePrices(@Param("today") LocalDate today);
}

