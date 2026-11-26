package com.example.QuanLyChoThueXeDien.payment.dtos;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.regex.Pattern;

/** Metadata travels in VNPay's signed TxnRef, never in additional database columns. */
public record PaymentReference(int id, LocalDateTime expires, String invoice) {
    private static final Pattern FORMAT = Pattern.compile("V([1-9][0-9]{0,9})T([0-9]{10})H([a-f0-9]{64})");

    public String encode() {
        if (id <= 0 || !invoice.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid payment reference");
        return "V" + id + "T" + expires.toEpochSecond(ZoneOffset.UTC) + "H" + invoice;
    }

    public static Optional<PaymentReference> parse(String value) {
        if (value == null) return Optional.empty();
        var match = FORMAT.matcher(value);
        if (!match.matches()) return Optional.empty();
        try {
            return Optional.of(new PaymentReference(Integer.parseInt(match.group(1)),
                LocalDateTime.ofEpochSecond(Long.parseLong(match.group(2)),0,ZoneOffset.UTC),match.group(3)));
        } catch (RuntimeException invalid) { return Optional.empty(); }
    }
}