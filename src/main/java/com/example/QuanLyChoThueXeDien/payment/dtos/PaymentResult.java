package com.example.QuanLyChoThueXeDien.payment.dtos;

import java.math.BigDecimal;

public record PaymentResult(int rental, String ref, String status, String rentalStatus, BigDecimal amount) {}
