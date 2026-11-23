package com.example.QuanLyChoThueXeDien.rental.services;

import com.example.QuanLyChoThueXeDien.rental.repositories.RentalRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component @EnableScheduling @RequiredArgsConstructor
public class HoldExpiryScheduler {
    private static final Logger log = LoggerFactory.getLogger(HoldExpiryScheduler.class);
    private final RentalRepository rentals;
    private final BookingService bookings;
    private final RentalClock clock;

    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void expireHolds() {
        for (Integer id : rentals.expiredIds(clock.now(), PageRequest.of(0, 100))) {
            try { bookings.expire(id); }
            catch (RuntimeException e) {
                // No success is reported: each failed hold remains retryable on the next pass.
                log.error("Không xử lý được hết hạn đơn #{} ({})", id, e.getClass().getSimpleName());
            }
        }
    }
}
