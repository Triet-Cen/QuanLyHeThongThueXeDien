package com.example.QuanLyChoThueXeDien.license.entities;
import com.example.QuanLyChoThueXeDien.auth.entities.Account;
import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "nhan_vien")
@Getter
public class LicenseVerifier {
    @Id
    @Column(name = "MaNV")
    private Integer staffId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MaTK")
    private Account account;

    @Column(name = "HoTen")
    private String fullName;
}
