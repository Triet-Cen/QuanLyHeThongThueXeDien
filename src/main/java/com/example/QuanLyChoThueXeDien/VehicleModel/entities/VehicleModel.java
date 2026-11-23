package com.example.QuanLyChoThueXeDien.VehicleModel.entities;

import com.example.QuanLyChoThueXeDien.category.entities.VehicleCategory;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "mau_xe")
@Getter
@NoArgsConstructor
public class VehicleModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaMauXe")
    private Integer modelId;

    @Column(name = "TenMauXe", nullable = false, length = 40)
    private String modelName;

    @Column(name = "HangXe", nullable = false, length = 40)
    private String brand;

    @Column(name = "SoChoNgoi", nullable = false)
    private Integer seatCount;

    @Column(name = "MoTa")
    private String description;

    @Column(name = "HinhAnh", length = 2048)
    private String imageUrl;

    @Column(name = "TrangThai", nullable = false)
    private String modelStatus = "ACTIVE";

    public void setModelStatus(String modelStatus) {
        this.modelStatus = modelStatus;
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaLoaiXe", nullable = false)
    private VehicleCategory category;

    public void updateDetails(
            String modelName,
            String brand,
            Integer seatCount,
            String description,
            VehicleCategory category
    ) {
        this.modelName = modelName.strip();
        this.brand = brand.strip();
        this.seatCount = seatCount;
        this.description = description == null || description.isBlank()
                ? null
                : description.strip();
        this.category = category;
    }
}