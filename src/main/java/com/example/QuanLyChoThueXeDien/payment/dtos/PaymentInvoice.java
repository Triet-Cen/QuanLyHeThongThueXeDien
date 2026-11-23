package com.example.QuanLyChoThueXeDien.payment.dtos;

import com.example.QuanLyChoThueXeDien.rental.entities.Rental;
import java.math.BigDecimal;
import java.util.List;

public record PaymentInvoice(Rental rental, boolean fees, List<PaymentFee> lines, BigDecimal amount, String key) {}
