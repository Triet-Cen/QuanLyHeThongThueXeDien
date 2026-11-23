package com.example.QuanLyChoThueXeDien.vehicle.controllers;


import com.example.QuanLyChoThueXeDien.VehicleModel.entities.VehicleModel;
import com.example.QuanLyChoThueXeDien.VehicleModel.services.VehicleModelService;
import com.example.QuanLyChoThueXeDien.vehicle.service.VehicleService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

@Controller
public class CustomerVehicleModelController {

    private final VehicleModelService modelService;
    private final VehicleService vehicleService;


    public CustomerVehicleModelController(VehicleModelService modelService,
                                          VehicleService vehicleService) {
        this.modelService = modelService;
        this.vehicleService = vehicleService;
    }

    @GetMapping("/models/{id}")
    public  String detail (
            @PathVariable("id") Integer id,
            Principal principal,
            Model model
    )
    {
        VehicleModel vehicleModel= modelService.findForEdit(id)
                .filter(item->"ACTIVE".equals(item.getModelStatus()))
                .filter(item->
                        "ACTIVE".equals(item.getCategory().getCategoryStatus()))
                .orElseThrow(()-> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Mẫu xe không tồn tại"
                ));

        model.addAttribute("email", principal.getName());
        model.addAttribute("vehicleModel", vehicleModel);
        model.addAttribute("vehicles", vehicleService.findByModelId(id));

        return "user/model-detail";
    }
}
