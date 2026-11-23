package com.example.QuanLyChoThueXeDien.search.controllers;

// Import VehicleSearchForm và VehicleSearchService.
import com.example.QuanLyChoThueXeDien.search.dtos.VehicleSearchForm;
import com.example.QuanLyChoThueXeDien.search.services.VehicleSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpSession;
import com.example.QuanLyChoThueXeDien.rental.controllers.BookingSession;
import com.example.QuanLyChoThueXeDien.rental.dtos.BookingForm;

@Controller
@RequiredArgsConstructor
public class VehicleSearchController {
    private final VehicleSearchService searchService;

    @GetMapping("/vehicles")
    public String search(
            @Valid @ModelAttribute("searchForm") VehicleSearchForm form,
            BindingResult errors,
            @RequestParam(required = false) Integer selectVehicleId,
            HttpSession session,
            Principal principal,
            Model model
    ) {
        var options = searchService.options();

        model.addAttribute("stations", options.stations());
        model.addAttribute("categories", options.categories());
        model.addAttribute("searchModels", options.models());

        model.addAttribute(
                "email",
                principal == null ? null : principal.getName()
        );

        model.addAttribute("searchAttempted", true);
        model.addAttribute("searchSucceeded", false);
        model.addAttribute("searchResults", List.of());

        // Các biến của khu vực danh mục cũ vẫn tồn tại.
        model.addAttribute("vehicleModels", List.of());
        model.addAttribute("dailyRates", Map.of());

        if (!errors.hasErrors()) {
            try {
                var results = searchService.search(form);
                model.addAttribute("searchResults", results);
                if (selectVehicleId != null) {
                    if (results.stream().noneMatch(r -> r.vehicleId().equals(selectVehicleId)))
                        throw new IllegalArgumentException("Xe không còn phù hợp với lịch đã chọn. Vui lòng tìm lại.");
                    BookingForm selection = new BookingForm();
                    selection.setVehicleId(selectVehicleId); selection.setStationId(form.getStationId());
                    selection.setCategoryId(form.getCategoryId()); selection.setModelId(form.getModelId());
                    selection.setStartAt(form.getStartAt()); selection.setEndAt(form.getEndAt());
                    session.setAttribute(BookingSession.SELECTION, selection);
                    return "redirect:/bookings/confirm";
                }
                model.addAttribute("searchSucceeded", true);
            } catch (IllegalArgumentException exception) {
                errors.reject("search.invalid", exception.getMessage());
            }
        }

        return "home";
    }
}
