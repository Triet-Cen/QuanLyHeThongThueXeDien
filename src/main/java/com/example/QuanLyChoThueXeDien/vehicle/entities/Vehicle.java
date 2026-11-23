package com.example.QuanLyChoThueXeDien.vehicle.entities;


import com.example.QuanLyChoThueXeDien.VehicleModel.entities.VehicleModel;
import com.example.QuanLyChoThueXeDien.station.entities.Station;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import jakarta.persistence.Column;

import java.math.BigDecimal;

@Entity
@Table(name = "xe")
@Getter
@Setter
@DynamicUpdate
@NoArgsConstructor

public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaXe")
    private Integer vehicleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaMauXe", nullable = false)
    private VehicleModel vehicleModel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name="MaTram", nullable = false)
    private Station station;

    @Column(name = "BienSo", nullable = false, unique = true)
    private String licensePlate;

    @Column(name="MauSac", nullable = false)
    private String color;

    @Column(name="MucPin", nullable = false, precision = 5, scale = 2)
    private BigDecimal batteryLevel;

    @Column(name = "TrangThaiXe", nullable = false)
    private String vehicleStatus = "Available";

    public void updateDetails(VehicleModel vehicleModel, Station station,
                              String licensePlate, String color, BigDecimal batteryLevel) {
        this.vehicleModel = vehicleModel;
        this.station = station;
        this.licensePlate = licensePlate;
        this.color = color == null || color.isBlank() ? null : color.strip();
        this.batteryLevel = batteryLevel;
    }

}
