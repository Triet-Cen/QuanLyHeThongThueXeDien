package com.example.QuanLyChoThueXeDien.category.repositories;

import com.example.QuanLyChoThueXeDien.category.entities.VehicleCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleCategoryRepository
        extends JpaRepository<VehicleCategory, Integer> {

    @Modifying
    @Query("delete from VehicleCategory c where c.categoryId = :id")
    int deleteCategory(@Param("id") Integer id);
}