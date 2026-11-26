package com.example.QuanLyChoThueXeDien.payment.controllers;

import com.example.QuanLyChoThueXeDien.payment.services.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.transaction.TransactionException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.security.Principal;
import java.util.Map;


@Controller
@RequiredArgsConstructor
@Slf4j
public class PaymentController {
    private final PaymentService payments;

    @GetMapping("/bookings/{id}/invoice")
    public String showInvoice(@PathVariable int id, @RequestParam(defaultValue="false") boolean fees,
                          Principal user, Model model, RedirectAttributes flash) {
        try {
            model.addAttribute("invoice",payments.getInvoice(user.getName(),id,fees));

            return "user/payment-invoice";
        } catch (BadSqlGrammarException failure) {
            return showMissingPaymentTables(id, flash, failure);
        } catch (IllegalArgumentException invalid) {
            flash.addFlashAttribute("errorMessage",invalid.getMessage());
            return "redirect:/bookings/" + id;
        }
    }

    @PostMapping("/bookings/{id}/vnpay")
    public String startPayment(@PathVariable int id, @RequestParam boolean fees, @RequestParam String invoiceKey,
                        Principal user, HttpServletRequest request, RedirectAttributes flash) {
        try {
            return "redirect:" + payments.startPayment(user.getName(),id,fees,invoiceKey,request.getRemoteAddr());
        } catch (BadSqlGrammarException failure) {
            return showMissingPaymentTables(id, flash, failure);
        } catch (IllegalArgumentException invalid) {
            flash.addFlashAttribute("errorMessage",invalid.getMessage());
            return "redirect:/bookings/" + id + "/invoice?fees=" + fees;
        }
    }

    private String showMissingPaymentTables(int id, RedirectAttributes flash, BadSqlGrammarException failure) {
        if (failure.getSQLException().getErrorCode() != 1146) throw failure;
        log.error("VNPay unavailable for booking {} because a required database table is missing", id, failure);
        flash.addFlashAttribute("errorMessage", "Chưa thể thanh toán qua VNPay vì cơ sở dữ liệu chưa được chuẩn bị cho thanh toán.");
        return "redirect:/bookings/" + id;
    }

    @GetMapping("/payments/vnpay/ipn")
    @ResponseBody
    public Map<String,String> receiveIpn(HttpServletRequest request) {
        String code;
        try {
            code = payments.handleIpn(request.getParameterMap());
        } catch (DataAccessException | TransactionException unavailable) {
            code = "99"; // The transaction failed. Let VNPay retry.
        }
        String message = switch (code) {
            case "00" -> "Confirm Success";
            case "01" -> "Order not found";
            case "02" -> "Order already confirmed";
            case "04" -> "Invalid Amount";
            case "97" -> "Invalid Checksum";
            default -> "Processing error";
        };
        return Map.of("RspCode",code,"Message",message);
    }

    @GetMapping("/payments/vnpay/return")
    public String showPaymentResult(@RequestParam("vnp_TxnRef") String ref, Principal user,
                                    HttpServletRequest request, Model model) {
        model.addAttribute("result",payments.handleReturn(user.getName(),ref,request.getParameterMap()));
        return "user/payment-result";
    }

    @GetMapping("/admin/payments/reconciliation")
    public String showPaymentsForReview(Model model) {
        model.addAttribute("events",payments.listPaymentsForReview());
        return "admin/payment-reconciliation";
    }

    @PostMapping("/admin/payments/reconciliation/{event}/notes")
    public String saveReviewNote(@PathVariable long event, @RequestParam String note, Principal user) {
        payments.saveReviewNote(event,user.getName(),note);
        return "redirect:/admin/payments/reconciliation";
    }
}
