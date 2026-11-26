import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.io.*;

/** Local maintenance tool. Reads credentials from .env without printing them. */
class PaymentSchemaMaintenance {
    static final List<String> EXTRA = List.of("txn_ref","request_key","invoice_key","merchant","currency","created_at","expires_at","PhuongThucThanhToan");
    static final List<String> TABLES = List.of("payment_receipt","payment_review_note","payment_fee_snapshot","payment_allocation","payment_ipn");
    static String id(String name) { return "`"+name.replace("`","``")+"`"; }
    static boolean table(Connection c,String name) throws Exception {
        try(var s=c.prepareStatement("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?")) {
            s.setString(1,name); try(var r=s.executeQuery()){r.next();return r.getInt(1)>0;}
        }
    }
    static List<String> columns(Connection c) throws Exception {
        var names=new ArrayList<String>();
        try(var s=c.createStatement();var r=s.executeQuery("SHOW COLUMNS FROM giao_dich_thanh_toan")){while(r.next())names.add(r.getString(1));}return names;
    }
    static void run(Connection c,String sql) throws Exception {try(var s=c.createStatement()){s.execute(sql);}}
    static void inspect(Connection c) throws Exception {
        System.out.println("Payment columns: "+columns(c));
        for(String name:TABLES) if(table(c,name))try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM "+id(name))){r.next();System.out.println(name+": "+r.getLong(1)+" rows");}
        try(var s=c.createStatement();var r=s.executeQuery("SELECT TrangThaiGD,COUNT(*) FROM giao_dich_thanh_toan GROUP BY TrangThaiGD")){while(r.next())System.out.println("Payment status "+r.getString(1)+": "+r.getLong(2));}
    }
    static String definition(Connection c,String name) throws Exception {try(var s=c.createStatement();var r=s.executeQuery("SHOW CREATE TABLE "+id(name))){r.next();return r.getString(2);}}
    static void backup(Connection c,Path path) throws Exception {
        Files.createDirectories(path.toAbsolutePath().getParent());
        var tables=new ArrayList<String>();var views=new ArrayList<String>();
        try(var s=c.createStatement();var r=s.executeQuery("SHOW FULL TABLES")){while(r.next())("VIEW".equals(r.getString(2))?views:tables).add(r.getString(1));}
        c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); c.setAutoCommit(false);
        try(var out=Files.newBufferedWriter(path,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            out.write("-- Full database backup before restoring the original payment schema.\nSET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS=0;\n");
            for(String name:tables){
                out.write(definition(c,name)+";\n");
                try(var s=c.createStatement();var r=s.executeQuery("SELECT * FROM "+id(name))){
                    var m=r.getMetaData();var names=new ArrayList<String>();for(int i=1;i<=m.getColumnCount();i++)names.add(id(m.getColumnName(i)));
                    while(r.next()){
                        var values=new ArrayList<String>();
                        for(int i=1;i<=m.getColumnCount();i++){
                            String v=r.getString(i);
                            if(v==null)values.add("NULL");
                            else if(r.getObject(i) instanceof Boolean b)values.add(b?"1":"0");
                            else if(Set.of(Types.BINARY,Types.VARBINARY,Types.LONGVARBINARY,Types.BLOB,Types.BIT).contains(m.getColumnType(i)))values.add("X'"+HexFormat.of().formatHex(r.getBytes(i))+"'");
                            else values.add("CONVERT(X'"+HexFormat.of().formatHex(v.getBytes(StandardCharsets.UTF_8))+"' USING utf8mb4)");
                        }
                        out.write("INSERT INTO "+id(name)+" ("+String.join(",",names)+") VALUES ("+String.join(",",values)+");\n");
                    }
                }
            }
            for(String name:views)out.write(definition(c,name)+";\n");
            out.write("SET FOREIGN_KEY_CHECKS=1;\n");
        }finally{c.rollback();c.setAutoCommit(true);}
        System.out.println("Full backup saved: "+path.toAbsolutePath());
    }
    static void migrate(Connection c) throws Exception {
        var cols=columns(c);
        var originals=List.of("MaGD","MaDonThue","LoaiThanhToan","MaGiaoDichVNPAYhoacSEPay","SoTien","ThoiGianGiaoDich","TrangThaiGD");
        if(!cols.containsAll(originals))throw new IllegalStateException("Original columns are missing");
        for(String col:cols)if(!originals.contains(col)&&!EXTRA.contains(col))throw new IllegalStateException("Unexpected column: "+col);
        c.setAutoCommit(false);
        try{
            if(cols.contains("txn_ref"))run(c,"""
                INSERT INTO lich_su_trang_thai_don(MaDonThue,TrangThaiCu,TrangThaiMoi,ThoiGianThayDoi,GhiChu)
                SELECT g.MaDonThue,d.TrangThaiDon,d.TrangThaiDon,COALESCE(g.created_at,NOW()),
                  CONCAT('VNPAY LEGACY #',g.MaGD,' ref=',g.txn_ref)
                FROM giao_dich_thanh_toan g JOIN don_thue_xe d ON d.MaDonThue=g.MaDonThue
                WHERE g.txn_ref IS NOT NULL AND NOT EXISTS(SELECT 1 FROM lich_su_trang_thai_don h
                  WHERE h.MaDonThue=g.MaDonThue AND h.GhiChu=CONCAT('VNPAY LEGACY #',g.MaGD,' ref=',g.txn_ref))
                """);
            if(cols.contains("txn_ref")&&table(c,"payment_ipn"))run(c,"""
                INSERT INTO lich_su_trang_thai_don(MaDonThue,TrangThaiCu,TrangThaiMoi,ThoiGianThayDoi,GhiChu)
                SELECT g.MaDonThue,d.TrangThaiDon,d.TrangThaiDon,i.received_at,
                  LEFT(CONCAT('VNPAY REVIEW #',g.MaGD,' ',i.disposition,'; ref=',i.ref,'; provider=',COALESCE(i.provider_id,''),
                    '; received=',i.received_amount,' ',i.currency),1000)
                FROM payment_ipn i JOIN giao_dich_thanh_toan g ON g.txn_ref=i.ref
                  JOIN don_thue_xe d ON d.MaDonThue=g.MaDonThue WHERE i.disposition LIKE 'REVIEW%'
                """);
            if(cols.contains("txn_ref")&&table(c,"payment_review_note")&&table(c,"payment_ipn"))run(c,"""
                INSERT INTO lich_su_trang_thai_don(MaDonThue,TrangThaiCu,TrangThaiMoi,ThoiGianThayDoi,MaTKThucHien,GhiChu)
                SELECT g.MaDonThue,d.TrangThaiDon,d.TrangThaiDon,n.recorded_at,n.actor_id,
                  LEFT(CONCAT('VNPAY NOTE #',g.MaGD,' ',n.note),1000)
                FROM payment_review_note n JOIN payment_ipn i ON i.event_id=n.event_id
                  JOIN giao_dich_thanh_toan g ON g.txn_ref=i.ref JOIN don_thue_xe d ON d.MaDonThue=g.MaDonThue
                """);
            c.commit();
        }catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
        run(c,"DROP VIEW IF EXISTS payment_allocated_cashflow");
        for(String name:TABLES)run(c,"DROP TABLE IF EXISTS "+id(name));
        var indexes=new HashSet<String>();
        try(var statement=c.createStatement();var result=statement.executeQuery("SHOW INDEX FROM giao_dich_thanh_toan")) {
            while(result.next())indexes.add(result.getString("Key_name"));
        }
        if(indexes.contains("payment_request"))run(c,"ALTER TABLE giao_dich_thanh_toan DROP INDEX payment_request");
        for(String col:EXTRA)if(cols.contains(col))run(c,"ALTER TABLE giao_dich_thanh_toan DROP COLUMN "+id(col));
        if(!columns(c).equals(originals))throw new IllegalStateException("Final schema is not the expected seven columns");
        System.out.println("Verified: exactly seven original payment columns; five auxiliary tables removed.");
    }
    public static void main(String[] args) throws Exception {
        var env=new Properties();try(var reader=Files.newBufferedReader(Path.of(".env"))){env.load(reader);}
        String url=env.getProperty("DB_URL");
        try(var c=DriverManager.getConnection(url,env.getProperty("DB_USERNAME"),env.getProperty("DB_PASSWORD"))){
            switch(args[0]){
                case "inspect" -> inspect(c);
                case "drop-test-schema" -> {
                    if(!args[1].equals("booking_test_vnpay_seven_20261126"))throw new IllegalArgumentException("Only this task's isolated test schema may be removed");
                    if(c.getCatalog().equals(args[1]))throw new IllegalStateException("Refusing to drop the configured application database");
                    run(c,"DROP DATABASE "+id(args[1]));
                    System.out.println("Removed the temporary integration-test database.");
                }
                case "backup" -> backup(c,Path.of(args[1]));
                case "verify-backup" -> {
                    String name="booking_test_vnpay_restore_20261126";
                    run(c,"CREATE DATABASE "+id(name)+" CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                    try {
                        c.setCatalog(name);
                        var statement=new StringBuilder();
                        for(String line:Files.readAllLines(Path.of(args[1]),StandardCharsets.UTF_8)) {
                            if(line.startsWith("--"))continue;
                            statement.append(line).append('\n');
                            if(line.endsWith(";")){run(c,statement.toString());statement.setLength(0);}
                        }
                        System.out.println("Full backup restored successfully in isolated database.");
                        migrate(c);inspect(c);
                    } finally {run(c,"DROP DATABASE "+id(name));}
                }
                case "apply" -> {backup(c,Path.of(args[1]));migrate(c);inspect(c);}
                case "prepare-test-refunds" -> {
                    if(!args[1].matches("booking_test_[a-z0-9_]+"))throw new IllegalArgumentException("Invalid test schema");
                    c.setCatalog(args[1]);
                    var command=new StringBuilder();
                    for(String line:Files.readAllLines(Path.of("src/main/resources/db/manual/Customer_refund_request.sql"))) {
                        if(line.startsWith("--"))continue;
                        command.append(line).append('\n');
                    }
                    run(c,command.toString());
                    migrate(c);
                    System.out.println("Optional refund metadata installed only in isolated test database.");
                }
                case "test-schema" -> {
                    String name=args[1];if(!name.matches("booking_test_[a-z0-9_]+"))throw new IllegalArgumentException("Invalid isolated test schema");
                    run(c,"CREATE DATABASE "+id(name)+" CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                    String original=c.getCatalog();var definitions=new ArrayList<String>();
                    try(var s=c.createStatement();var r=s.executeQuery("SHOW FULL TABLES WHERE Table_type='BASE TABLE'")){while(r.next())definitions.add(definition(c,r.getString(1)));}
                    c.setCatalog(name);run(c,"SET FOREIGN_KEY_CHECKS=0");
                    for(String ddl:definitions)run(c,ddl);
                    run(c,"SET FOREIGN_KEY_CHECKS=1");migrate(c);
                    System.out.println("Isolated test schema ready: "+name);
                }
                default -> throw new IllegalArgumentException("Use inspect, apply <backup.sql>, or test-schema <booking_test_name>");
            }
        }
    }
}