package com.example.QuanLyChoThueXeDien.VehicleModel.controllers;


import com.example.QuanLyChoThueXeDien.VehicleModel.dtos.VehicleModelForm;
import com.example.QuanLyChoThueXeDien.VehicleModel.entities.VehicleModel;
import com.example.QuanLyChoThueXeDien.VehicleModel.services.VehicleModelService;
import com.example.QuanLyChoThueXeDien.category.entities.VehicleCategory;
import com.example.QuanLyChoThueXeDien.category.services.VehicleCategoryService;
import jakarta.validation.Valid;
import org.springframework.boot.Banner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
@RequestMapping("/admin/models")
public class AdminVehicleModelController {

    private final VehicleCategoryService categoryService;
    private final VehicleModelService modelService;

    public AdminVehicleModelController(VehicleCategoryService categoryService, VehicleModelService modelService) {
        this.categoryService = categoryService;
        this.modelService = modelService;
    }

    @ModelAttribute("adminName")
    public String adminName(Principal principal){
        return principal.getName();
    }


    @GetMapping
    public String list(Model model){
        model.addAttribute("vehicleModels", modelService.findAll());
        return "/admin/catalog/models";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model){
        model.addAttribute("vehicleModelForm", new VehicleModelForm());
        return renderForm(null, model);

    }

    @GetMapping("/{id}/edit")
    public String showEditForm(
            @PathVariable("id") Integer id,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        VehicleModel vehicleModel = modelService.findForEdit(id).orElse(null);//tim id cua mau xe do de sua hoac de null neu khong co

        if (vehicleModel == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy mẫu xe.");
            return "redirect:/admin/models";
        }

        VehicleModelForm form = new VehicleModelForm(); //Hien form da co thong tin truoc do khi nhan sua
        form.setModelName(vehicleModel.getModelName());
        form.setBrand(vehicleModel.getBrand());
        form.setSeatCount(vehicleModel.getSeatCount());
        form.setDescription(vehicleModel.getDescription());
        form.setCategoryId(vehicleModel.getCategory().getCategoryId());

        model.addAttribute("vehicleModelForm", form);
        return renderForm(id, model);
    }

    @PostMapping({"", "/{id}"})
    public String save(
            @PathVariable(value = "id", required = false) Integer id,
            @Valid @ModelAttribute("vehicleModelForm") VehicleModelForm form,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (result.hasErrors()) {
            return renderForm(id, model);
        }

        VehicleCategory category = categoryService.findById(form.getCategoryId()).orElse(null);

        if (category == null) {
            result.rejectValue("categoryId", "category.missing", "Loại xe đã chọn không còn tồn tại.");
            return renderForm(id, model);
        }

        try {
            if (modelService.save(id, form, category)) {
                redirectAttributes.addFlashAttribute(
                        "successMessage",
                        id == null ? "Đã thêm mẫu xe." : "Đã cập nhật mẫu xe."
                );
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Mẫu xe không còn tồn tại.");
            }
        } catch (DataIntegrityViolationException exception) {
            result.reject("model.save", "Không thể lưu mẫu xe. Hãy kiểm tra lại loại xe đã chọn.");
            return renderForm(id, model);
        }

        return "redirect:/admin/models";
    }

    @PostMapping("/{id}/visibility")
    public String changeVisibility(
            @PathVariable("id") Integer id,
            @RequestParam("visible") boolean visible,
            RedirectAttributes redirectAttributes
    ) {
        if (modelService.changeVisibility(id, visible)) {
            redirectAttributes.addFlashAttribute(
                    "successMessage", visible ? "Đã hiện lại mẫu xe." : "Đã ẩn mẫu xe."
            );
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Mẫu xe không tồn tại.");
        }

        return "redirect:/admin/models";
    }


    private String renderForm(Integer id, Model model) {
        model.addAttribute("modelId", id);
        model.addAttribute("categories", categoryService.findAll());
        return "admin/catalog/model-form";
    }
}
