package com.example.QuanLyChoThueXeDien.VehicleModel.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VehicleModelForm {

    @NotBlank(message = "Vui lòng nhập tên mẫu xe.")
    @Size(max = 40, message = "Tên mẫu xe tối đa 40 ký tự.")
    private String modelName;

    @NotBlank(message = "Vui lòng nhập hãng xe.")
    @Size(max = 40, message = "Hãng xe tối đa 40 ký tự.")
    private String brand;

    @NotNull(message = "Vui lòng nhập số chỗ ngồi.")
    @Positive(message = "Số chỗ ngồi phải lớn hơn 0.")
    private Integer seatCount;

    @NotNull(message = "Vui lòng chọn loại xe.")
    @Positive(message = "Mã loại xe phải lớn hơn 0.")
    private Integer categoryId;

    @Size(max = 200, message = "Mô tả tối đa 200 ký tự.")
    private String description;
}