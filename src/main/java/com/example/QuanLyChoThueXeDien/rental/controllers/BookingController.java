package com.example.QuanLyChoThueXeDien.rental.controllers;

import com.example.QuanLyChoThueXeDien.rental.dtos.*;
import com.example.QuanLyChoThueXeDien.rental.services.*;
import com.example.QuanLyChoThueXeDien.session.BookingSession;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.security.Principal;
import java.util.UUID;
import com.example.QuanLyChoThueXeDien.payment.services.PaymentService;

@Controller
@RequiredArgsConstructor //truyền service và clock vào.
@RequestMapping("/bookings")
public class BookingController {
    private final BookingService service;
    private final RentalClock clock;

    private final RentalRefundService refunds;

    @GetMapping
    public String list(Principal principal, Model model) {
        model.addAttribute("rentals", service.own(principal.getName()));
        model.addAttribute("email", principal.getName());
        return "user/bookings";
    }

    @GetMapping("/confirm")
    public String confirm(Principal principal, HttpSession session, Model model) {
        BookingForm form = BookingSession.getSelection(session);
        if (form == null) return "redirect:/home#vehicle-search";
        return render(principal, session, model, form);
    }

    @PostMapping("/quote")
    public String quote(@Valid @ModelAttribute("bookingForm") BookingForm form, BindingResult errors,
            @RequestParam(defaultValue = "false") boolean removeCoupon,
            Principal principal, HttpSession session, Model model) {
        if (removeCoupon) {
            form.setCouponCode(null);
            // Nếu khách gỡ mã giảm giá, render trang lại để cập nhật giá lại
            return render(principal, session, model, form);
        }
        if (errors.hasErrors()) {
            model.addAttribute("email", principal.getName());
            return "user/booking-confirm";
        }
        return render(principal, session, model, form);
    }

    @PostMapping
    public String create(@RequestParam String quoteToken, Principal principal, HttpSession session,
            Model model, RedirectAttributes redirect) {
        BookingSession state = BookingSession.of(session, principal.getName());
        BookingQuote accepted;
        // Chỉ nhớ kết quả trong session; khóa DB trong service vẫn bảo vệ lịch giữa mọi khách.
        synchronized (state) {
            Integer createdId = state.createdOrder(quoteToken);
            if (createdId != null) return "redirect:/bookings/" + createdId;
            accepted = state.get(quoteToken);
            //Lấy báo giá đã lưu trong BookingSession cỦA khách
            if (accepted == null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Báo giá không còn trong phiên. Vui lòng chọn lại xe.");
            try {
                Integer id = service.create(principal.getName(), accepted);
                state.rememberCreated(quoteToken, id);
                redirect.addFlashAttribute("successMessage", "Đơn đã được tạo. Lịch xe được giữ đến hạn thanh toán ghi trên đơn.");
                return "redirect:/bookings/" + id;
            } catch (IllegalArgumentException e) {
                model.addAttribute("errorMessage", e.getMessage());
            } catch (ConcurrencyFailureException e) {
                model.addAttribute("errorMessage", "Xe hoặc mã giảm đang được xử lý. Vui lòng kiểm tra lại trước khi xác nhận.");
            }
        }
        model.addAttribute("bookingForm", accepted.selection().copy());
        return render(principal, session, model, accepted.selection());
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Integer id, Principal principal, Model model) {
        var rental = service.view(principal.getName(), id);
        model.addAttribute("rental", rental);
        if ("CANCELLED".equalsIgnoreCase(rental.getStatus())) model.addAttribute("refund",refunds.customerView(principal.getName(),id));
        boolean activeHold = rental.hasActiveHold(clock.now());
        model.addAttribute("activeHold", activeHold);
        if (activeHold) {
            model.addAttribute("invoice", PaymentService.rentalInvoice(rental));
            model.addAttribute("requestKey", UUID.randomUUID());
        }
        model.addAttribute("serverNow", clock.now().atZone(RentalClock.ZONE).toInstant().toEpochMilli());
        model.addAttribute("holdUntil", rental.getHoldExpiresAt().atZone(RentalClock.ZONE).toInstant().toEpochMilli());
        model.addAttribute("email", principal.getName());
        return "user/booking-pending";
    }

    @PostMapping("/{id}/refund-request")
    public String requestRefund(@PathVariable int id,Principal principal,@RequestParam String bank,@RequestParam String number,
            @RequestParam String holder,@RequestParam String note,RedirectAttributes redirect) {
        try {
            refunds.request(principal.getName(),id,bank,number,holder,note);
            redirect.addFlashAttribute("successMessage","Đã ghi nhận yêu cầu hoàn tiền.");
        } catch(IllegalArgumentException e) { redirect.addFlashAttribute("errorMessage",e.getMessage()); }
        return "redirect:/bookings/"+id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Integer id, Principal principal, RedirectAttributes redirect) {
        try {
            service.cancel(principal.getName(), id);
            redirect.addFlashAttribute("successMessage", "Đơn đã hủy hoặc đã hết hạn. Khoảng lịch giữ đã được giải phóng.");
        } catch (IllegalArgumentException e) { redirect.addFlashAttribute("errorMessage", e.getMessage()); }
        return "redirect:/bookings/" + id;
    }

    private String render(Principal principal, HttpSession session, Model model, BookingForm form) {
        model.addAttribute("email", principal.getName());
        model.addAttribute("bookingForm", form.copy());
        BookingSession.saveSelection(session, form);
        try {
            BookingQuote quote = service.quote(principal.getName(), form);
            model.addAttribute("quote", quote);
            model.addAttribute("quoteToken", BookingSession.of(session, principal.getName()).add(quote));
        } catch (IllegalArgumentException e) { model.addAttribute("quoteError", e.getMessage()); }
        return "user/booking-confirm";
    }
}
