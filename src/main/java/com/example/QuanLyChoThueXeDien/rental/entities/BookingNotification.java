package com.example.QuanLyChoThueXeDien.rental.entities;

import jakarta.persistence.*;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name = "thong_bao") @Setter
public class BookingNotification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaThongBao") private Integer id;
    @Column(name = "MaTKNhan") private Integer accountId;
    @Column(name = "TieuDe") private String title;
    @Column(name = "NoiDung") private String content;
    @Column(name = "ThoiGianGui") private LocalDateTime sentAt;
    @Column(name = "TrangThaiDaDoc") private boolean read;
}
