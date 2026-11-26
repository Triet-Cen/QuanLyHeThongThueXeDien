package com.example.QuanLyChoThueXeDien.payment.dtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentAttempt(int id, int rental, String kind, String ref, String invoice,
                             BigDecimal amount, String status, LocalDateTime expires) {}
