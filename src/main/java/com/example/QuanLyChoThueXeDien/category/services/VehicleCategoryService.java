package com.example.QuanLyChoThueXeDien.category.services;

import com.example.QuanLyChoThueXeDien.category.entities.VehicleCategory;
import com.example.QuanLyChoThueXeDien.category.repositories.VehicleCategoryRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;


@Service
public class VehicleCategoryService {


    private final VehicleCategoryRepository vehicleCategoryRepository;

    public VehicleCategoryService(VehicleCategoryRepository vehicleCategoryRepository) {
        this.vehicleCategoryRepository = vehicleCategoryRepository;
    }

    public List<VehicleCategory> findAll(){
        return vehicleCategoryRepository.findAll(Sort.by("categoryId"));
    }

    public Optional<VehicleCategory> findById(Integer id){
        return vehicleCategoryRepository.findById(id);
    }

    public void create(String name, String description){
         vehicleCategoryRepository.save(new VehicleCategory(name,description));
    }

    @Transactional
    public boolean update(Integer id, String name, String description){
        VehicleCategory category= vehicleCategoryRepository.findById(id).orElse(null);


        if(category==null){
            return false;
        }

        category.updateDetails(name, description);
        return true;
    }

    @Transactional
    public boolean changeVisibility(Integer id, boolean visible) {
        VehicleCategory category = vehicleCategoryRepository.findById(id).orElse(null);
        if (category == null) {
            return false;
        }

        category.setCategoryStatus(visible ? "ACTIVE" : "INACTIVE");
        return true;
    }



}
