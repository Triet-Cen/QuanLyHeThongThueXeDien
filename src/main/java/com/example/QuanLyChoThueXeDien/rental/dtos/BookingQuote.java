package com.example.QuanLyChoThueXeDien.rental.dtos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.example.QuanLyChoThueXeDien.pricing.services.RentalQuoteService.Amounts;

public record BookingQuote(BookingForm selection, String modelName, String imageUrl, String description,
    String stationName, long days, BigDecimal dailyRate, BigDecimal discountPercent, Amounts amounts,
    String eligibilityError, LocalDateTime quotedAt) {
    public boolean samePrice(BookingQuote other) {
        return days == other.days && dailyRate.compareTo(other.dailyRate) == 0
            && discountPercent.compareTo(other.discountPercent) == 0
            && amounts.rentalAmount().compareTo(other.amounts.rentalAmount()) == 0
            && amounts.discountAmount().compareTo(other.amounts.discountAmount()) == 0
            && amounts.totalAmount().compareTo(other.amounts.totalAmount()) == 0;
    }
    /*BookingQuote giữ dữ liệu báo giá mà khách được xem;
     samePrice() giúp service phát hiện thay đổi giá trước khi tạo đơn*/
}
