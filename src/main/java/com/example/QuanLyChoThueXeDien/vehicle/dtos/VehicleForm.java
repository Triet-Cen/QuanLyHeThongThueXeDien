package com.example.QuanLyChoThueXeDien.vehicle.dtos;


import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class VehicleForm {


    @NotNull(message = "Bạn phải chọn mẫu xe")
    private Integer modelId;

    @NotNull(message = "Bạn phải chọn trạm cho xe")
    private Integer stationId;

    @NotNull(message = "Biển số phải nhập")
    @Size(max = 20, message = "Nhập tối đa 20 ký tự")
    private String licensePlate;

    @Size(max = 30, message = "Màu sắc tối đa 30 ký tự.")
    private String color;

    @NotNull(message = "Bạn cần nhập mức pin.")
    @DecimalMin(value = "0", message = "Mức pin phải từ 0 đến 100.")
    @DecimalMax(value = "100", message = "Mức pin phải từ 0 đến 100.")
    @Digits(integer = 3, fraction = 2, message = "Mức pin có tối đa 2 chữ số thập phân.")
    private BigDecimal batteryLevel;
}
