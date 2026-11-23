package com.example.QuanLyChoThueXeDien.auth.dtos;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {


    @NotBlank(message = "Vui lòng nhập họ tên")
    @Size (min = 2, max= 255, message = "Họ tên không hợp lệ")
    private String fullName;

    @NotBlank(message = "Vui lòng nhập email")
    @Email(message = "Email không hợp lệ")
    @Size (max = 50, message = "Email quá dài")
    @Pattern(
            regexp = "[^@]+@[^@.]+(\\.[^@.]+)+",
            message = "Phần sau @ phải có dấu chấm, ví dụ ten@gmail.com"
    )
    private String email;

    @NotBlank(message = "Vui lòng nhập số điện thoai")
    @Pattern(
            regexp = "^0\\d{9}$",
            message = "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0"
    )
    private String phone;

    @NotBlank(message = "Vui lòng nhập mật khẩu")
    @Size (min = 6, max = 50, message = "Mật khẩu ít nhất 6 ký tự")
    private String password;


    @NotBlank(message = "Vui lòng xác nhận mật khẩu đúng")
    private String confirmPassword;
}
