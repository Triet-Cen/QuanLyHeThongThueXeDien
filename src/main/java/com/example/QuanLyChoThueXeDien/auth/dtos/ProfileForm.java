package com.example.QuanLyChoThueXeDien.auth.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
public class ProfileForm {
    @NotBlank(message = "Vui lòng nhập họ và tên")
    @Size(max = 255, message = "Họ và tên tối đa 255 ký tự")
    private String fullName;

    @NotBlank(message = "Vui lòng nhập số điện thoại")
    @Pattern(regexp = "^0\\d{9}$",
            message = "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0")
    private String phone;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate birthDate;

    @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
    private String address;
}
