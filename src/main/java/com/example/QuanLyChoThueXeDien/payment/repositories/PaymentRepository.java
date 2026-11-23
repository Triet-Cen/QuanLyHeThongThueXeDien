package com.example.QuanLyChoThueXeDien.payment.repositories;

import com.example.QuanLyChoThueXeDien.payment.dtos.PaymentFee;
import com.example.QuanLyChoThueXeDien.payment.dtos.PaymentAttempt;
import com.example.QuanLyChoThueXeDien.payment.dtos.VnPayEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class PaymentRepository {
    private final JdbcTemplate db;

    public Optional<PaymentAttempt> findAttemptByRef(String ref) {
        return findAttempts("txn_ref=? AND PhuongThucThanhToan='VNPAY'",ref).stream().findFirst();
    }

    public Optional<PaymentAttempt> findAttemptByRequest(int rental, UUID request) {
        return findAttempts("""
            MaDonThue=? AND PhuongThucThanhToan='VNPAY' AND request_key=?
            """,rental,request.toString()).stream().findFirst();
    }

    private List<PaymentAttempt> findAttempts(String predicate, Object... args) {
        return db.query("SELECT * FROM giao_dich_thanh_toan WHERE " + predicate,
            (r,i) -> new PaymentAttempt(r.getInt("MaGD"),r.getInt("MaDonThue"),r.getString("LoaiThanhToan"),
            r.getString("txn_ref"),r.getString("merchant"),r.getString("currency"),r.getString("invoice_key"),
            r.getBigDecimal("SoTien"),r.getString("TrangThaiGD"),r.getObject("created_at",LocalDateTime.class),
            r.getObject("expires_at",LocalDateTime.class)),args);
    }

    public void saveAttempt(int rental, String kind, String ref, String merchant, UUID request, String invoice,
                BigDecimal amount, LocalDateTime now, LocalDateTime expiry, List<PaymentFee> fees) {
        db.update("""
            INSERT INTO giao_dich_thanh_toan(MaDonThue,LoaiThanhToan,txn_ref,merchant,currency,request_key,
              invoice_key,SoTien,created_at,expires_at,TrangThaiGD,PhuongThucThanhToan)
            VALUES(?,?,?,?,'VND',?,?,?,?,?,'PENDING','VNPAY')
            """,rental,kind,ref,merchant,request.toString(),invoice,amount,now,expiry);
        for (PaymentFee fee : fees) db.update("""
            INSERT INTO payment_fee_snapshot(txn_ref,fee_id,amount,fee_type,reason) VALUES(?,?,?,?,?)
            """,ref,fee.id(),fee.amount(),fee.type(),fee.reason());
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

    public List<PaymentFee> findAttemptFees(String ref) {
        return db.query("""
            SELECT fee_id,amount,fee_type,reason FROM payment_fee_snapshot WHERE txn_ref=? ORDER BY fee_id
            """,(r,i)->new PaymentFee(r.getInt(1),r.getBigDecimal(2),r.getString(3),r.getString(4),"UNPAID"),ref);
    }

    public boolean isRentalPaid(int rental) {
        return db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM payment_allocation WHERE obligation=?) OR EXISTS(
            SELECT 1 FROM giao_dich_thanh_toan WHERE MaDonThue=? AND LoaiThanhToan IN ('RENTAL','TIỀN THUÊ')
            AND TrangThaiGD IN ('SUCCESS','THÀNH CÔNG'))
            """,Boolean.class,"RENTAL:"+rental,rental);
    }

    public boolean isFeeAllocated(int id) {
        return db.queryForObject("SELECT EXISTS(SELECT 1 FROM payment_allocation WHERE obligation=?)",Boolean.class,"FEE:"+id);
    }

    public boolean hasLegacyReceipt(String providerId) {
        return db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM giao_dich_thanh_toan WHERE txn_ref IS NULL
              AND PhuongThucThanhToan='VNPAY' AND MaGiaoDichVNPAYhoacSEPay=?
              AND TrangThaiGD IN ('SUCCESS','THÀNH CÔNG','NEEDS_RECONCILIATION','CẦN ĐỐI SOÁT'))
            """,Boolean.class,providerId);
    }

    public void saveAllocation(String obligation, PaymentAttempt a) {
        db.update("INSERT INTO payment_allocation(obligation,transaction_id) VALUES(?,?)",obligation,a.id());
    }

    public void markFeePaid(PaymentAttempt a, PaymentFee fee) {
        saveAllocation("FEE:"+fee.id(),a);
        if (db.update("""
            UPDATE phi_phat_sinh SET TrangThaiThanhToan='PAID'
            WHERE MaPhi=? AND MaDonThue=? AND TrangThaiThanhToan='UNPAID'
            """,fee.id(),a.rental()) != 1) throw new IllegalStateException("Khoản phí đã thay đổi.");
    }

    public String recordIpn(VnPayEvent e, LocalDateTime now) {
        db.update("""
            INSERT INTO payment_ipn(signature,merchant,ref,provider_id,received_amount,currency,provider_date,
              response_code,transaction_status,received_at,disposition) VALUES(?,?,?,?,?,?,?,?,?,?,'RECEIVED')
            ON DUPLICATE KEY UPDATE signature=signature
            """,e.signature(),e.merchant(),e.ref(),e.providerId(),e.amount(),e.currency(),e.date(),e.response(),e.status(),now);
        return db.queryForObject("SELECT disposition FROM payment_ipn WHERE signature=?",String.class,e.signature());
    }

    public String claimReceipt(VnPayEvent e) {
        db.update("""
            INSERT INTO payment_receipt(method,merchant,provider_id,signature) VALUES('VNPAY',?,?,?)
            ON DUPLICATE KEY UPDATE method=method
            """,e.merchant(),e.providerId(),e.signature());
        return db.queryForObject("""
            SELECT signature FROM payment_receipt WHERE method='VNPAY' AND merchant=? AND provider_id=?
            """,String.class,e.merchant(),e.providerId());
    }

    public void saveIpnResult(VnPayEvent e, String reason) {
        db.update("UPDATE payment_ipn SET disposition=? WHERE signature=?",reason,e.signature());
    }

    public boolean isSamePaidReceipt(VnPayEvent e) {
        return db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM payment_receipt p JOIN payment_ipn i ON i.signature=p.signature
            WHERE p.method='VNPAY' AND p.merchant=? AND p.provider_id=? AND i.ref=? AND i.received_amount=?
              AND i.currency=? AND i.disposition='ALLOCATED')
            """,Boolean.class,e.merchant(),e.providerId(),e.ref(),e.amount(),e.currency());
    }

    public void updateAttemptStatus(PaymentAttempt a, VnPayEvent e, String status, LocalDateTime now) {
        db.update("""
            UPDATE giao_dich_thanh_toan SET TrangThaiGD=?,MaGiaoDichVNPAYhoacSEPay=?,ThoiGianGiaoDich=?
            WHERE MaGD=? AND TrangThaiGD <> 'SUCCESS'
            """,status,e.providerId(),now,a.id());
    }

    public boolean hasOpenMaintenance(int vehicle) {
        return db.queryForObject("SELECT EXISTS(SELECT 1 FROM bao_tri_xe WHERE MaXe=? AND NgayHoanTat IS NULL)",
            Boolean.class,vehicle);
    }

    public boolean hasRentedOrder(int vehicle) {
        return db.queryForObject("SELECT EXISTS(SELECT 1 FROM don_thue_xe WHERE MaXe=? AND TrangThaiDon='RENTED')",
            Boolean.class,vehicle);
    }

    public void sendPaymentNotifications(int account, PaymentAttempt a, LocalDateTime now) {
        String message = "Đơn #" + a.rental() + " · " + a.amount().toPlainString() + " VND · " + a.ref();
        db.update("INSERT INTO thong_bao(MaTKNhan,TieuDe,NoiDung,ThoiGianGui) VALUES(?,?,?,?)",
            account,"Đã xác nhận thanh toán",message,now);
        if ("EXTRA_FEE".equals(a.kind())) db.update("""
            INSERT INTO thong_bao(MaTKNhan,TieuDe,NoiDung,ThoiGianGui)
            SELECT n.MaTK,'Đã thu phí trả xe',?,? FROM bien_ban_tra_xe b JOIN nhan_vien n ON n.MaNV=b.MaNVNhan
            WHERE b.MaDonThue=?
            """,message,now,a.rental());
    }

    public List<Map<String,Object>> findPaymentsForReview() {
        return db.queryForList("""
            SELECT i.event_id,i.ref,i.merchant,i.provider_id,i.received_amount,i.currency,i.received_at,i.disposition,
              g.MaDonThue AS rental,g.SoTien AS expected,
              (SELECT n.note FROM payment_review_note n WHERE n.event_id=i.event_id ORDER BY note_id DESC LIMIT 1) AS resolution
            FROM payment_ipn i LEFT JOIN giao_dich_thanh_toan g ON g.txn_ref=i.ref
            WHERE i.disposition LIKE 'REVIEW%' ORDER BY received_at DESC LIMIT 200
            """);
    }

    public boolean addReviewNote(long event, String email, String note, LocalDateTime now) {
        return db.update("""
            INSERT INTO payment_review_note(event_id,actor_id,recorded_at,note)
            SELECT i.event_id,t.MaTK,?,? FROM payment_ipn i JOIN tai_khoan t ON t.Email=?
            WHERE i.event_id=? AND i.disposition LIKE 'REVIEW%' AND t.VaiTro='ADMIN' AND t.TrangThai='ACTIVE'
            """,now,note,email,event) == 1;
    }
}
