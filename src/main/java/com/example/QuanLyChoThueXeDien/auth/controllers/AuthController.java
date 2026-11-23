package com.example.QuanLyChoThueXeDien.auth.controllers;


import com.example.QuanLyChoThueXeDien.VehicleModel.entities.VehicleModel;
import com.example.QuanLyChoThueXeDien.VehicleModel.services.VehicleModelService;
import com.example.QuanLyChoThueXeDien.pricing.entities.RentalPrice;
import com.example.QuanLyChoThueXeDien.pricing.repositories.RentalPriceRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.example.QuanLyChoThueXeDien.auth.dtos.RegisterRequest;
import com.example.QuanLyChoThueXeDien.auth.services.RegistrationService;
import com.example.QuanLyChoThueXeDien.search.dtos.VehicleSearchForm;
import com.example.QuanLyChoThueXeDien.search.services.VehicleSearchService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.core.Authentication;

import java.security.Principal;
import com.example.QuanLyChoThueXeDien.auth.repositories.CustomerProfileRepository;

@Controller
public class AuthController {

    private final RegistrationService registrationService;
    private final VehicleModelService vehicleModelService;
    private final RentalPriceRepository rentalPriceRepository;
    private final VehicleSearchService searchService;

    public AuthController(RegistrationService registrationService,
                          VehicleModelService vehicleModelService,
                          RentalPriceRepository rentalPriceRepository,
                          VehicleSearchService searchService) {
        this.registrationService = registrationService;
        this.vehicleModelService = vehicleModelService;
        this.rentalPriceRepository = rentalPriceRepository;
        this.searchService = searchService;
    }

    @GetMapping("/")
    public String index(){
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String login(){
        return "user/auth/login";
    }

    @GetMapping("/register")
    public String registerPage(Model model){
            model.addAttribute("registerRequest", new RegisterRequest());
            return "user/auth/register";
    }

    @GetMapping("/after-login")
    public String redirectAfterLogin(Authentication authentication, jakarta.servlet.http.HttpSession session){
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())
                );
        if(isAdmin==true){
            return "redirect:/admin";
        }
        if (authentication.getAuthorities().stream().anyMatch(a -> "ROLE_CUSTOMER".equals(a.getAuthority()))
                && session.getAttribute(com.example.QuanLyChoThueXeDien.rental.controllers.BookingSession.SELECTION) != null) {
            return "redirect:/bookings/confirm";
        }
        return "redirect:/home";
    }

    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute("registerRequest") RegisterRequest request,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return "user/auth/register";
        }

        try {
            registrationService.register(request);
        } catch (IllegalArgumentException exception) {
            model.addAttribute("error", exception.getMessage());
            return "user/auth/register";
        } catch (DataIntegrityViolationException exception) {

            model.addAttribute(
                    "error",
                    "Dữ liệu đăng ký bị trùng hoặc không hợp lệ"
            );
            return "user/auth/register";
        }

        redirectAttributes.addFlashAttribute(
                "success",
                "Đăng ký thành công"
        );

        return "redirect:/login";
    }

    @GetMapping("/home")
    public String home(Model model, Principal principal) {
        List<VehicleModel> vehicleModels = vehicleModelService.findAll().stream()
                .filter(item -> "ACTIVE".equals(item.getModelStatus()))
                .filter(item -> "ACTIVE".equals(item.getCategory().getCategoryStatus()))
                .toList();

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        Map<Integer, BigDecimal> dailyRates = new HashMap<>();

        for (RentalPrice price : rentalPriceRepository.findEffectivePrices(today)) {
            Integer modelId = price.getVehicleModel().getModelId();
            dailyRates.putIfAbsent(modelId, price.getDailyRate());
        }

        vehicleModels= vehicleModels.stream()
                        .filter(item->dailyRates.get(item.getModelId())!=null)
                                .toList();

        model.addAttribute(
                "email",
                principal == null ? null : principal.getName()
        );
        model.addAttribute("vehicleModels", vehicleModels);
        model.addAttribute("dailyRates", dailyRates);

        var options = searchService.options();

        model.addAttribute("searchForm", new VehicleSearchForm());
        model.addAttribute("stations", options.stations());
        model.addAttribute("categories", options.categories());
        model.addAttribute("searchModels", options.models());
        model.addAttribute("searchAttempted", false);
        model.addAttribute("searchSucceeded", false);

        return "home";
    }

}
