package com.example.QuanLyChoThueXeDien.rental.entities;

import jakarta.persistence.*;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name = "ma_giam_gia") @Getter
public class Coupon {
    @Id @Column(name = "MaGiamGia") private String code;
    @Column(name = "PhanTramGiam") private BigDecimal percent;
    @Column(name = "NgayBatDau") private LocalDateTime startsAt;
    @Column(name = "NgayKetThuc") private LocalDateTime endsAt;
    @Column(name = "SoLuongConLai") private Integer remainingQuantity;
    @Column(name = "TrangThai") private String status;
}
