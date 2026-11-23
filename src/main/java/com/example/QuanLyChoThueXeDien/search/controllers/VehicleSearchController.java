package com.example.QuanLyChoThueXeDien.search.controllers;

// Import VehicleSearchForm và VehicleSearchService.
import com.example.QuanLyChoThueXeDien.search.dtos.VehicleSearchForm;
import com.example.QuanLyChoThueXeDien.search.services.VehicleSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpSession;
import com.example.QuanLyChoThueXeDien.session.BookingSession;
import com.example.QuanLyChoThueXeDien.rental.dtos.BookingForm;

@Controller
@RequiredArgsConstructor
public class VehicleSearchController {
    private final VehicleSearchService searchService;

    @GetMapping(value = "/vehicles", params = "!bookModelId")
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
                    return confirmSelection(form, selectVehicleId, session);
                }
                model.addAttribute("searchSucceeded", true);
            } catch (IllegalArgumentException exception) {
                errors.reject("search.invalid", exception.getMessage());
            }
        }

        return "home";
    }

    @GetMapping(value = "/vehicles", params = "bookModelId")
    public String bookModel(@RequestParam Integer bookModelId,
                            @RequestParam(defaultValue = "false") boolean checkAvailability,
                            @Valid @ModelAttribute("searchForm") VehicleSearchForm form, BindingResult errors,
                            HttpSession session, Model model) {
        var selectedModel = searchService.findBookableModel(bookModelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mẫu xe không còn mở đặt."));
        var stations = searchService.bookingStations(bookModelId);
        model.addAttribute("vehicleModel", selectedModel);
        model.addAttribute("stations", stations);
        model.addAttribute("checkAvailability", checkAvailability);

        if (checkAvailability && !errors.hasErrors()) {
            form.setModelId(bookModelId);
            form.setCategoryId(null);
            try {
                var available = searchService.search(form);
                var bookable = available.stream().filter(vehicle -> vehicle.estimatedAmount() != null).findFirst();
                if (bookable.isPresent()) {
                    return confirmSelection(form, bookable.get().vehicleId(), session);
                }
                errors.reject("booking.unavailable", available.isEmpty()
                        ? "Mẫu xe này đã hết xe trong lịch đã chọn. Vui lòng đổi trạm hoặc thời gian."
                        : "Mẫu xe này chưa có giá thuê cho ngày nhận đã chọn. Vui lòng đổi lịch thuê.");
            } catch (IllegalArgumentException exception) {
                errors.reject("booking.invalid", exception.getMessage());
            }
        }
        return "user/model-booking";
    }

    private String confirmSelection(VehicleSearchForm form, Integer vehicleId, HttpSession session) {
        BookingForm selection = new BookingForm();
        selection.setVehicleId(vehicleId);
        selection.setStationId(form.getStationId());
        selection.setCategoryId(form.getCategoryId());
        selection.setModelId(form.getModelId());
        selection.setStartAt(form.getStartAt());
        selection.setEndAt(form.getEndAt());
        BookingSession.saveSelection(session, selection);
        return "redirect:/bookings/confirm";
    }
}
