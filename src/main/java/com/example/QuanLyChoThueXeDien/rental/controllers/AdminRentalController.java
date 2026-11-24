package com.example.QuanLyChoThueXeDien.rental.controllers;

import com.example.QuanLyChoThueXeDien.rental.repositories.AdminRentalRepository;
import com.example.QuanLyChoThueXeDien.rental.services.*;
import java.security.Principal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/rentals")
@RequiredArgsConstructor
public class AdminRentalController {
    private final AdminRentalRepository rentals;
    private final BookingService bookings;
    private final RentalClock clock;
    private final RentalRefundService refunds;

    @PostMapping("/{id}/refunds/{refund}")
    String refund(@PathVariable int id,@PathVariable int refund,@RequestParam(defaultValue="false") boolean confirm,
                  @RequestParam(required=false) String reference,@RequestParam(required=false) String evidence,
                  @RequestParam(required=false) java.time.LocalDateTime paidAt,
                  Principal principal,RedirectAttributes redirect) {
        try {
            refunds.process(principal.getName(),id,refund,confirm,reference,evidence,paidAt);
            redirect.addFlashAttribute("message","Đã ghi nhận xử lý hồ sơ hoàn.");
        } catch(IllegalArgumentException e) { redirect.addFlashAttribute("message",e.getMessage()); }
        return "redirect:/admin/rentals/"+id;
    }

    @ModelAttribute
    void checkAccess(Principal principal) { rentals.requireAdmin(principal.getName()); }

    @GetMapping
    String list(@RequestParam(defaultValue="") String q,@RequestParam(required=false) Integer station,
            @RequestParam(defaultValue="") String status,@RequestParam(defaultValue="created") String basis,
            @RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,
            @RequestParam(defaultValue="") String flag,@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int size,Model model) {
        model.addAttribute("results",rentals.search(q,station,status,basis,from,to,flag,page,size,clock.now()));
        model.addAttribute("stations",rentals.stations());
        model.addAttribute("statuses",AdminRentalRepository.STATUSES);
        model.addAttribute("page",page);
        model.addAttribute("size",size);
        return "admin/rental-list";
    }
    @GetMapping("/{id}")
    String detail(@PathVariable int id,Model model) {
        model.addAttribute("statuses",AdminRentalRepository.STATUSES);
        model.addAttribute("r",rentals.rental(id));
        model.addAttribute("sections",rentals.records(id));
        return "admin/rental-detail";
    }
    @PostMapping("/{id}/cancel-company")
    String cancel(@PathVariable int id,@RequestParam String reason,Principal principal,RedirectAttributes redirect) {
        try {
            bookings.cancelForNoReplacement(principal.getName(),id,reason);
            redirect.addFlashAttribute("message","Đơn đã hủy. Quyền hoàn BR41 được xử lý bên ngoài ứng dụng.");
        } catch(IllegalArgumentException e) { redirect.addFlashAttribute("message",e.getMessage()); }
        return "redirect:/admin/rentals/"+id;
    }
}
