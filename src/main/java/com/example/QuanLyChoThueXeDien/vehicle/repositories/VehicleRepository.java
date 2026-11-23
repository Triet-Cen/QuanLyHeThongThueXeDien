package com.example.QuanLyChoThueXeDien.vehicle.repositories;

import com.example.QuanLyChoThueXeDien.vehicle.entities.Vehicle;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;

public interface VehicleRepository extends JpaRepository< Vehicle, Integer> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v where v.vehicleId = :id")
    Optional<Vehicle> findForUpdate(@Param("id") Integer id);

    @EntityGraph(attributePaths = {"vehicleModel", "station"})
    List<Vehicle> findAllByOrderByVehicleIdAsc();

    @EntityGraph(attributePaths = {"vehicleModel", "station"})
    Optional<Vehicle> findByVehicleId(Integer vehicleId);

    Optional<Vehicle> findByLicensePlate(String licensePlate);

    @EntityGraph(attributePaths = "station")
    List<Vehicle> findByVehicleModel_ModelIdAndStation_StationStatusOrderByVehicleIdAsc(
            Integer modelId,
            String stationStatus
    );// cai nay de xem xe thuoc cac mau duoc chon va tram dang hoat dong(khong bi an ben trang admin)

    @EntityGraph(attributePaths = {
            "station",
            "vehicleModel",
            "vehicleModel.category"
    })
    @Query("""
    select v from Vehicle v
    where v.station.stationId = :stationId
      and v.station.stationStatus = 'ACTIVE'
      and v.vehicleModel.modelStatus = 'ACTIVE'
      and v.vehicleModel.category.categoryStatus = 'ACTIVE'
      and upper(v.vehicleStatus) <> 'MAINTENANCE' /*lọc xe ở trạng thái đang không bị bảo trì*/
      and (:categoryId is null
           or v.vehicleModel.category.categoryId = :categoryId)
      and (:modelId is null
           or v.vehicleModel.modelId = :modelId)  /*đoạn này nếu customer nhập vào mà không lọc theo mẫu xe
               và lọc danh mục thì vẫn tìm kiếm được, sử dụng cho nhiều mục đích tìm kiếm khác nhau*/

      and not exists (
          select r.rentalId from Rental r
          where r.vehicle.vehicleId = v.vehicleId
            and r.startAt < :endAt
            and r.endAt > :startAt
            and (
                upper(r.status) in ('BOOKED', 'RENTED')
                or (
                    upper(r.status) in (
                        'PENDING_PAYMENT', 'CHỜ THANH TOÁN'
                    )
                    and r.holdExpiresAt > :now
                )
            )
      ) /*đoạn này nếu không thời gian mà customer nhập vô
           bị chồng lấn với xe đã có đơn thuê ở thời điểm đó thì nó sẽ không lấy ra*/

      and not exists (
          select overdue.rentalId from Rental overdue
          where overdue.vehicle.vehicleId = v.vehicleId
            and upper(overdue.status) = 'RENTED'
            and overdue.endAt <= :now
      )   
    order by v.vehicleModel.modelId, v.vehicleId
    """) /*đoạn này nó sẽ lọc xe đã được thuê nhưng khách trước đó chưa trả,
    mà quá thời hạn trả thì nó cũng xe không hiện vì thực tế nhân viên trạm xe chưa đến trạm đó trả lại xe*/


    List<Vehicle> searchAvailable(
            @Param("stationId") Integer stationId,
            @Param("categoryId") Integer categoryId,
            @Param("modelId") Integer modelId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("now") LocalDateTime now
    );

 }
