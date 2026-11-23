package com.example.QuanLyChoThueXeDien.category.controllers;


import com.example.QuanLyChoThueXeDien.category.dtos.VehicleCategoryForm;
import com.example.QuanLyChoThueXeDien.category.entities.VehicleCategory;
import com.example.QuanLyChoThueXeDien.category.services.VehicleCategoryService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
@RequestMapping("/admin/categories")
public class AdminCategoryController {

    private final VehicleCategoryService vehicleCategoryService;

    public AdminCategoryController(VehicleCategoryService vehicleCategoryService) {
        this.vehicleCategoryService = vehicleCategoryService;
    }

    @ModelAttribute("adminName")
    public String adminName(Principal principal){
        return principal.getName();
    }
    //ham viet goi ten admin khi lam viec voi cac thao tac tren controller

    @GetMapping
    public String list(Model model) {
        model.addAttribute("categories", vehicleCategoryService.findAll());
        return "admin/catalog/categories";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("categoryForm", new VehicleCategoryForm());
        return "admin/catalog/category-form";
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute("categoryForm") VehicleCategoryForm form,
            BindingResult result,
            RedirectAttributes redirectAttributes
    ){
        if(result.hasErrors()){
            return "/admin/catalog/category-form";
        }
        vehicleCategoryService.create(form.getCategoryName(), form.getDescription());
        redirectAttributes.addFlashAttribute("successMessage","Đã thêm loại xe");
        return "redirect:/admin/categories";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(
            @PathVariable("id") Integer id,
            Model model,
            RedirectAttributes redirectAttributes
    ){
        VehicleCategory category= vehicleCategoryService.findById(id).orElse(null);

        if(category==null){
            redirectAttributes.addFlashAttribute("errorMessage","Không tìm thấy loại xe");
            return "redirect:/admin/categories";
        }

        VehicleCategoryForm form= new VehicleCategoryForm();
        form.setCategoryName(category.getCategoryName());
        form.setDescription(category.getDescription());

        model.addAttribute("categoryId", id);
        model.addAttribute("categoryForm", form);
        return "admin/catalog/category-form";

    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable("id") Integer id,
            @Valid @ModelAttribute("categoryForm") VehicleCategoryForm form,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (result.hasErrors()) {
            model.addAttribute("categoryId", id);
            return "admin/catalog/category-form";
        }

        if (vehicleCategoryService.update(id, form.getCategoryName(), form.getDescription())) {
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật loại xe.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Loại xe không còn tồn tại.");
        }

        return "redirect:/admin/categories";
    }

    @PostMapping("/{id}/visibility")
    public String changeVisibility(
            @PathVariable("id") Integer id,
            @RequestParam("visible") boolean visible,
            RedirectAttributes redirectAttributes
    ) {
        if (vehicleCategoryService.changeVisibility(id, visible)) {
            redirectAttributes.addFlashAttribute(
                    "successMessage", visible ? "Đã hiện lại loại xe." : "Đã ẩn loại xe."
            );
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Loại xe không tồn tại.");
        }

        return "redirect:/admin/categories";
    }

}
