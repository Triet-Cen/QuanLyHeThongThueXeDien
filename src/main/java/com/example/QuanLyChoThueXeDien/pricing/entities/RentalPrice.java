package com.example.QuanLyChoThueXeDien.pricing.entities;


import com.example.QuanLyChoThueXeDien.VehicleModel.entities.VehicleModel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "gia_thue")
@Getter
@Setter
@NoArgsConstructor
public class RentalPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaGia")
    private Integer priceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaMauXe", nullable = false)
    private VehicleModel vehicleModel;

    @Column(name = "DonGiaNgay", nullable = false, precision = 15, scale = 2)
    private BigDecimal dailyRate;

    @Column(name = "NgayBatDauApDung", nullable = false)
    private LocalDate startDate;

    @Column(name = "NgayKetThucApDung")
    private LocalDate endDate;

    public RentalPrice(VehicleModel vehicleModel, BigDecimal dailyRate,
                       LocalDate startDate, LocalDate endDate) {
        this.vehicleModel = vehicleModel;
        this.dailyRate = dailyRate;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void endOn(LocalDate endDate) {
        this.endDate = endDate;
    }
}
