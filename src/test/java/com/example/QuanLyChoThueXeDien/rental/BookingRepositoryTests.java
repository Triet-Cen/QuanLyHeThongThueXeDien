package com.example.QuanLyChoThueXeDien.rental;

import com.example.QuanLyChoThueXeDien.rental.repositories.*;
import com.example.QuanLyChoThueXeDien.vehicle.repositories.VehicleRepository;
import com.example.QuanLyChoThueXeDien.pricing.repositories.RentalPriceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Validates mappings and JPQL without opening a datasource; does not prove DB lock behavior. */
class BookingRepositoryTests {
    @Test void validateMappingsAndAllBookingQueriesWithoutDatabase() {
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setPackagesToScan("com.example.QuanLyChoThueXeDien");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","none",
            "hibernate.boot.allow_jdbc_metadata_access","false",
            "hibernate.dialect","org.hibernate.dialect.MySQLDialect"));
        try {
            factory.afterPropertiesSet();
            try (var em = factory.getObject().createEntityManager()) {
                var repositories = new JpaRepositoryFactory(em);
                assertNotNull(repositories.getRepository(RentalRepository.class));
                assertNotNull(repositories.getRepository(CouponRepository.class));
                assertNotNull(repositories.getRepository(VehicleRepository.class));
                assertNotNull(repositories.getRepository(RentalPriceRepository.class));
            }
        } finally { factory.destroy(); }
    }
}
