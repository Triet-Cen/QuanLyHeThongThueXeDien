package com.example.QuanLyChoThueXeDien.pricing.services;

import com.example.QuanLyChoThueXeDien.pricing.entities.RentalPrice;
import com.example.QuanLyChoThueXeDien.pricing.repositories.RentalPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.*;
import java.time.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RentalQuoteService {
    private final RentalPriceRepository prices;

    public Map<Integer, BigDecimal> displayDailyRates(LocalDate date) {
        Map<Integer, BigDecimal> dailyRates = new HashMap<>();
        for (RentalPrice price : prices.findEffectivePrices(date)) {
            dailyRates.putIfAbsent(price.getVehicleModel().getModelId(), price.getDailyRate());
        }
        return dailyRates;
    }

    public BigDecimal dailyRate(Integer modelId, LocalDate pickupDate) {
        return requireRate(prices.findEffectiveForModel(modelId, pickupDate));
    }

    public static BigDecimal requireRate(List<RentalPrice> applicable) {
        if (applicable.isEmpty()) throw new IllegalArgumentException("Chưa có giá áp dụng tại thời điểm nhận.");
        if (applicable.size() != 1) throw new IllegalArgumentException("Bảng giá bị trùng kỳ; chưa thể báo giá.");
        BigDecimal rate = applicable.getFirst().getDailyRate();
        if (rate == null || rate.signum() <= 0) throw new IllegalArgumentException("Giá thuê không hợp lệ.");
        return rate;
    }

    public static long rentalDays(LocalDateTime start, LocalDateTime end) {
        Duration duration = Duration.between(start, end);
        if (duration.isNegative() || duration.isZero()) throw new IllegalArgumentException("Giờ trả phải sau giờ nhận.");
        long days = duration.toDays();
        return duration.minusDays(days).isZero() ? days : days + 1;
    }

    public static Amounts amounts(BigDecimal rate, long days, BigDecimal percent) {
        if (percent.signum() < 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0)
            throw new IllegalArgumentException("Tỷ lệ mã giảm phải từ 0 đến 100%.");
        BigDecimal gross = rate.multiply(BigDecimal.valueOf(days)).setScale(0, RoundingMode.HALF_UP);
        BigDecimal discount = gross.multiply(percent).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        return new Amounts(gross, discount, gross.subtract(discount));
    }
    public record Amounts(BigDecimal rentalAmount, BigDecimal discountAmount, BigDecimal totalAmount) {}
}
