package com.example.QuanLyChoThueXeDien.pricing.dtos;


import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class RentalPriceForm {

    @NotNull(message = "Bạn cần chọn mẫu xe.")
    private Integer modelId;

    @NotNull(message = "Bạn cần nhập đơn giá.")
    @DecimalMin(value = "0.01", message = "Đơn giá phải lớn hơn 0.")
    @Digits(integer = 13, fraction = 2, message = "Đơn giá tối đa 13 số nguyên và 2 số lẻ.")
    private BigDecimal dailyRate;

    @NotNull(message = "Bạn cần nhập ngày bắt đầu.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

}
