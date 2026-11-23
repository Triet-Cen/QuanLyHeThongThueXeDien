package com.example.QuanLyChoThueXeDien.VehicleModel.services;


import com.example.QuanLyChoThueXeDien.VehicleModel.dtos.VehicleModelForm;
import com.example.QuanLyChoThueXeDien.VehicleModel.entities.VehicleModel;
import com.example.QuanLyChoThueXeDien.VehicleModel.repositories.VehicleModelRepository;
import com.example.QuanLyChoThueXeDien.category.entities.VehicleCategory;
import com.example.QuanLyChoThueXeDien.category.repositories.VehicleCategoryRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class VehicleModelService {


    private  final VehicleModelRepository repository;

    public VehicleModelService(VehicleModelRepository repository) {
        this.repository = repository;
    }

    public List<VehicleModel> findAll() {
        return repository.findAllByOrderByModelIdAsc();
    }

    public Optional<VehicleModel> findForEdit(Integer id) {
        return repository.findByModelId(id);
    }

    @Transactional
    public boolean save(Integer id, VehicleModelForm form, VehicleCategory category){
        VehicleModel vehicleModel= id ==null
                ? new VehicleModel() : repository.findById(id).orElse(null);
        if(vehicleModel==null){
            return false;
        }

        vehicleModel.updateDetails(form.getModelName(),
                form.getBrand(),
                form.getSeatCount(),
                form.getDescription(),
                category
        );
        repository.save(vehicleModel);
        return true;

    }

    @Transactional
    public boolean changeVisibility(Integer id, boolean visible) {
        VehicleModel vehicleModel = repository.findById(id).orElse(null);
        if (vehicleModel == null) {
            return false;
        }

        vehicleModel.setModelStatus(visible ? "ACTIVE" : "INACTIVE");
        return true;
    }
}
