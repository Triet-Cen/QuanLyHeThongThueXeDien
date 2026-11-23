package com.example.QuanLyChoThueXeDien.admin.controllers;


import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import java.security.Principal;
import org.springframework.ui.Model;
@Controller
public class AdminController {

    @GetMapping("/admin")
    public String dashboard(Principal principal, Model model){
        model.addAttribute("adminName", principal.getName());
        return "/admin/dashboard/dashboard";
    }
}
