package com.example.QuanLyChoThueXeDien.payment.dtos;

import java.math.BigDecimal;

public record PaymentFee(int id, BigDecimal amount, String type, String reason, String status) {
    public boolean unpaid() { return "UNPAID".equalsIgnoreCase(status); }
}
