package com.example.QuanLyChoThueXeDien.payment.repositories;

import com.example.QuanLyChoThueXeDien.payment.dtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.Statement;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class PaymentRepository {
    private final JdbcTemplate db;

    public Optional<PaymentAttempt> findAttemptByRef(String ref) {
        var token = PaymentReference.parse(ref);
        if (token.isEmpty()) {
            if (ref == null || !ref.matches("[a-f0-9]{32}")) return Optional.empty();
            return db.query("""
                SELECT g.* FROM giao_dich_thanh_toan g WHERE EXISTS (
                    SELECT 1 FROM lich_su_trang_thai_don h WHERE h.MaDonThue=g.MaDonThue
                    AND h.GhiChu=CONCAT('VNPAY LEGACY #',g.MaGD,' ref=',?))
                """,(r,i)->new PaymentAttempt(r.getInt("MaGD"),r.getInt("MaDonThue"),r.getString("LoaiThanhToan"),
                    ref,"","VND","",r.getBigDecimal("SoTien"),r.getString("TrangThaiGD"),
                    r.getObject("ThoiGianGiaoDich",LocalDateTime.class),LocalDateTime.MIN),ref).stream().findFirst();
        }
        var t = token.get();
        return db.query("SELECT * FROM giao_dich_thanh_toan WHERE MaGD=?", (r,i) ->
            new PaymentAttempt(r.getInt("MaGD"),r.getInt("MaDonThue"),r.getString("LoaiThanhToan"),
                ref,"","VND",t.invoice(),r.getBigDecimal("SoTien"),r.getString("TrangThaiGD"),
                r.getObject("ThoiGianGiaoDich",LocalDateTime.class),t.expires()),t.id()).stream().findFirst();
    }

    public int saveAttempt(int rental, String kind, BigDecimal amount, LocalDateTime now) {
        var keys = new GeneratedKeyHolder();
        db.update(connection -> {
            var s = connection.prepareStatement("""
                INSERT INTO giao_dich_thanh_toan(MaDonThue,LoaiThanhToan,SoTien,ThoiGianGiaoDich,TrangThaiGD)
                VALUES(?,?,?,?,'PENDING')
                """,Statement.RETURN_GENERATED_KEYS);
            s.setInt(1,rental); s.setString(2,kind); s.setBigDecimal(3,amount);
            s.setObject(4,now); return s;
        },keys);
        return Objects.requireNonNull(keys.getKey()).intValue();
    }

    // Serialize provider-ID checks across orders without adding a unique index or table.
    // The row lock is released by the surrounding transaction, including on rollback.
    public void lockReceiptProcessing() {
        db.queryForList("SELECT MaGD FROM giao_dich_thanh_toan ORDER BY MaGD LIMIT 1 FOR UPDATE",Integer.class);
    }

    public boolean hasOtherReceipt(int id, String providerId) {
        return Boolean.TRUE.equals(db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM giao_dich_thanh_toan WHERE MaGD<>?
              AND MaGiaoDichVNPAYhoacSEPay=? AND TrangThaiGD IN ('SUCCESS','THÀNH CÔNG','NEEDS_RECONCILIATION','CẦN ĐỐI SOÁT'))
            """,Boolean.class,id,providerId));
    }

    public boolean hasSameReceipt(int id, String providerId) {
        return Boolean.TRUE.equals(db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM giao_dich_thanh_toan WHERE MaGD=? AND MaGiaoDichVNPAYhoacSEPay=?)
            """,Boolean.class,id,providerId));
    }

    public boolean hasReturnReport(int rental) {
        return !db.queryForList("SELECT MaBBTra FROM bien_ban_tra_xe WHERE MaDonThue=? FOR UPDATE",Integer.class,rental).isEmpty();
    }

    public List<PaymentFee> findRentalFees(int rental) {
        return db.query("""
            SELECT MaPhi,SoTien,LoaiPhi,LyDo,TrangThaiThanhToan FROM phi_phat_sinh
            WHERE MaDonThue=? ORDER BY MaPhi FOR UPDATE
            """,(r,i)->new PaymentFee(r.getInt(1),r.getBigDecimal(2),r.getString(3),r.getString(4),r.getString(5)),rental);
    }

    public boolean isRentalPaid(int rental) {
        return Boolean.TRUE.equals(db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM giao_dich_thanh_toan WHERE MaDonThue=?
              AND LoaiThanhToan IN ('RENTAL','TIỀN THUÊ') AND TrangThaiGD IN ('SUCCESS','THÀNH CÔNG'))
            """,Boolean.class,rental));
    }

    public void markFeePaid(PaymentAttempt a, PaymentFee fee) {
        if (db.update("""
            UPDATE phi_phat_sinh SET TrangThaiThanhToan='PAID'
            WHERE MaPhi=? AND MaDonThue=? AND TrangThaiThanhToan IN ('UNPAID','CHƯA THANH TOÁN')
            """,fee.id(),a.rental()) != 1) throw new IllegalStateException("Khoản phí đã thay đổi.");
    }

    public void updateAttemptStatus(PaymentAttempt a, VnPayEvent e, String status, LocalDateTime now) {
        db.update("""
            UPDATE giao_dich_thanh_toan SET TrangThaiGD=?,MaGiaoDichVNPAYhoacSEPay=?,ThoiGianGiaoDich=?
            WHERE MaGD=? AND TrangThaiGD NOT IN ('SUCCESS','THÀNH CÔNG')
            """,status,e.providerId(),now,a.id());
    }

    public void recordReview(PaymentAttempt a, VnPayEvent e, String reason, LocalDateTime now) {
        String note = "VNPAY REVIEW #"+a.id()+" "+reason+"; ref="+e.ref()+"; provider="+e.providerId()
            +"; received="+e.amount().toPlainString()+" "+e.currency();
        db.update("""
            INSERT INTO lich_su_trang_thai_don(MaDonThue,TrangThaiCu,TrangThaiMoi,ThoiGianThayDoi,GhiChu)
            SELECT MaDonThue,TrangThaiDon,TrangThaiDon,?,? FROM don_thue_xe d WHERE MaDonThue=?
              AND NOT EXISTS(SELECT 1 FROM lich_su_trang_thai_don h WHERE h.MaDonThue=d.MaDonThue AND h.GhiChu=?)
            """,now,note,a.rental(),note);
    }

    public boolean hasOpenMaintenance(int vehicle) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM bao_tri_xe WHERE MaXe=? AND NgayHoanTat IS NULL)",Boolean.class,vehicle));
    }

    public boolean hasRentedOrder(int vehicle) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM don_thue_xe WHERE MaXe=? AND TrangThaiDon='RENTED')",Boolean.class,vehicle));
    }

    public void sendPaymentNotifications(int account, PaymentAttempt a, LocalDateTime now) {
        String message = "Đơn #" + a.rental() + " · " + a.amount().toPlainString() + " VND · " + a.ref();
        db.update("INSERT INTO thong_bao(MaTKNhan,TieuDe,NoiDung,ThoiGianGui) VALUES(?,?,?,?)",account,"Đã xác nhận thanh toán",message,now);
        if ("EXTRA_FEE".equals(a.kind())) db.update("""
            INSERT INTO thong_bao(MaTKNhan,TieuDe,NoiDung,ThoiGianGui)
            SELECT n.MaTK,'Đã thu phí trả xe',?,? FROM bien_ban_tra_xe b JOIN nhan_vien n ON n.MaNV=b.MaNVNhan
            WHERE b.MaDonThue=?
            """,message,now,a.rental());
    }

    public List<Map<String,Object>> findPaymentsForReview() {
        return db.queryForList("""
            SELECT g.MaGD AS event_id,g.MaGD AS ref,g.MaDonThue AS rental,g.SoTien AS expected,
              g.MaGiaoDichVNPAYhoacSEPay AS provider_id,g.ThoiGianGiaoDich AS received_at,
              (SELECT h.GhiChu FROM lich_su_trang_thai_don h WHERE h.MaDonThue=g.MaDonThue
                AND h.GhiChu LIKE CONCAT('VNPAY REVIEW #',g.MaGD,' %') ORDER BY h.MaLichSu DESC LIMIT 1) AS disposition,
              (SELECT h.GhiChu FROM lich_su_trang_thai_don h WHERE h.MaDonThue=g.MaDonThue
                AND h.GhiChu LIKE CONCAT('VNPAY NOTE #',g.MaGD,' %') ORDER BY h.MaLichSu DESC LIMIT 1) AS resolution
            FROM giao_dich_thanh_toan g WHERE g.TrangThaiGD IN ('NEEDS_RECONCILIATION','CẦN ĐỐI SOÁT')
              OR EXISTS(SELECT 1 FROM lich_su_trang_thai_don h WHERE h.MaDonThue=g.MaDonThue
                AND h.GhiChu LIKE CONCAT('VNPAY REVIEW #',g.MaGD,' %'))
            ORDER BY g.MaGD DESC LIMIT 200
            """);
    }

    public boolean addReviewNote(long event, String email, String note, LocalDateTime now) {
        return db.update("""
            INSERT INTO lich_su_trang_thai_don(MaDonThue,TrangThaiCu,TrangThaiMoi,ThoiGianThayDoi,MaTKThucHien,GhiChu)
            SELECT d.MaDonThue,d.TrangThaiDon,d.TrangThaiDon,?,t.MaTK,?
            FROM giao_dich_thanh_toan g JOIN don_thue_xe d ON d.MaDonThue=g.MaDonThue
              JOIN tai_khoan t ON t.Email=? AND t.VaiTro='ADMIN' AND t.TrangThai='ACTIVE'
            WHERE g.MaGD=? AND (g.TrangThaiGD IN ('NEEDS_RECONCILIATION','CẦN ĐỐI SOÁT') OR EXISTS (
              SELECT 1 FROM lich_su_trang_thai_don h WHERE h.MaDonThue=d.MaDonThue
                AND h.GhiChu LIKE CONCAT('VNPAY REVIEW #',g.MaGD,' %')))
            """,now,"VNPAY NOTE #"+event+" "+note,email,event) == 1;
    }
}