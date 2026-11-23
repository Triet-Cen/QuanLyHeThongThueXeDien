package com.example.QuanLyChoThueXeDien.search.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Getter
@Setter
public class VehicleSearchForm {
    @NotNull(message = "Vui lòng chọn trạm nhận xe")
    private Integer stationId;

    private Integer categoryId;
    private Integer modelId;

    @NotNull(message = "Vui lòng nhập ngày giờ nhận xe")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startAt;

    @NotNull(message = "Vui lòng nhập ngày giờ trả xe")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endAt;
}