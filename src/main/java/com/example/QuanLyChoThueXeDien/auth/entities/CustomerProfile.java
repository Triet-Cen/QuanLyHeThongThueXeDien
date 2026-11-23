package com.example.QuanLyChoThueXeDien.auth.entities;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;


@Entity
@Table(name = "khach_hang")
@Getter
@Setter
@NoArgsConstructor
public class CustomerProfile  {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaKH")
    private Integer CustomerId;

    @OneToOne(fetch = FetchType.LAZY, optional = false) // fetchtype.lazy giúp tiết kiệm tài nguyên và query dữ liệu không dùng đến
    @JoinColumn(name = "MaTK", nullable = false, unique = true)
    private Account account;

    @Column(name = "HoTen", nullable = false, length = 255)
    private String fullName;

    @Column(name = "SoDienThoai", nullable = false, length = 20)
    private String phone;

    @Column(name = "NgaySinh")
    private LocalDate birthDate;

    @Column(name = "DiaChi", length = 255)
    private String address;

}
