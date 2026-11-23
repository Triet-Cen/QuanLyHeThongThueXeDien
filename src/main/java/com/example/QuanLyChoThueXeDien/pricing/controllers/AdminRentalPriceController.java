package com.example.QuanLyChoThueXeDien.pricing.controllers;


import com.example.QuanLyChoThueXeDien.VehicleModel.services.VehicleModelService;
import com.example.QuanLyChoThueXeDien.pricing.dtos.RentalPriceForm;
import com.example.QuanLyChoThueXeDien.pricing.services.RentalPriceService;
import jakarta.validation.Valid;
import org.springframework.boot.Banner;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/rental-prices")
public class AdminRentalPriceController {
    private final RentalPriceService priceService;
    private final VehicleModelService modelService;

    public AdminRentalPriceController(RentalPriceService priceService,
                                      VehicleModelService modelService) {
        this.priceService = priceService;
        this.modelService = modelService;
    }

    @GetMapping
    public String list(Model model){
        model.addAttribute("rentalPriceForm", new RentalPriceForm());
        return renderPage(model);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("rentalPriceForm") RentalPriceForm form,
                         BindingResult errors, Model model,
                         RedirectAttributes redirectAttributes) {
        if (errors.hasErrors()) {
            return renderPage(model);
        }

        try {
            priceService.create(form);
        } catch (IllegalArgumentException exception) {
            errors.reject("rentalPrice.invalid", exception.getMessage());
            return renderPage(model);
        }

        redirectAttributes.addFlashAttribute("successMessage", "Đã lưu giá thuê.");
        return "redirect:/admin/rental-prices";
    }


    private String renderPage(Model model) {
        model.addAttribute("vehicleModels", modelService.findAll());
        model.addAttribute("prices", priceService.findAll());
        return "admin/catalog/rental-prices";
    }
}
