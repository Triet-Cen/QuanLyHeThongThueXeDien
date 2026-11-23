package com.example.QuanLyChoThueXeDien.rental.dtos;

import com.example.QuanLyChoThueXeDien.search.dtos.VehicleSearchForm;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class BookingForm extends VehicleSearchForm {

    @NotNull(message = "Vui lòng chọn xe từ kết quả tìm kiếm.")
    private Integer vehicleId;

    @Size(max = 50, message = "Mã giảm tối đa 50 ký tự.")
    private String couponCode;

    public BookingForm copy() {
        BookingForm f = new BookingForm();
        f.setVehicleId(vehicleId); f.setCouponCode(couponCode);
        f.setStationId(getStationId()); f.setCategoryId(getCategoryId()); f.setModelId(getModelId());
        f.setStartAt(getStartAt()); f.setEndAt(getEndAt());
        return f;
    }
}
