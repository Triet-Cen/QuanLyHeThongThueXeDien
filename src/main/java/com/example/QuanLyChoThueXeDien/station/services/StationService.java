package com.example.QuanLyChoThueXeDien.station.services;


import com.example.QuanLyChoThueXeDien.station.entities.Station;
import com.example.QuanLyChoThueXeDien.station.repositories.StationRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
@Service
public class StationService {

    private final StationRepository stationRepository;

    public StationService(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
    }

    public List<Station> findAll(){
        return stationRepository.findAll(Sort.by("stationId"));
    }

    public void create(String name, String address, String phone){
        Station sta = new Station(
                name.strip(),
                address.strip(),
                phone.strip(),
                "ACTIVE"
        );
        stationRepository.save(sta);
    }

    public Optional<Station> findById(Integer stationId){
        return stationRepository.findById(stationId);
    }

    @Transactional
    public  boolean updateStation(Integer stationId, String name, String address, String phone){
        Station station= stationRepository.findById(stationId).orElse(null);

        if(station==null){
            return false;
        }

        station.updateDetails(name.strip(),address.strip(),phone.strip());
        return true;
    }

    @Transactional
    public boolean changeVisibility(Integer stationId, boolean visible) {
        Station station = stationRepository.findById(stationId).orElse(null);
        if (station == null) {
            return false;
        }

        station.setStationStatus(visible ? "ACTIVE" : "INACTIVE");
        return true;
    }

}
