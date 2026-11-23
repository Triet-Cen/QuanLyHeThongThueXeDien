package com.example.QuanLyChoThueXeDien.vehicle.service;


import com.example.QuanLyChoThueXeDien.VehicleModel.entities.VehicleModel;
import com.example.QuanLyChoThueXeDien.VehicleModel.services.VehicleModelService;
import com.example.QuanLyChoThueXeDien.station.entities.Station;
import com.example.QuanLyChoThueXeDien.station.services.StationService;
import com.example.QuanLyChoThueXeDien.vehicle.dtos.VehicleForm;
import com.example.QuanLyChoThueXeDien.vehicle.entities.Vehicle;
import com.example.QuanLyChoThueXeDien.vehicle.repositories.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final VehicleModelService modelService;
    private final StationService stationService;

    public VehicleService(VehicleRepository vehicleRepository,
                          VehicleModelService modelService,
                          StationService stationService) {
        this.vehicleRepository = vehicleRepository;
        this.modelService = modelService;
        this.stationService = stationService;
    }

    public List<Vehicle> findAll() {
        return vehicleRepository.findAllByOrderByVehicleIdAsc();
    }

    public Optional<Vehicle> findForEdit(Integer id) {
        return vehicleRepository.findByVehicleId(id);
    }

    public List<VehicleModel> modelsForForm(Integer currentModelId) {
        return modelService.findAll().stream()
                .filter(model -> canUseModel(model, currentModelId))
                .toList();
    }

    public List<Station> stationsForForm(Integer currentStationId) {
        return stationService.findAll().stream()
                .filter(station -> canUseStation(station, currentStationId))
                .toList();
    }

    @Transactional
    public boolean save(Integer id, VehicleForm form) {
        Vehicle vehicle = id == null
                ? new Vehicle()
                : vehicleRepository.findByVehicleId(id).orElse(null);

        if (vehicle == null) {
            return false;
        }

        Integer currentModelId = id == null ? null : vehicle.getVehicleModel().getModelId();
        Integer currentStationId = id == null ? null : vehicle.getStation().getStationId();

        VehicleModel model = modelService.findForEdit(form.getModelId())
                .filter(item -> canUseModel(item, currentModelId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Mẫu xe hoặc loại xe đã ẩn, hoặc mẫu xe không tồn tại."));

        Station station = stationService.findById(form.getStationId())
                .filter(item -> canUseStation(item, currentStationId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Trạm xe đã ẩn hoặc không tồn tại."));

        String plate = form.getLicensePlate().strip();

        if (vehicleRepository.findByLicensePlate(plate)
                .filter(other -> !other.getVehicleId().equals(id))
                .isPresent()) {
            throw new IllegalArgumentException("Biển số này đã được sử dụng.");
        }

        vehicle.updateDetails(model, station, plate, form.getColor(), form.getBatteryLevel());
        vehicleRepository.saveAndFlush(vehicle);
        return true;
    }

    private boolean canUseModel(VehicleModel model, Integer currentModelId) {
        return model.getModelId().equals(currentModelId)
                || ("ACTIVE".equals(model.getModelStatus())
                && "ACTIVE".equals(model.getCategory().getCategoryStatus()));
    }

    private boolean canUseStation(Station station, Integer currentStationId) {
        return station.getStationId().equals(currentStationId)
                || "ACTIVE".equals(station.getStationStatus());
    }

    public List<Vehicle> findByModelId(Integer modelId) {
        return vehicleRepository
                .findByVehicleModel_ModelIdAndStation_StationStatusOrderByVehicleIdAsc(
                        modelId,
                        "ACTIVE"
                );
    }

}
