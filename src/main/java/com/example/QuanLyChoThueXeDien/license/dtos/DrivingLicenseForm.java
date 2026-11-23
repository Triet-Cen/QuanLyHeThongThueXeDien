package com.example.QuanLyChoThueXeDien.license.dtos;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;

@Getter @Setter
public class DrivingLicenseForm {


    @NotBlank(message = "Vui lòng nhập số GPLX")
    @Size(max = 100, message = "Số GPLX tối đa 100 ký tự")
    private String licenseNumber;


    @NotBlank(message = "Vui lòng chọn hạng GPLX")
    @Pattern(regexp = "B", message = "Chỉ chấp nhận GPLX hạng B")
    private String licenseClass = "B";


    @NotNull(message = "Vui lòng nhập ngày cấp")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate issueDate;


    @NotNull(message = "Vui lòng nhập ngày hết hạn")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate expiryDate;
    private MultipartFile image;
}
