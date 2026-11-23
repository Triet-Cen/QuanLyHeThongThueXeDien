package com.example.QuanLyChoThueXeDien.search.dtos;

import com.example.QuanLyChoThueXeDien.station.entities.Station;
import com.example.QuanLyChoThueXeDien.category.entities.VehicleCategory;
import com.example.QuanLyChoThueXeDien.VehicleModel.entities.VehicleModel;

import java.util.List;

public record SearchOptions(
        List<Station> stations,
        List<VehicleCategory> categories,
        List<VehicleModel> models
) {}