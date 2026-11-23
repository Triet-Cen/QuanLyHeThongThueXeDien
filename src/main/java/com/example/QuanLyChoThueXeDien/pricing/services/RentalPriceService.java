package com.example.QuanLyChoThueXeDien.pricing.services;


import com.example.QuanLyChoThueXeDien.VehicleModel.entities.VehicleModel;
import com.example.QuanLyChoThueXeDien.VehicleModel.repositories.VehicleModelRepository;
import com.example.QuanLyChoThueXeDien.pricing.dtos.RentalPriceForm;
import com.example.QuanLyChoThueXeDien.pricing.entities.RentalPrice;
import com.example.QuanLyChoThueXeDien.pricing.repositories.RentalPriceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

@Service
public class RentalPriceService {

    private final RentalPriceRepository rentalPriceRepository;
    private final VehicleModelRepository vehicleModelRepository;

    public RentalPriceService(RentalPriceRepository rentalPriceRepository,
                              VehicleModelRepository vehicleModelRepository) {
        this.rentalPriceRepository = rentalPriceRepository;
        this.vehicleModelRepository = vehicleModelRepository;
    }

    public List<RentalPrice> findAll() {
        return rentalPriceRepository.findAllByOrderByStartDateDescPriceIdDesc();
    }

    @Transactional
    public void create(RentalPriceForm form) {
        LocalDate start = form.getStartDate();
        LocalDate end = form.getEndDate();

        if (end != null && end.isBefore(start)) {
            throw new IllegalArgumentException(
                    "Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.");
        } //kiểm tra ngày bắt đầu áp dụng và ngày kết thúc áp dụng giá này

        VehicleModel model = vehicleModelRepository.findByIdForPriceUpdate(form.getModelId())
                .orElseThrow(() -> new IllegalArgumentException("Mẫu xe không tồn tại."));
        //tìm mẫu xe

        List<RentalPrice> overlapping = rentalPriceRepository.findOverlapping(
                model.getModelId(), start, end);

        if (!overlapping.isEmpty()) {
            RentalPrice previous = overlapping.getFirst();

            if (overlapping.size() != 1 || previous.getEndDate() != null
                    || !previous.getStartDate().isBefore(start)) {
                throw new IllegalArgumentException(
                        "Kỳ giá bị trùng với một kỳ đã có của mẫu xe này.");
            }

            previous.endOn(start.minusDays(1));
        }

        rentalPriceRepository.save(new RentalPrice(model, form.getDailyRate(), start, end));
    }
}
