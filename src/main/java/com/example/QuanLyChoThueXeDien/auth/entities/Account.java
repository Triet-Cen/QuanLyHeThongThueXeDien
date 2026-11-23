package com.example.QuanLyChoThueXeDien.auth.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.PreUpdate;
import jakarta.persistence.PrePersist;
import java.time.LocalDateTime;
import java.util.Date;

@Entity
@Table(name="tai_khoan")
@Getter
@Setter
@NoArgsConstructor

public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) //Khóa tự tăng
    @Column(name ="MaTK", nullable = false)
    private Integer accountId;

    @Column(name= "Email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "MatKhau", nullable = false, length = 255)
    private String password;

    @Column(name = "VaiTro", nullable = false, length = 255)
    private String role;

    @Column(name = "TrangThai", nullable = false, length = 255)
    private String status;

    @Column(name = "NgayTao", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updatedAt", nullable = false)
    private Date updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = new Date();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Date();
    }


}
