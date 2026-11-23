package com.example.QuanLyChoThueXeDien.station.dtos;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StationForm {
    @NotBlank(message = "Vui lòng nhập tên trạm")
    @Size(max=60,message = "Tối đa 60 ký tự")
    private String stationName;

    @NotBlank(message = "Vui lòng nhập đầy đủ địa chỉ")
    @Size(max=60,message = "Tối đa 60 ký tự")
    private String stationAddress;


    @NotBlank(message = "Vui lòng nhập đầy đủ địa chỉ")
    @Size(max=60,message = "Tối đa 60 ký tự")
    private String stationPhoneNumber;

}
