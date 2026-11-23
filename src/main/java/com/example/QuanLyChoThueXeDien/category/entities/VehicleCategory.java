package com.example.QuanLyChoThueXeDien.category.entities;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "loai_xe")
@Getter
@Setter
@NoArgsConstructor
public class VehicleCategory {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaLoaiXe")
    private Integer categoryId;

    @Column(name = "TenLoaiXe", nullable = false, length = 60)
    private String categoryName;

    @Column(name = "MoTa", nullable = false, length = 60)
    private String description;

    @Column(name = "TrangThai", nullable = false)
    private String categoryStatus = "ACTIVE";

    public VehicleCategory(String categoryName, String description) {
        updateDetails(categoryName, description);
    }

    public void updateDetails(String categoryName, String description) {
        this.categoryName = categoryName;
        this.description = description;
    }
}
