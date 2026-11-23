package com.example.QuanLyChoThueXeDien.station.repositories;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.QuanLyChoThueXeDien.station.entities.Station;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StationRepository extends JpaRepository<Station, Integer> {
    @Modifying
    @Query("delete from Station s where s.stationId = :stationId")
    int deleteStationId(@Param("stationId") Integer stationId);
}
