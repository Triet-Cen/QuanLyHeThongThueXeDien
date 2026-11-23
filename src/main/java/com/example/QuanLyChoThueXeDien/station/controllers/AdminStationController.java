package com.example.QuanLyChoThueXeDien.station.controllers;

import com.example.QuanLyChoThueXeDien.station.dtos.StationForm;
import com.example.QuanLyChoThueXeDien.station.services.StationService;
import jakarta.validation.Valid;
import org.springframework.boot.Banner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import com.example.QuanLyChoThueXeDien.station.entities.Station;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AdminStationController {


    private final StationService stationService;

    public AdminStationController(StationService stationService) {
        this.stationService = stationService;
    }


    @GetMapping("/admin/stations")
    public String listStation(Model model, Principal principal){
        model.addAttribute("adminName",principal.getName());
        model.addAttribute("stations", stationService.findAll());
        return "/admin/catalog/stations";
    }

    @GetMapping("/admin/stations/new")
    public String showCreateStationForm(Model model, Principal principal){
        model.addAttribute("adminName",principal.getName());
        model.addAttribute("stationForm", new StationForm());

        return "/admin/catalog/station-form";
    }

    @PostMapping("/admin/stations")
    public String createStation(
            @Valid @ModelAttribute("stationForm") StationForm stationForm,
            BindingResult result,
            Model model,
            Principal principal,
            RedirectAttributes redirectAttributes
    ) {
        if (result.hasErrors()) {
            model.addAttribute("adminName", principal.getName());
            return "admin/catalog/station-form";
        }

        stationService.create(stationForm.getStationName(), stationForm.getStationAddress(), stationForm.getStationPhoneNumber());

        redirectAttributes.addFlashAttribute("successMessage", "Thêm trạm thành công");
        return "redirect:/admin/stations";
    }

    @GetMapping("/admin/stations/{stationId}/edit")
    public String showEditForm(
            @PathVariable("stationId") Integer id,
            Model model,
            Principal principal,
            RedirectAttributes redirectAttributes
    ){
        Station station= stationService.findById(id).orElse(null);

        if(station==null){
            redirectAttributes.addFlashAttribute("errorMessage","Trạm xe không tồn tại");
            return "redirect:/admin/stations";
        }

        StationForm stationForm= new StationForm();
        stationForm.setStationName(station.getStationName());
        stationForm.setStationAddress(station.getStationAddress());
        stationForm.setStationPhoneNumber(station.getStationPhoneNumber());

        model.addAttribute("adminName", principal.getName());
        model.addAttribute("stationId", id);
        model.addAttribute("stationForm",stationForm);

        return "/admin/catalog/station-form";
    }

    @PostMapping("/admin/stations/{stationId}")
    public String updateStation(
            @PathVariable ("stationId") Integer id,
            @Valid @ModelAttribute ("stationForm") StationForm form,
            BindingResult bindingResult,
            Model model,
            Principal principal,
            RedirectAttributes redirectAttributes
    ){
        if(bindingResult.hasErrors()){
            model.addAttribute("adminName", principal.getName());
            model.addAttribute("stationId", id );
            return "/admin/catalog/station-form";
        }

        boolean updated= stationService.updateStation(id, form.getStationName(),
                form.getStationAddress(),
                form.getStationPhoneNumber());
        if(updated){
            redirectAttributes.addFlashAttribute("successMessage","Cập nhật thành công");
        }
        else{
            redirectAttributes.addFlashAttribute("successMessage","Trạm xe không tồn tại");
        }

        return "redirect:/admin/stations";
    }

    @PostMapping("/admin/stations/{stationId}/visibility")
    public String changeVisibility(
            @PathVariable("stationId") Integer stationId,
            @RequestParam("visible") boolean visible,
            RedirectAttributes redirectAttributes
    ) {
        if (stationService.changeVisibility(stationId, visible)) {
            redirectAttributes.addFlashAttribute(
                    "successMessage", visible ? "Đã hiện lại trạm xe." : "Đã ẩn trạm xe."
            );
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Trạm xe không tồn tại.");
        }

        return "redirect:/admin/stations";
    }


    }

