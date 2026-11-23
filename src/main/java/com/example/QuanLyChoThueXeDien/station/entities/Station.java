package com.example.QuanLyChoThueXeDien.station.entities;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tram_xe")
@Getter
@Setter
@NoArgsConstructor
public class Station {

    public Station(String stationName,
                   String stationAddress,
                   String stationPhoneNumber,
                   String stationStatus) {
        this.stationName = stationName;
        this.stationAddress = stationAddress;
        this.stationPhoneNumber = stationPhoneNumber;
        this.stationStatus = stationStatus;
    }

    public void updateDetails(String stationName,String stationAddress, String stationPhoneNumber){
        this.stationName= stationName;
        this.stationAddress= stationAddress;
        this.stationPhoneNumber= stationPhoneNumber;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaTram")
    private Integer stationId;

    @Column(name = "TenTram")
    private String stationName;

    @Column(name ="DiaChi")
    private String stationAddress;

    @Column(name ="SoDienThoai")
    private String stationPhoneNumber;

    @Column(name = "TrangThai")
    private String stationStatus;

}
