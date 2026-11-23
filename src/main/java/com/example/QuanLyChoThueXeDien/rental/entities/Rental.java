package com.example.QuanLyChoThueXeDien.rental.entities;

import com.example.QuanLyChoThueXeDien.vehicle.entities.Vehicle;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.example.QuanLyChoThueXeDien.auth.entities.CustomerProfile;
import com.example.QuanLyChoThueXeDien.station.entities.Station;
import java.math.BigDecimal;

import java.time.LocalDateTime;

@Entity
@Table(name = "don_thue_xe")
@Getter
@Setter
public class Rental {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaDonThue")
    private Integer rentalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MaXe")
    private Vehicle vehicle;

    @Column(name = "ThoiGianNhanDuKien")
    private LocalDateTime startAt;

    @Column(name = "ThoiGianTraDuKien")
    private LocalDateTime endAt;

    @Column(name = "ThoiHanGiuCho")
    private LocalDateTime holdExpiresAt;

    @Column(name = "TrangThaiDon")
    private String status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaKH")
    private CustomerProfile customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaTramNhan")
    private Station pickupStation;

    @Column(name = "MaGiamGia")
    private String couponCode;

    @Column(name = "SoNgayThue")
    private Integer rentalDays;

    @Column(name = "DonGiaNgayApDung")
    private BigDecimal dailyRate;

    @Column(name = "TienThue")
    private BigDecimal rentalAmount;

    @Column(name = "TienGiam")
    private BigDecimal discountAmount;

    @Column(name = "TongTienThue")
    private BigDecimal totalAmount;

    @Column(name = "NgayTao")
    private LocalDateTime createdAt;

    public boolean isPending() {
        return "PENDING_PAYMENT".equalsIgnoreCase(status) || "CHỜ THANH TOÁN".equalsIgnoreCase(status);
    }

    public boolean hasActiveHold(LocalDateTime now) {
        return isPending() && holdExpiresAt != null && now.isBefore(holdExpiresAt);
    }
}
