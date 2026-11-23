package com.example.QuanLyChoThueXeDien.vehicle.controllers;


import com.example.QuanLyChoThueXeDien.vehicle.dtos.VehicleForm;
import com.example.QuanLyChoThueXeDien.vehicle.entities.Vehicle;
import com.example.QuanLyChoThueXeDien.vehicle.service.VehicleService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("admin/vehicles")
public class AdminVehicleController {

    private final VehicleService vehicleService;

    public AdminVehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("vehicles", vehicleService.findAll());
        return "admin/catalog/vehicles";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("vehicleForm", new VehicleForm());
        return renderForm(null, model);
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable("id") Integer id, Model model,
                           RedirectAttributes redirectAttributes) {
        Vehicle vehicle = vehicleService.findForEdit(id).orElse(null);
        if (vehicle == null) {
            return vehicleNotFound(redirectAttributes);
        }

        VehicleForm form = new VehicleForm();
        form.setModelId(vehicle.getVehicleModel().getModelId());
        form.setStationId(vehicle.getStation().getStationId());
        form.setLicensePlate(vehicle.getLicensePlate());
        form.setColor(vehicle.getColor());
        form.setBatteryLevel(vehicle.getBatteryLevel());

        model.addAttribute("vehicleForm", form);
        return renderForm(vehicle, model);
    }

    @PostMapping({"", "/{id}"})
    public String save(@PathVariable(name = "id", required = false) Integer id,
                       @Valid @ModelAttribute("vehicleForm") VehicleForm form,
                       BindingResult errors, Model model,
                       RedirectAttributes redirectAttributes) {
        Vehicle current = id == null ? null : vehicleService.findForEdit(id).orElse(null);
        if (id != null && current == null) {
            return vehicleNotFound(redirectAttributes);
        }

        if (errors.hasErrors()) {
            return renderForm(current, model);
        }

        try {
            if (!vehicleService.save(id, form)) {
                return vehicleNotFound(redirectAttributes);
            }
        } catch (IllegalArgumentException exception) {
            errors.reject("vehicle.invalid", exception.getMessage());
            return renderForm(current, model);
        } catch (DataIntegrityViolationException exception) {
            errors.reject("vehicle.conflict",
                    "Không lưu được xe. Hãy kiểm tra biển số, mẫu xe và trạm xe.");
            return renderForm(current, model);
        }

        redirectAttributes.addFlashAttribute(
                "successMessage", id == null ? "Đã thêm xe." : "Đã cập nhật xe.");
        return "redirect:/admin/vehicles";
    }

    private String renderForm(Vehicle vehicle, Model model) {
        model.addAttribute("vehicleId", vehicle == null ? null : vehicle.getVehicleId());
        model.addAttribute("vehicleModels", vehicleService.modelsForForm(
                vehicle == null ? null : vehicle.getVehicleModel().getModelId()));
        model.addAttribute("stations", vehicleService.stationsForForm(
                vehicle == null ? null : vehicle.getStation().getStationId()));
        return "admin/catalog/vehicle-form";
    }

    private String vehicleNotFound(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", "Xe không tồn tại.");
        return "redirect:/admin/vehicles";
    }
}
