package com.example.QuanLyChoThueXeDien.rental.services;

import org.springframework.stereotype.Component;
import java.time.*;

@Component
public class RentalClock {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final Clock clock;
    public RentalClock() { this(Clock.system(ZONE)); }
    public RentalClock(Clock clock) { this.clock = clock; }
    public LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZONE).truncatedTo(java.time.temporal.ChronoUnit.MICROS); }
}
