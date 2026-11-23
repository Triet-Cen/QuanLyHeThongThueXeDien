package com.example.QuanLyChoThueXeDien.VehicleModel.repositories;

import com.example.QuanLyChoThueXeDien.VehicleModel.entities.VehicleModel;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;


public interface VehicleModelRepository extends JpaRepository<VehicleModel, Integer> {

    @EntityGraph(attributePaths = "category")
    List<VehicleModel> findAllByOrderByModelIdAsc();


    @EntityGraph(attributePaths = "category")
    Optional<VehicleModel> findByModelId(Integer modelId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from VehicleModel m where m.modelId = :id")
    Optional<VehicleModel> findByIdForPriceUpdate(@Param("id") Integer id);


}
