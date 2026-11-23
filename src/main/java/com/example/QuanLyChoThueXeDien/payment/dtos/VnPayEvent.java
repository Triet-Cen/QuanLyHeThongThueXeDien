package com.example.QuanLyChoThueXeDien.payment.dtos;

import java.math.BigDecimal;

public record VnPayEvent(String ref, String merchant, String providerId, BigDecimal amount, String currency,
                         String date, String response, String status, String signature) {
    public boolean successful() { return "00".equals(response) && "00".equals(status); }
}
