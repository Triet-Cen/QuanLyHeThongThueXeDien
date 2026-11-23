package com.example.QuanLyChoThueXeDien.auth.controllers;


import com.example.QuanLyChoThueXeDien.auth.dtos.ProfileForm;
import com.example.QuanLyChoThueXeDien.auth.services.CustomerProfileService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller

public class CustomerProfileController {

    private CustomerProfileService profileService;

    public CustomerProfileController(CustomerProfileService profileService) {
        this.profileService = profileService;
    }


    @GetMapping("/profile")
    public  String show(Principal principal, Model model){
        model.addAttribute("email", principal.getName());
        model.addAttribute("profileForm",
                profileService.getForm(principal.getName()));
        return "user/profile";
    }

    @PostMapping("/profile")
    public String update(
            @Valid @ModelAttribute("profileForm")ProfileForm form,
            BindingResult result,
            Principal principal,
            Model model,
            RedirectAttributes redirectAttributes
            )
    {
        if(result.hasErrors()){
            model.addAttribute("email", principal.getName());
            return "user/profile";
        }

        profileService.update(principal.getName(), form);
        redirectAttributes.addFlashAttribute(
                "successMessage","Đã lưu thông tin hồ sơ"
        );
        return "redirect:/profile";
    }
}
