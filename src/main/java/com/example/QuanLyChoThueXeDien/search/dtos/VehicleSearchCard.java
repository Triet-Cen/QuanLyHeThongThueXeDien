package com.example.QuanLyChoThueXeDien.search.dtos;

import java.math.BigDecimal;

public record VehicleSearchCard(
        Integer vehicleId,
        String modelName,
        String imageUrl,
        String brand,
        Integer seatCount,
        String categoryName,
        String stationName,
        BigDecimal dailyRate,
        long rentalDays,
        BigDecimal estimatedAmount,
        String priceError
) {}