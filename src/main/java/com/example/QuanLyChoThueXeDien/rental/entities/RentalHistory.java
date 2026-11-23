package com.example.QuanLyChoThueXeDien.rental.entities;

import jakarta.persistence.*;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name = "lich_su_trang_thai_don") @Setter
public class RentalHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaLichSu") private Integer id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "MaDonThue") private Rental rental;
    @Column(name = "TrangThaiCu") private String previousStatus;
    @Column(name = "TrangThaiMoi") private String newStatus;
    @Column(name = "ThoiGianThayDoi") private LocalDateTime changedAt;
    @Column(name = "MaTKThucHien") private Integer actorId;
    @Column(name = "GhiChu") private String reason;
}
