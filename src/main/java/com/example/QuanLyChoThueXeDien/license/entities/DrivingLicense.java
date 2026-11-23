package com.example.QuanLyChoThueXeDien.license.entities;
import com.example.QuanLyChoThueXeDien.auth.entities.CustomerProfile;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.example.QuanLyChoThueXeDien.auth.entities.Account;

@Entity
@Table(name = "giay_phep_lai_xe")
@Getter
@Setter
public class DrivingLicense {
    public static final String UNVERIFIED = "Chưa xác minh";
    public static final String VERIFIED = "Đã xác minh";
    public static final String REJECTED = "Từ chối";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaGPLX")
    private Integer licenseId;


    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MaKH", unique = true)
    private CustomerProfile customer;


    @Column(name = "SoGPLX", nullable = false, unique = true)
    private String licenseNumber;


    @Column(name = "HangGPLX", nullable = false, length = 5)
    private String licenseClass;


    @Column(name = "NgayCap", nullable = false)
    private LocalDate issueDate;


    @Column(name = "NgayHetHan", nullable = false)
    private LocalDate expiryDate;


    @Column(name = "AnhGPLX", nullable = false, length = 2048)
    private String imagePath;


    @Column(name = "TrangThaiXacMinh", nullable = false)
    private String verificationStatus;


    @Column(name = "NgayXacMinh")
    private LocalDateTime verifiedAt;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MaNVXacMinh")
    private LicenseVerifier verifiedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MaTKXacMinh")
    private Account reviewedBy;
}
