package com.example.QuanLyChoThueXeDien.license;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DrivingLicenseSchemaIT {
    @Autowired DataSource dataSource;
    @Test void licenseAndStaffSchemaSupportsFeature() throws Exception {
        try (var connection = dataSource.getConnection()) {
            for (String table : new String[]{"giay_phep_lai_xe", "nhan_vien"}) {
                try (var query = connection.prepareStatement(
                        "select count(*) from information_schema.tables where table_schema=database() and lower(table_name)=?")) {
                    query.setString(1,table);
                    try(var result=query.executeQuery()) { result.next(); assertEquals(1,result.getInt(1), "Missing table: "+table); }
                }
            }
            try (var query = connection.prepareStatement("select MaGPLX, MaKH, SoGPLX, HangGPLX, NgayCap, NgayHetHan, AnhGPLX, TrangThaiXacMinh, NgayXacMinh, MaNVXacMinh from giay_phep_lai_xe where 1=0")) {
                query.executeQuery().close();
            }
        }
    }
}
