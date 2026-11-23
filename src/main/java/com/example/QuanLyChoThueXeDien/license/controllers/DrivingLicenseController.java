package com.example.QuanLyChoThueXeDien.license.controllers;
import com.example.QuanLyChoThueXeDien.license.dtos.DrivingLicenseForm;
import com.example.QuanLyChoThueXeDien.license.entities.DrivingLicense;
import com.example.QuanLyChoThueXeDien.license.services.DrivingLicenseService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.security.Principal;

@Controller
public class DrivingLicenseController {
    private final DrivingLicenseService service;

    public DrivingLicenseController(DrivingLicenseService service) {
        this.service = service;
    }


    @GetMapping("/profile/license")
    public String own(Principal principal, Model model) {
        DrivingLicense current = service.own(principal.getName());
        DrivingLicenseForm form = new DrivingLicenseForm();
        if (current != null) {
            form.setLicenseNumber(current.getLicenseNumber()); form.setLicenseClass(current.getLicenseClass());
            form.setIssueDate(current.getIssueDate()); form.setExpiryDate(current.getExpiryDate());
        }
        model.addAttribute("licenseForm", form);
        return customerPage(principal, model);
    }

    @PostMapping("/profile/license")
    public String submit(@Valid @ModelAttribute("licenseForm") DrivingLicenseForm form,
            BindingResult errors, Principal principal, Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                service.submit(principal.getName(), form);
                redirect.addFlashAttribute("successMessage", "Đã gửi GPLX. Hồ sơ đang chờ nhân viên xác minh.");
                return "redirect:/profile/license";
            } catch (IllegalArgumentException e) { errors.reject("license.invalid", e.getMessage()); }
            catch (DataIntegrityViolationException e) { errors.reject("license.duplicate", "Không lưu được GPLX. Số GPLX có thể vừa được sử dụng; vui lòng kiểm tra lại."); }
        }
        return customerPage(principal, model);
    }
    private String customerPage(Principal principal, Model model) {
        model.addAttribute("email", principal.getName());
        model.addAttribute("license", service.own(principal.getName()));
        return "user/license";
    }

    @GetMapping("/profile/license/image")
    @ResponseBody
    public ResponseEntity<byte[]> ownImage(Principal principal) {
        return image(service.ownImage(principal.getName()));
    }

    @GetMapping("/staff/licenses")
    public String list(Principal principal, Model model) {
        model.addAttribute("licenses", service.list(principal.getName()));
        return "staff/licenses";
    }

    @GetMapping("/staff/licenses/{id}")
    public String detail(@PathVariable Integer id, Principal principal, Model model) {
        model.addAttribute("license", service.detail(id, principal.getName()));
        return "staff/license-detail";
    }

    @PostMapping("/staff/licenses/{id}")
    public String verify(@PathVariable Integer id, @RequestParam String expectedImage,
            @RequestParam String decision, Principal principal, RedirectAttributes redirect) {
        try {
            service.verify(id, principal.getName(), expectedImage, decision);
            redirect.addFlashAttribute("successMessage", "Đã lưu kết quả xác minh.");
        } catch (IllegalArgumentException e) { redirect.addFlashAttribute("errorMessage", e.getMessage()); }
        return "redirect:/staff/licenses/" + id;
    }

    @GetMapping("/staff/licenses/{id}/image")
    @ResponseBody
    public ResponseEntity<byte[]> staffImage(@PathVariable Integer id, Principal principal) {
        return image(service.staffImage(id, principal.getName()));
    }

    private ResponseEntity<byte[]> image(DrivingLicenseService.ImageData image) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
            .header(HttpHeaders.CACHE_CONTROL, "no-store, private")
            .header("X-Content-Type-Options", "nosniff").body(image.bytes());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String uploadTooLarge(RedirectAttributes redirect) {
        redirect.addFlashAttribute("errorMessage", "Ảnh quá lớn. Vui lòng chọn ảnh tối đa 1 MB.");
        return "redirect:/profile/license";
    }
}
