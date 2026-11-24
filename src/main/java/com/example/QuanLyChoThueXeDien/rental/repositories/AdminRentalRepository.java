package com.example.QuanLyChoThueXeDien.rental.repositories;

import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Repository
@RequiredArgsConstructor
public class AdminRentalRepository {
    private final NamedParameterJdbcTemplate db;
    private static final String JOINS = """
        FROM don_thue_xe d JOIN khach_hang k ON k.MaKH=d.MaKH JOIN tai_khoan a ON a.MaTK=k.MaTK
        JOIN xe x ON x.MaXe=d.MaXe JOIN mau_xe m ON m.MaMauXe=x.MaMauXe JOIN tram_xe t ON t.MaTram=d.MaTramNhan
        """;

    private static final String HEAD = """
        SELECT d.*, k.HoTen,k.SoDienThoai,a.Email,x.BienSo,x.TrangThaiXe,m.TenMauXe,t.TenTram,
            DATE_FORMAT(d.ThoiGianNhanDuKien,'%d/%m/%Y') pickupDate, TIME_FORMAT(d.ThoiGianNhanDuKien,'%H:%i:%s') pickupTime,
            DATE_FORMAT(d.ThoiGianTraDuKien,'%d/%m/%Y') returnDate, TIME_FORMAT(d.ThoiGianTraDuKien,'%H:%i:%s') returnTime
        """;

    public static final Map<String,String> STATUSES = Map.of("PENDING_PAYMENT","Chờ thanh toán","BOOKED","Đã đặt","RENTED","Đang thuê","CANCELLED","Đã hủy","COMPLETED","Hoàn tất","NO_SHOW","Không đến nhận");

    private static final String INCIDENT = "EXISTS (SELECT 1 FROM bao_cao_su_co s WHERE s.MaDonThue=d.MaDonThue AND s.MaXe=d.MaXe AND s.TrangThaiXuLy='NO_REPLACEMENT')";

    private static final Map<String,String> FLAGS = Map.of(
        "expired","d.TrangThaiDon IN ('PENDING_PAYMENT','CHỜ THANH TOÁN') AND d.ThoiHanGiuCho<=:now",
        "dispatch", INCIDENT,
        "fees","EXISTS (SELECT 1 FROM phi_phat_sinh f WHERE f.MaDonThue=d.MaDonThue AND f.TrangThaiThanhToan IN ('UNPAID','CHƯA THANH TOÁN'))",
        "newRefund","EXISTS (SELECT 1 FROM hoan_tien h WHERE h.MaDonThue=d.MaDonThue AND h.LyDoHoan='BR39' AND h.TrangThaiHoanTien='PENDING')",
        "refund","EXISTS (SELECT 1 FROM hoan_tien h WHERE h.MaDonThue=d.MaDonThue AND h.NgayHoanTat IS NULL AND h.TrangThaiHoanTien IN ('PENDING','PROCESSING','Chờ xử lý','Đang xử lý'))",
        "review","EXISTS (SELECT 1 FROM payment_ipn i JOIN giao_dich_thanh_toan g ON g.txn_ref=i.ref WHERE g.MaDonThue=d.MaDonThue AND i.disposition LIKE 'REVIEW%')");


    public void requireAdmin(String email) {
        if (db.queryForObject("SELECT COUNT(*) FROM tai_khoan WHERE Email=:email AND VaiTro='ADMIN' AND TrangThai='ACTIVE'",Map.of("email",email),Integer.class)!=1)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    public List<Map<String,Object>> stations() {
        return db.queryForList("SELECT MaTram,TenTram FROM tram_xe ORDER BY TenTram,MaTram",Map.of());
    }

    public record Results(List<Map<String,Object>> rows,long total) {}

    public Results search(String q,Integer station,String status,String basis,LocalDate from,LocalDate to,String flag,int page,int size,java.time.LocalDateTime now) {
        if(page<1 || size<1 || size>100 || !Set.of("created","pickup").contains(basis)
            || (!status.isEmpty() && !STATUSES.containsKey(status)) || (!flag.isEmpty() && !FLAGS.containsKey(flag))
            || (from!=null && to!=null && from.isAfter(to)) || (to!=null && to.getYear()>9999))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bộ lọc không hợp lệ");
        var params=new HashMap<String,Object>();
        params.put("now",now);
        String where=" WHERE 1=1";
        if (!q.isBlank()) {
            params.put("q",q.trim());
            where+=" AND (CAST(d.MaDonThue AS CHAR)=:q OR CONCAT('DT',d.MaDonThue)=UPPER(:q) OR LOCATE(:q,k.HoTen)>0 OR LOCATE(:q,k.SoDienThoai)>0 OR LOCATE(:q,a.Email)>0 OR LOCATE(:q,x.BienSo)>0)";
        }
        if(station!=null) {
            params.put("station",station);
            if(db.queryForObject("SELECT COUNT(*) FROM tram_xe WHERE MaTram=:station",params,Integer.class)==0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Trạm không tồn tại");
            where+=" AND d.MaTramNhan=:station";
        }
        if(!status.isEmpty()) {
            params.put("status",status);
            where+=status.equals("PENDING_PAYMENT")?" AND d.TrangThaiDon IN (:status,'CHỜ THANH TOÁN')":" AND d.TrangThaiDon=:status";
        }
        String date=basis.equals("created")?"d.NgayTao":"d.ThoiGianNhanDuKien";
        if(from!=null){where+=" AND "+date+">=:start";params.put("start",from.atStartOfDay());}
        if(to!=null){where+=" AND "+date+"<:end";params.put("end",to.plusDays(1).atStartOfDay());}
        if(!flag.isEmpty()) where+=" AND ("+FLAGS.get(flag)+")";
        long count=db.queryForObject("SELECT COUNT(*) "+JOINS+where,params,Long.class);
        params.put("size",size);params.put("offset",(long)(page-1)*size);
        String alerts=", CASE WHEN "+FLAGS.get("expired")+" THEN 'Đã hết giữ lịch' WHEN d.TrangThaiDon='BOOKED' AND d.ThoiGianNhanDuKien<DATE_SUB(:now,INTERVAL 60 MINUTE) THEN 'Staff kiểm tra khách đến nhận' WHEN d.TrangThaiDon='RENTED' AND EXISTS(SELECT 1 FROM bien_ban_tra_xe b WHERE b.MaDonThue=d.MaDonThue) THEN 'Đã tiếp nhận xe' WHEN d.TrangThaiDon='RENTED' AND d.ThoiGianTraDuKien<:now THEN 'Quá giờ trả xe' END warning ";
        return new Results(db.queryForList(HEAD+alerts+JOINS+where+" ORDER BY d.NgayTao DESC,d.MaDonThue DESC LIMIT :size OFFSET :offset",params),count);
    }

    public Map<String,Object> rental(int id) {
        return db.queryForList(HEAD+", "+INCIDENT+" noReplacement, EXISTS(SELECT 1 FROM bien_ban_giao_xe b WHERE b.MaDonThue=d.MaDonThue) handed "+JOINS+" WHERE d.MaDonThue=:id",Map.of("id",id)).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public Map<String,List<Map<String,Object>>> records(int id) {
        Map<String,List<Map<String,Object>>> sections=new LinkedHashMap<>();
        Map<String,String> queries=new LinkedHashMap<>();
        queries.put("Tổng hợp thanh toán (đồng)","""
            SELECT d.TongTienThue AS `Tiền thuê chốt`,
            COALESCE((SELECT SUM(SoTien) FROM phi_phat_sinh WHERE MaDonThue=d.MaDonThue),0) AS `Tổng phí`,
            COALESCE((SELECT SUM(SoTien) FROM phi_phat_sinh WHERE MaDonThue=d.MaDonThue AND TrangThaiThanhToan IN ('UNPAID','CHƯA THANH TOÁN')),0) AS `Phí chưa trả`,
            COALESCE((SELECT SUM(g.SoTien) FROM giao_dich_thanh_toan g WHERE g.MaDonThue=d.MaDonThue AND EXISTS
                (SELECT 1 FROM payment_allocation a WHERE a.transaction_id=g.MaGD AND a.obligation LIKE 'RENTAL:%')),0) AS `Đã thu thuê`,
            COALESCE((SELECT SUM(g.SoTien) FROM giao_dich_thanh_toan g WHERE g.MaDonThue=d.MaDonThue AND EXISTS
                (SELECT 1 FROM payment_allocation a WHERE a.transaction_id=g.MaGD AND a.obligation LIKE 'FEE:%')),0) AS `Đã thu phí`,
            COALESCE((SELECT SUM(SoTienHoan) FROM hoan_tien WHERE MaDonThue=d.MaDonThue AND NgayHoanTat IS NOT NULL),0) AS `Đã hoàn`,
            COALESCE((SELECT SUM(SoTienHoan) FROM hoan_tien WHERE MaDonThue=d.MaDonThue AND NgayHoanTat IS NULL
                AND TrangThaiHoanTien IN ('PENDING','PROCESSING','Chờ xử lý','Đang xử lý')),0) AS `Chờ hoàn`
            FROM don_thue_xe d WHERE d.MaDonThue=:id
            """);
        queries.put("Giao dịch","SELECT MaGD,LoaiThanhToan,PhuongThucThanhToan,txn_ref,MaGiaoDichVNPAYhoacSEPay,SoTien,TrangThaiGD,ThoiGianGiaoDich FROM giao_dich_thanh_toan WHERE MaDonThue=:id ORDER BY MaGD DESC");
        queries.put("Phân bổ","SELECT a.obligation,g.MaGD FROM payment_allocation a JOIN giao_dich_thanh_toan g ON g.MaGD=a.transaction_id WHERE g.MaDonThue=:id");
        queries.put("Đối soát","SELECT i.event_id,i.received_amount,i.received_at,i.disposition,n.note,n.recorded_at FROM payment_ipn i JOIN giao_dich_thanh_toan g ON g.txn_ref=i.ref LEFT JOIN payment_review_note n ON n.event_id=i.event_id WHERE g.MaDonThue=:id AND i.disposition LIKE 'REVIEW%'");
        queries.put("Phí phát sinh","SELECT * FROM phi_phat_sinh WHERE MaDonThue=:id");
        queries.put("Hoàn tiền","SELECT * FROM hoan_tien WHERE MaDonThue=:id");
        queries.put("Biên bản giao","SELECT * FROM bien_ban_giao_xe WHERE MaDonThue=:id");
        queries.put("Biên bản trả","SELECT * FROM bien_ban_tra_xe WHERE MaDonThue=:id");
        queries.put("Sự cố","SELECT * FROM bao_cao_su_co WHERE MaDonThue=:id");
        queries.put("Bảo trì liên quan","SELECT b.* FROM bao_tri_xe b JOIN bao_cao_su_co s ON s.MaSuCo=b.MaSuCo WHERE s.MaDonThue=:id");
        queries.put("Lịch sử","SELECT * FROM lich_su_trang_thai_don WHERE MaDonThue=:id ORDER BY ThoiGianThayDoi DESC,MaLichSu DESC");
        queries.forEach((title,sql)->sections.put(title,db.queryForList(sql,Map.of("id",id))));
        return sections;
    }
}
