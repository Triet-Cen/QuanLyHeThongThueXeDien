package com.example.QuanLyChoThueXeDien.search.services;


import com.example.QuanLyChoThueXeDien.VehicleModel.entities.VehicleModel;
import com.example.QuanLyChoThueXeDien.VehicleModel.repositories.VehicleModelRepository;
import com.example.QuanLyChoThueXeDien.category.repositories.VehicleCategoryRepository;
import com.example.QuanLyChoThueXeDien.pricing.entities.RentalPrice;
import com.example.QuanLyChoThueXeDien.pricing.repositories.RentalPriceRepository;
import com.example.QuanLyChoThueXeDien.search.dtos.SearchOptions;
import com.example.QuanLyChoThueXeDien.search.dtos.VehicleSearchCard;
import com.example.QuanLyChoThueXeDien.search.dtos.VehicleSearchForm;
import com.example.QuanLyChoThueXeDien.station.repositories.StationRepository;
import com.example.QuanLyChoThueXeDien.station.entities.Station;
import com.example.QuanLyChoThueXeDien.vehicle.entities.Vehicle;
import com.example.QuanLyChoThueXeDien.vehicle.repositories.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import com.example.QuanLyChoThueXeDien.pricing.services.RentalQuoteService;
import com.example.QuanLyChoThueXeDien.rental.services.RentalClock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleSearchService {
    private final StationRepository stations;
    private final VehicleCategoryRepository categories;
    private final VehicleModelRepository models;
    private final VehicleRepository vehicles;
    private final RentalPriceRepository prices;
    private final RentalClock clock;

    public SearchOptions options() {
        return new SearchOptions(
                stations.findAll().stream()
                        .filter(s -> "ACTIVE".equals(s.getStationStatus()))
                        .toList(),

                categories.findAll().stream()
                        .filter(c -> "ACTIVE".equals(c.getCategoryStatus()))
                        .toList(),

                models.findAllByOrderByModelIdAsc().stream()
                        .filter(m -> "ACTIVE".equals(m.getModelStatus()))
                        .filter(m -> "ACTIVE".equals(
                                m.getCategory().getCategoryStatus()))
                        .toList()
        );
    }  /*Hàm options này lấy trạm, loại xe và mẫu xe không bị ẩn đi.
     Nó trả về 3 đối tượng trạm, loại xe và mẫu xe*/

    public Optional<VehicleModel> findBookableModel(Integer modelId) {
        return models.findByModelId(modelId)
                .filter(m -> "ACTIVE".equals(m.getModelStatus()))
                .filter(m -> "ACTIVE".equals(m.getCategory().getCategoryStatus()));
    }

    public List<Station> bookingStations(Integer modelId) {
        return vehicles.findByVehicleModel_ModelIdAndStation_StationStatusOrderByVehicleIdAsc(modelId, "ACTIVE")
                .stream()
                .filter(vehicle -> !"MAINTENANCE".equalsIgnoreCase(vehicle.getVehicleStatus()))
                .map(Vehicle::getStation)
                .distinct()
                .toList();
    }

    public List<VehicleSearchCard> search(VehicleSearchForm form) {
        LocalDateTime now = clock.now();
        validate(form, now); //đoạn này validate cái form khách đã nhập trên trang home chỗ tìm kiếm

        List<Vehicle> matched = vehicles.searchAvailable(
                form.getStationId(),
                form.getCategoryId(),
                form.getModelId(),
                form.getStartAt(),
                form.getEndAt(),
                now
        ); //chỗ này để gọi các xe còn trống theo customer đã search

        long days = rentalDays(form.getStartAt(), form.getEndAt());

        // TK04: chọn giá theo ngày nhận, không theo ngày hôm nay.
        Map<Integer, List<RentalPrice>> priceGroups =
                prices.findEffectivePrices(form.getStartAt().toLocalDate())
                        .stream()
                        .collect(Collectors.groupingBy(
                                p -> p.getVehicleModel().getModelId()
                        ));

        return matched.stream().map(vehicle -> {
            var vehicleModel = vehicle.getVehicleModel();

            List<RentalPrice> applicable = priceGroups.getOrDefault(
                    vehicleModel.getModelId(), List.of()
            );

            BigDecimal dailyRate = null;
            BigDecimal total = null;
            String priceError = null;

            try {
                dailyRate = RentalQuoteService.requireRate(applicable);
                total = RentalQuoteService.amounts(dailyRate, days, BigDecimal.ZERO).totalAmount();
            } catch (IllegalArgumentException e) { priceError = e.getMessage(); } //kiểm tra giá và tính tổng tiền

            return new VehicleSearchCard(
                    vehicle.getVehicleId(),
                    vehicleModel.getModelName(),
                    vehicleModel.getImageUrl(),
                    vehicleModel.getBrand(),
                    vehicleModel.getSeatCount(),
                    vehicleModel.getCategory().getCategoryName(),
                    vehicle.getStation().getStationName(),
                    dailyRate,
                    days,
                    total,
                    priceError
            );
        }).toList(); //tạo thông tin này đêr hiển thị xe lên cho khách xem
    }

    private void validate(
            VehicleSearchForm form,
            LocalDateTime now
    ) {
        if (form.getStationId() == null
                || form.getStartAt() == null
                || form.getEndAt() == null) {
            throw new IllegalArgumentException(
                    "Vui lòng chọn trạm và nhập đủ ngày giờ nhận, trả."
            );
        }

        if (form.getStartAt().isBefore(now)) {
            throw new IllegalArgumentException(
                    "Giờ nhận xe không được nằm trong quá khứ."
            );
        }

        if (!form.getEndAt().isAfter(form.getStartAt())) {
            throw new IllegalArgumentException(
                    "Giờ trả xe phải sau giờ nhận xe."
            );
        }

        stations.findById(form.getStationId())
                .filter(s -> "ACTIVE".equals(s.getStationStatus()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Trạm không tồn tại hoặc đã ngừng hoạt động."
                ));

        if (form.getCategoryId() != null) {
            categories.findById(form.getCategoryId())
                    .filter(c -> "ACTIVE".equals(c.getCategoryStatus()))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Loại xe không tồn tại hoặc đã được ẩn."
                    ));
        }

        if (form.getModelId() != null) {
            var selected = findBookableModel(form.getModelId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Mẫu xe không tồn tại hoặc đã được ẩn."
                    ));

            if (form.getCategoryId() != null
                    && !form.getCategoryId().equals(
                    selected.getCategory().getCategoryId())) {
                throw new IllegalArgumentException(
                        "Mẫu xe không thuộc loại xe đã chọn."
                );
            }
        }
    }

    public static long rentalDays(
            LocalDateTime start,
            LocalDateTime end
    ) {
        return RentalQuoteService.rentalDays(start, end);
    } //tính số ngày thuê

}
