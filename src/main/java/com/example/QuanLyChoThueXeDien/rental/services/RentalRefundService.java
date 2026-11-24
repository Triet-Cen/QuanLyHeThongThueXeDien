package com.example.QuanLyChoThueXeDien.rental.services;

import com.example.QuanLyChoThueXeDien.rental.repositories.AdminRentalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RentalRefundService {
    private final AdminRentalRepository rentals;
    private final JdbcTemplate db;
    private final RentalClock clock;

    private static final String ELIGIBLE = """
        SELECT g.MaGD,a.MaTK,LEAST(g.SoTien,d.TongTienThue) amount FROM don_thue_xe d
        JOIN khach_hang k ON k.MaKH=d.MaKH JOIN tai_khoan a ON a.MaTK=k.MaTK
        JOIN payment_allocation p ON p.obligation=CONCAT('RENTAL:',d.MaDonThue)
        JOIN giao_dich_thanh_toan g ON g.MaGD=p.transaction_id AND g.MaDonThue=d.MaDonThue
        WHERE d.MaDonThue=? AND a.Email=? AND a.VaiTro='CUSTOMER' AND a.TrangThai='ACTIVE'
        AND d.TrangThaiDon='CANCELLED' AND g.TrangThaiGD='SUCCESS' AND g.PhuongThucThanhToan='VNPAY'
        AND g.LoaiThanhToan='RENTAL' AND g.SoTien>0 AND d.TongTienThue>0 AND EXISTS (
          SELECT 1 FROM lich_su_trang_thai_don h WHERE h.MaDonThue=d.MaDonThue
          AND h.MaTKThucHien=a.MaTK AND h.TrangThaiCu='BOOKED' AND h.TrangThaiMoi='CANCELLED'
          AND h.GhiChu='BR39' AND h.ThoiGianThayDoi<d.ThoiGianNhanDuKien)
        """;

    private boolean hasRequestSchema() {
        return Boolean.TRUE.equals(db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM information_schema.columns
                WHERE table_schema=DATABASE() AND table_name='hoan_tien' AND column_name='account_number')
            """,Boolean.class));
    }

    public java.util.Map<String,Object> customerView(String email,int id) {
        boolean ready = hasRequestSchema();
        String maskedAccount = ready ? "CONCAT('•••• ',RIGHT(h.account_number,4))" : "NULL";
        var existing=db.queryForList("""
            SELECT h.TrangThaiHoanTien status,h.SoTienHoan amount,h.NgayYeuCau sentAt,
                %s maskedAccount
            FROM hoan_tien h JOIN don_thue_xe d ON d.MaDonThue=h.MaDonThue
            JOIN khach_hang k ON k.MaKH=d.MaKH JOIN tai_khoan a ON a.MaTK=k.MaTK
            WHERE h.MaDonThue=? AND a.Email=? ORDER BY h.MaHoanTien DESC LIMIT 1
            """.formatted(maskedAccount),id,email);
        if(!existing.isEmpty()) return existing.get(0);
        var eligible = db.queryForList(ELIGIBLE,id,email);
        if (eligible.isEmpty()) return java.util.Map.of();
        eligible.get(0).put("requestAvailable", ready);
        return eligible.get(0);
    }

    @Transactional
    public void request(String email,int id,String bank,String number,String holder,String note) {
        if (!hasRequestSchema()) throw new IllegalArgumentException("Chức năng gửi yêu cầu hoàn tiền chưa sẵn sàng.");
        var source=db.queryForList(ELIGIBLE+" FOR UPDATE",id,email);
        if(source.isEmpty()) throw new IllegalArgumentException("Đơn không đủ điều kiện gửi yêu cầu hoàn.");
        if(!db.queryForList("SELECT MaHoanTien FROM hoan_tien WHERE MaDonThue=? OR MaGDGoc=? FOR UPDATE",
            id,source.get(0).get("MaGD")).isEmpty()) return;
        if(bank.isBlank() || bank.length()>100 || !number.matches("[0-9]{1,34}") || holder.isBlank()
            || holder.length()>120 || note.isBlank() || note.length()>1000)
            throw new IllegalArgumentException("Vui lòng kiểm tra thông tin ngân hàng và lời nhắn.");
        var row=source.get(0);
        db.update("""
            INSERT INTO hoan_tien(MaDonThue,MaGDGoc,SoTienHoan,LyDoHoan,NgayYeuCau,TrangThaiHoanTien,
                customer_request_order,requested_by,bank_name,account_number,account_holder,customer_note)
            VALUES(?,?,?,'BR39',?,'PENDING',?,?,?,?,?,?)
            """,id,row.get("MaGD"),row.get("amount"),clock.now(),id,row.get("MaTK"),bank.strip(),number,holder.strip(),note.strip());
        db.update("""
            INSERT INTO thong_bao(MaTKNhan,TieuDe,NoiDung,ThoiGianGui)
            SELECT MaTK,'Yêu cầu hoàn tiền mới',?,? FROM tai_khoan WHERE VaiTro='ADMIN' AND TrangThai='ACTIVE'
            ""","Khách gửi yêu cầu hoàn cho đơn DT"+id,clock.now());
    }

    @Transactional
    public void process(String email,int rental,int refund,boolean confirm,String reference,String evidence,java.time.LocalDateTime paidAt) {
        rentals.requireAdmin(email);
        var state=db.queryForList("""
            SELECT h.TrangThaiHoanTien FROM don_thue_xe d JOIN hoan_tien h ON h.MaDonThue=d.MaDonThue
            JOIN giao_dich_thanh_toan g ON g.MaGD=h.MaGDGoc AND g.MaDonThue=d.MaDonThue
            JOIN payment_allocation a ON a.transaction_id=g.MaGD AND a.obligation=CONCAT('RENTAL:',d.MaDonThue)
            WHERE d.MaDonThue=? AND h.MaHoanTien=? AND d.TrangThaiDon='CANCELLED'
            AND g.PhuongThucThanhToan='VNPAY' AND g.TrangThaiGD='SUCCESS'
            AND h.LyDoHoan='BR39'
            AND h.SoTienHoan>0 AND (SELECT SUM(s.SoTienHoan) FROM hoan_tien s WHERE s.MaGDGoc=g.MaGD)<=g.SoTien
            FOR UPDATE
            """,String.class,rental,refund);
        if(state.isEmpty()) throw new IllegalArgumentException("Hồ sơ cần đối soát nguồn và quyền hoàn.");
        String next=confirm?"COMPLETED":"PROCESSING";
        if(next.equals(state.get(0))) return;
        if(!(confirm?"PROCESSING":"PENDING").equals(state.get(0)))
            throw new IllegalArgumentException("Trạng thái hồ sơ đã thay đổi.");
        if(confirm) {
            if(reference==null || reference.isBlank() || reference.length()>100 || evidence==null || evidence.isBlank()
                || evidence.length()>500 || paidAt==null || paidAt.isAfter(clock.now()))
                throw new IllegalArgumentException("Cần mã giao dịch, chứng từ và thời điểm hoàn hợp lệ.");
            try {
                int changed=db.update("""
                    UPDATE hoan_tien SET TrangThaiHoanTien='COMPLETED',NgayHoanTat=?,transfer_reference=?,
                    transfer_evidence=?,recorded_by=(SELECT MaTK FROM tai_khoan WHERE Email=?)
                    WHERE MaHoanTien=? AND NgayYeuCau<=?
                    """,paidAt,reference.strip(),evidence.strip(),email,refund,paidAt);
                if(changed!=1) throw new IllegalArgumentException("Thời điểm hoàn phải sau khi gửi yêu cầu.");
            } catch(org.springframework.dao.DuplicateKeyException duplicate) {
                throw new IllegalArgumentException("Mã giao dịch hoàn đã được ghi nhận.");
            }
        } else db.update("UPDATE hoan_tien SET TrangThaiHoanTien='PROCESSING' WHERE MaHoanTien=?",refund);
        String note="Hồ sơ hoàn #"+refund+(confirm?" đã được Admin ghi nhận hoàn thủ công.":" đã được tiếp nhận.");
        db.update("""
            INSERT INTO lich_su_trang_thai_don(MaDonThue,TrangThaiCu,TrangThaiMoi,ThoiGianThayDoi,MaTKThucHien,GhiChu)
            SELECT ?,'CANCELLED','CANCELLED',?,MaTK,? FROM tai_khoan WHERE Email=?
            """,rental,clock.now(),note,email);
        db.update("""
            INSERT INTO thong_bao(MaTKNhan,TieuDe,NoiDung,ThoiGianGui)
            SELECT k.MaTK,'Cập nhật hoàn tiền',?,? FROM don_thue_xe d JOIN khach_hang k ON k.MaKH=d.MaKH WHERE d.MaDonThue=?
            """,note,clock.now(),rental);
    }
}
