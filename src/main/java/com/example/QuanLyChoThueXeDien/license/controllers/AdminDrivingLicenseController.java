package com.example.QuanLyChoThueXeDien.license.controllers;

import com.example.QuanLyChoThueXeDien.license.services.DrivingLicenseService;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
@RequestMapping("/admin/licenses")
public class AdminDrivingLicenseController {

    private final DrivingLicenseService service;

    public AdminDrivingLicenseController(DrivingLicenseService service) {
        this.service = service;
    }

    @ModelAttribute("adminName")
    public String adminName(Principal principal) {
        return principal.getName();
    }

    @GetMapping
    public String list(Principal principal, Model model) {
        model.addAttribute(
                "licenses",
                service.list(principal.getName())
        );
        return "admin/license/licenses";
    }

    @GetMapping("/{id}")
    public String detail(
            @PathVariable("id") Integer id,
            Principal principal,
            Model model
    ) {
        model.addAttribute(
                "license",
                service.detail(id, principal.getName())
        );
        return "admin/license/license-detail";
    }

    @PostMapping("/{id}")
    public String verify(
            @PathVariable("id") Integer id,
            @RequestParam("expectedImage") String expectedImage,
            @RequestParam("decision") String decision,
            Principal principal,
            RedirectAttributes redirect
    ) {
        try {
            service.verify(
                    id, principal.getName(), expectedImage, decision
            );
            redirect.addFlashAttribute(
                    "successMessage", "Đã lưu kết quả xác minh."
            );
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute(
                    "errorMessage", exception.getMessage()
            );
        }

        return "redirect:/admin/licenses/" + id;
    }

    @GetMapping("/{id}/image")
    @ResponseBody
    public ResponseEntity<byte[]> image(
            @PathVariable("id") Integer id,
            Principal principal
    ) {
        var image = service.staffImage(id, principal.getName());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store, private")
                .header("X-Content-Type-Options", "nosniff")
                .body(image.bytes());
    }
}