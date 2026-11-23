package com.example.QuanLyChoThueXeDien.rental;

import com.example.QuanLyChoThueXeDien.payment.repositories.PaymentRepository;
import com.example.QuanLyChoThueXeDien.payment.services.PaymentService;
import com.example.QuanLyChoThueXeDien.payment.services.VnPayGateway;
import com.example.QuanLyChoThueXeDien.auth.entities.*;
import com.example.QuanLyChoThueXeDien.category.entities.VehicleCategory;
import com.example.QuanLyChoThueXeDien.VehicleModel.entities.VehicleModel;
import com.example.QuanLyChoThueXeDien.station.entities.Station;
import com.example.QuanLyChoThueXeDien.vehicle.entities.Vehicle;
import com.example.QuanLyChoThueXeDien.license.entities.DrivingLicense;
import com.example.QuanLyChoThueXeDien.pricing.entities.RentalPrice;
import com.example.QuanLyChoThueXeDien.pricing.services.RentalQuoteService;
import com.example.QuanLyChoThueXeDien.rental.entities.*;
import com.example.QuanLyChoThueXeDien.rental.dtos.*;
import com.example.QuanLyChoThueXeDien.rental.services.*;
import com.example.QuanLyChoThueXeDien.rental.repositories.RentalRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Explicit opt-in: BOOKING_TEST_DB_URL, BOOKING_TEST_DB_USER, BOOKING_TEST_DB_PASSWORD.
 * Requires a separately provisioned booking_test_* MySQL schema with the application's
 * existing schema. Booking_pending_schema.sql only provides optional precision/index tuning.
 * Never reads the production datasource.
 * Run explicitly with Maven -Dtest=BookingDatabaseIT test after approval for the test DB.
 */
@EnabledIfEnvironmentVariable(named = "BOOKING_TEST_DB_URL", matches = "jdbc:mysql://.+")
@SpringJUnitConfig(BookingDatabaseIT.Config.class)
@TestPropertySource(properties = {"VNPAY_TMN_CODE=TEST","VNPAY_HASH_SECRET=fixture-only",
    "VNPAY_URL=https://gateway.invalid/pay","VNPAY_RETURN_URL=https://merchant.invalid/return",
    "VNPAY_IPN_URL=https://merchant.invalid/ipn","VNPAY_FEE_ATTEMPT_MINUTES=30"})
class BookingDatabaseIT {
    @Configuration @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = "com.example.QuanLyChoThueXeDien")
    @Import({BookingService.class, RentalQuoteService.class, PaymentRepository.class, PaymentService.class, VnPayGateway.class})
    static class Config {
        @Bean DataSource dataSource() throws Exception {
            var source = new DriverManagerDataSource(System.getenv("BOOKING_TEST_DB_URL"),
                System.getenv("BOOKING_TEST_DB_USER"), System.getenv("BOOKING_TEST_DB_PASSWORD"));
            try (var connection = source.getConnection()) {
                if (connection.getCatalog() == null || !connection.getCatalog().startsWith("booking_test_"))
                    throw new IllegalStateException("Integration tests require a separate booking_test_* database.");
            }
            return source;
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source); factory.setPackagesToScan("com.example.QuanLyChoThueXeDien");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none")); return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean JdbcTemplate paymentJdbc(DataSource source) { return new JdbcTemplate(source); }
        @Bean AtomicBoolean failNotification() { return new AtomicBoolean(); }
        @Bean @Primary EntityManager entityManager(EntityManagerFactory factory, AtomicBoolean failNotification) {
            EntityManager delegate = SharedEntityManagerCreator.createSharedEntityManager(factory);
            return (EntityManager) java.lang.reflect.Proxy.newProxyInstance(EntityManager.class.getClassLoader(),
                new Class<?>[]{EntityManager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("persist") && args[0] instanceof BookingNotification && failNotification.getAndSet(false))
                        throw new IllegalStateException("Injected notification failure inside the real DB transaction");
                    if (method.getName().equals("persist") && args[0] instanceof RentalHistory h &&
                        "BOOKED".equals(ReflectionTestUtils.getField(h,"newStatus")) && failNotification.getAndSet(false))
                        throw new IllegalStateException("Injected history failure inside the payment transaction");
                    try { return method.invoke(delegate, args); }
                    catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                });
        }
        @Bean TestClock testClock() { return new TestClock(); }
        @Bean RentalClock rentalClock(TestClock clock) { return new RentalClock(clock); }
    }
    static class TestClock extends Clock {
        final AtomicReference<Instant> instant = new AtomicReference<>(Instant.parse("2030-01-01T01:00:00Z"));
        public ZoneId getZone() { return RentalClock.ZONE; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return instant.get(); }
    }
    @Autowired BookingService service;
    @Autowired RentalRepository rentals;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired TestClock clock;
    @Autowired AtomicBoolean failNotification;
    Fixture fixture;
    TransactionTemplate tx;
    LocalDateTime start = LocalDateTime.of(2030,1,2,8,0);

    record Fixture(String a, String b, int customerA, int customerB, int accountA, int accountB,
        int x, int y, int model, int station, int category, String coupon) {}

    @BeforeEach void setup() {
        clock.instant.set(Instant.parse("2030-01-01T01:00:00Z")); failNotification.set(false);
        tx = new TransactionTemplate(manager);
        fixture = tx.execute(status -> {
            String suffix = UUID.randomUUID().toString();
            Account a = account("a-" + suffix + "@example.invalid"); Account b = account("b-" + suffix + "@example.invalid");
            CustomerProfile ca = customer(a); CustomerProfile cb = customer(b);
            VehicleCategory category = new VehicleCategory("Test EV", "Test only"); em.persist(category);
            VehicleModel model = new VehicleModel(); model.updateDetails("Test model", "Test", 5, null, category); em.persist(model);
            Station station = new Station("Test station", "Test address", "0900000000", "ACTIVE"); em.persist(station);
            Vehicle x = vehicle(model, station, "X-" + suffix); Vehicle y = vehicle(model, station, "Y-" + suffix);
            em.persist(new RentalPrice(model, new BigDecimal("1200000"), LocalDate.of(2029,1,1), null));
            String code = "TEST-" + suffix;
            em.createNativeQuery("""
                INSERT INTO ma_giam_gia
                    (MaGiamGia,TenChuongTrinh,PhanTramGiam,NgayBatDau,NgayKetThuc,SoLuongConLai,TrangThai)
                VALUES (:code,'Integration test',10,:starts,:ends,1,'ACTIVE')
                """).setParameter("code",code).setParameter("starts",start.minusDays(2))
                .setParameter("ends",start.plusYears(1)).executeUpdate();
            em.flush();
            return new Fixture(a.getEmail(),b.getEmail(),ca.getCustomerId(),cb.getCustomerId(),a.getAccountId(),b.getAccountId(),
                x.getVehicleId(),y.getVehicleId(),model.getModelId(),station.getStationId(),category.getCategoryId(),code);
        });
    }
    Account account(String email) {
        Account a = new Account(); a.setEmail(email); a.setPassword("isolated-test-value"); a.setRole("CUSTOMER"); a.setStatus("ACTIVE"); em.persist(a); return a;
    }
    CustomerProfile customer(Account account) {
        CustomerProfile c = new CustomerProfile(); c.setAccount(account); c.setFullName("Integration test"); c.setPhone("0900000000"); em.persist(c);
        DrivingLicense l = new DrivingLicense(); l.setCustomer(c); l.setLicenseNumber(UUID.randomUUID().toString());
        l.setLicenseClass("B"); l.setIssueDate(LocalDate.of(2020,1,1)); l.setExpiryDate(LocalDate.of(2035,1,1));
        l.setVerificationStatus(DrivingLicense.VERIFIED); l.setImagePath("test-only.jpg"); em.persist(l); return c;
    }
    Vehicle vehicle(VehicleModel model, Station station, String plate) {
        Vehicle v = new Vehicle(); v.updateDetails(model,station,plate,"Test",BigDecimal.valueOf(90)); em.persist(v); return v;
    }
    BookingForm form(int vehicle, LocalDateTime begin) {
        BookingForm f = new BookingForm(); f.setVehicleId(vehicle); f.setStationId(fixture.station());
        f.setStartAt(begin); f.setEndAt(begin.plusDays(1)); return f;
    }

    @Test void adjacentScheduleCanBookWhileFirstPendingIsStillHeld() {
        BookingForm a = form(fixture.x(),start); int first = service.create(fixture.a(),service.quote(fixture.a(),a));
        BookingForm b = form(fixture.x(),a.getEndAt()); int second = service.create(fixture.b(),service.quote(fixture.b(),b));
        assertNotEquals(first,second); assertTrue(service.view(fixture.a(),first).isPending());
        assertEquals(2, count("select count(r) from Rental r where r.vehicle.vehicleId = " + fixture.x()));
    }
    @Test void competingTransactionsForSameScheduleCommitExactlyOneOrder() throws Exception {
        BookingForm f = form(fixture.x(),start);
        BookingQuote a = service.quote(fixture.a(),f); BookingQuote b = service.quote(fixture.b(),f);
        List<Object> results = race(() -> service.create(fixture.a(),a), () -> service.create(fixture.b(),b));
        assertEquals(1,results.stream().filter(Integer.class::isInstance).count());
        assertEquals(1,count("select count(r) from Rental r where r.vehicle.vehicleId = " + fixture.x()));
    }
    @Test void duplicateServiceCallsCannotCreateTwoOrdersForSameSchedule() throws Exception {
        BookingQuote quote = service.quote(fixture.a(),form(fixture.x(),start));
        List<Object> results = race(() -> service.create(fixture.a(),quote), () -> service.create(fixture.a(),quote));
        assertEquals(1,results.stream().filter(Integer.class::isInstance).count());
        assertEquals(1,count("select count(r) from Rental r where r.vehicle.vehicleId = " + fixture.x()));
        assertEquals(1,count("select count(b) from BookingNotification b where b.accountId = " + fixture.accountA()));
    }
    @Test void competingCouponReservationsDoNotConsumePublishedQuantity() throws Exception {
        BookingForm a = form(fixture.x(),start); a.setCouponCode(fixture.coupon());
        BookingForm b = form(fixture.y(),start); b.setCouponCode(fixture.coupon());
        BookingQuote qa = service.quote(fixture.a(),a); BookingQuote qb = service.quote(fixture.b(),b);
        List<Object> results = race(() -> service.create(fixture.a(),qa), () -> service.create(fixture.b(),qb));
        assertEquals(1,results.stream().filter(Integer.class::isInstance).count());
        assertEquals(1,tx.execute(s -> em.find(Coupon.class,fixture.coupon()).getRemainingQuantity()).intValue());
    }
    @Test void timeoutAndCancellationReleaseOnlyTheirOwnHold() {
        BookingForm a = form(fixture.x(),start); a.setCouponCode(fixture.coupon());
        int first = service.create(fixture.a(),service.quote(fixture.a(),a));
        clock.instant.updateAndGet(i -> i.plusSeconds(900));
        BookingForm b = form(fixture.x(),start); b.setCouponCode(fixture.coupon());
        int second = service.create(fixture.b(),service.quote(fixture.b(),b));
        service.expire(first); service.expire(first);
        assertEquals("CANCELLED",service.view(fixture.a(),first).getStatus());
        assertTrue(service.view(fixture.b(),second).isPending()); service.cancel(fixture.b(),second);
        assertEquals(1,tx.execute(s -> em.find(Coupon.class,fixture.coupon()).getRemainingQuantity()).intValue());
        assertEquals(0,tx.execute(s -> rentals.countCouponHolds(fixture.coupon(),start.minusDays(1).plusMinutes(15))).longValue());
    }
    @Test void notificationFailureRollsBackRentalHistoryAndCouponReservation() {
        BookingForm form = form(fixture.x(),start); form.setCouponCode(fixture.coupon());
        BookingQuote quote = service.quote(fixture.a(),form); failNotification.set(true);
        assertThrows(IllegalStateException.class, () -> service.create(fixture.a(),quote));
        assertEquals(0,count("select count(r) from Rental r where r.vehicle.vehicleId = " + fixture.x()));
        assertEquals(0,count("select count(b) from BookingNotification b where b.accountId = " + fixture.accountA()));
        assertDoesNotThrow(() -> service.create(fixture.a(),quote));
    }
    long count(String jpql) { return tx.execute(s -> em.createQuery(jpql,Long.class).getSingleResult()); }

    // These cases additionally require db/manual/VNPay_payment_schema.sql on the isolated test schema.
    @Nested class PaymentRaces {
        @Autowired PaymentService payments;
        @Autowired JdbcTemplate db;
        int rentalId;
        String ref;
        Map<String,String[]> callback;

        @BeforeEach void createAttempt() throws Exception {
            var form = form(fixture.x(),start);
            form.setCouponCode(fixture.coupon());
            rentalId = service.create(fixture.a(),service.quote(fixture.a(),form));
            startAttempt(false);
        }

        void startAttempt(boolean fees) throws Exception {
            var invoice = payments.getInvoice(fixture.a(),rentalId,fees);
            var request = UUID.randomUUID();
            payments.startPayment(fixture.a(),rentalId,fees,invoice.key(),request,"127.0.0.1");
            ref = db.queryForObject("select txn_ref from giao_dich_thanh_toan where MaDonThue=? and request_key=?",
                String.class,rentalId,request.toString());
            String providerId = db.queryForObject("select MaGD from giao_dich_thanh_toan where txn_ref=?",String.class,ref);
            var fields = new TreeMap<String,String>();
            fields.put("vnp_Amount",invoice.amount().movePointRight(2).toBigIntegerExact().toString());
            fields.put("vnp_ResponseCode","00"); fields.put("vnp_TmnCode","TEST");
            fields.put("vnp_TransactionNo",providerId); fields.put("vnp_TransactionStatus","00"); fields.put("vnp_TxnRef",ref);
            var signed = new StringJoiner("&");
            callback = new HashMap<>();
            fields.forEach((k,v) -> { signed.add(k+"="+v); callback.put(k,new String[]{v}); });
            var mac = javax.crypto.Mac.getInstance("HmacSHA512");
            mac.init(new javax.crypto.spec.SecretKeySpec("fixture-only".getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA512"));
            callback.put("vnp_SecureHash",new String[]{HexFormat.of().formatHex(mac.doFinal(
                signed.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)))});
        }

        int allocated() {
            return db.queryForObject("select count(*) from payment_allocation where obligation=?",Integer.class,"RENTAL:"+rentalId);
        }

        @Test void vp10_17_rollbackThenConcurrentRetriesAllocateExactlyOnce() throws Exception {
            failNotification.set(true);
            assertThrows(RuntimeException.class,()->payments.handleIpn(callback));
            assertEquals(0,allocated());
            assertEquals("PENDING",payments.getPaymentResult(fixture.a(),ref).status());
            assertTrue(service.view(fixture.a(),rentalId).isPending());
            assertEquals(List.of(0,0),race(()->Integer.valueOf(payments.handleIpn(callback)),
                ()->Integer.valueOf(payments.handleIpn(callback))));
            assertEquals(1,allocated());
            assertEquals("BOOKED",service.view(fixture.a(),rentalId).getStatus());
            assertEquals(0,tx.execute(s -> em.find(Coupon.class,fixture.coupon()).getRemainingQuantity()).intValue());
            assertEquals(1,count("select count(h) from RentalHistory h where h.rental.rentalId="+rentalId+" and h.newStatus='BOOKED'"));
        }

        @Test void vp15_timeoutAndIpnAtBoundaryLeaveOneCancelledOrder() throws Exception {
            clock.instant.updateAndGet(t->t.plusSeconds(900));
            assertEquals(List.of(0,0),race(()->Integer.valueOf(payments.handleIpn(callback)),()->{service.expire(rentalId);return 0;}));
            assertEquals(0,allocated());
            assertEquals("CANCELLED",service.view(fixture.a(),rentalId).getStatus());
            assertEquals("NEEDS_RECONCILIATION",payments.getPaymentResult(fixture.a(),ref).status());
        }

        @Test void validReturnConfirmsBookingAndLaterIpnDoesNotAllocateAgain() {
            var result = payments.handleReturn(fixture.a(),ref,callback);
            assertEquals("SUCCESS",result.status());
            assertEquals("BOOKED",result.rentalStatus());
            assertEquals("SUCCESS",payments.handleReturn(fixture.a(),ref,callback).status());
            assertEquals("00",payments.handleIpn(callback));
            assertEquals(1,allocated());
            assertEquals(0,tx.execute(s -> em.find(Coupon.class,fixture.coupon()).getRemainingQuantity()).intValue());
        }

        @Test void returnChecksOwnerAndSignatureBeforeConfirmingPayment() {
            assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> payments.handleReturn(fixture.b(),ref,callback));
            var tampered = new HashMap<>(callback);
            tampered.put("vnp_Amount",new String[]{"100"});
            assertEquals("PENDING",payments.handleReturn(fixture.a(),ref,tampered).status());
            assertEquals("PENDING",payments.handleReturn(fixture.a(),ref,Map.of("vnp_ResponseCode",new String[]{"00"})).status());
            assertEquals(0,allocated());
        }

        @Test void successfulReturnCannotRestoreExpiredBooking() {
            clock.instant.updateAndGet(t -> t.plusSeconds(900));
            service.expire(rentalId);
            var result = payments.handleReturn(fixture.a(),ref,callback);
            assertEquals("NEEDS_RECONCILIATION",result.status());
            assertEquals("CANCELLED",result.rentalStatus());
            assertEquals(0,allocated());
        }

        @Nested class ReturnFees {
            int staffAccount;
            int fee;
            @BeforeEach void returnedVehicle() throws Exception {
                assertEquals("00",payments.handleIpn(callback));
                staffAccount = tx.execute(s -> {
                    var staff = account(UUID.randomUUID()+"@example.invalid");
                    staff.setRole("STAFF");
                    return staff.getAccountId();
                });
                db.update("insert into nhan_vien(MaTK,MaTram,HoTen,SoDienThoai,TrangThaiLamViec) values(?,?,'Test staff','0900000000','ACTIVE')",
                    staffAccount,fixture.station());
                db.update("update don_thue_xe set TrangThaiDon='RENTED' where MaDonThue=?",rentalId);
                db.update("update xe set TrangThaiXe='Rented' where MaXe=?",fixture.x());
                db.update("""
                    insert into bien_ban_tra_xe(MaDonThue,MaNVNhan,ThoiGianTraThucTe,MucPinKhiTra,TinhTrangXe,DuongDanHinhAnh)
                    select ?,MaNV,?,90,'Test','test.jpg' from nhan_vien where MaTK=?
                    """,rentalId,start.plusDays(1),staffAccount);
                clock.instant.updateAndGet(t -> t.plusSeconds(3*86400));
                fee = addFee("Broken light");
                startAttempt(true);
            }

            int addFee(String reason) {
                db.update("insert into phi_phat_sinh(MaDonThue,LoaiPhi,SoTien,LyDo,TrangThaiThanhToan) values(?,'DAMAGE',25000,?,'UNPAID')",
                    rentalId,reason);
                return db.queryForObject("select MaPhi from phi_phat_sinh where MaDonThue=? and LyDo=?",Integer.class,rentalId,reason);
            }
            String feeStatus(int id) {
                return db.queryForObject("select TrangThaiThanhToan from phi_phat_sinh where MaPhi=?",String.class,id);
            }
            @Test void paysOnlyFeesShownOnInvoice() {
                int later = addFee("Broken mirror");
                assertEquals("SUCCESS",payments.handleReturn(fixture.a(),ref,callback).status());
                assertEquals("PAID",feeStatus(fee));
                assertEquals("UNPAID",feeStatus(later));
            }
            @Test void changedFeeGoesToReview() {
                db.update("update phi_phat_sinh set SoTien=26000 where MaPhi=?",fee);
                assertEquals("00",payments.handleIpn(callback));
                assertEquals("UNPAID",feeStatus(fee));
                assertEquals("NEEDS_RECONCILIATION",payments.getPaymentResult(fixture.a(),ref).status());
            }
            @Test void twoReceiptsPayOneFeeOnce() throws Exception {
                var first = callback;
                String firstRef = ref;
                startAttempt(true);
                assertEquals(List.of(0,0),race(()->Integer.valueOf(payments.handleIpn(first)),()->Integer.valueOf(payments.handleIpn(callback))));
                assertEquals(Set.of("SUCCESS","NEEDS_RECONCILIATION"),Set.of(payments.getPaymentResult(fixture.a(),firstRef).status(),
                    payments.getPaymentResult(fixture.a(),ref).status()));
                assertEquals("PAID",feeStatus(fee));
                assertEquals(1,db.queryForObject("select count(*) from payment_allocation where obligation=?",Integer.class,"FEE:"+fee));
                assertEquals(0,new BigDecimal("25000").compareTo(db.queryForObject(
                    "select sum(amount) from payment_allocated_cashflow where MaDonThue=? and LoaiThanhToan='EXTRA_FEE'",BigDecimal.class,rentalId)));
            }
            @Test void allFeesPaidAwaitStaffCompletion() {
                assertEquals("00",payments.handleIpn(callback));
                assertEquals("PAID",feeStatus(fee));
                assertEquals("RENTED",service.view(fixture.a(),rentalId).getStatus());
                assertEquals("Rented",db.queryForObject("select TrangThaiXe from xe where MaXe=?",String.class,fixture.x()));
            }
            @AfterEach void removeReturnRecord() {
                tx.executeWithoutResult(s -> {
                    db.update("delete from bien_ban_tra_xe where MaDonThue=?",rentalId);
                    db.update("delete from thong_bao where MaTKNhan=?",staffAccount);
                    db.update("delete from nhan_vien where MaTK=?",staffAccount);
                    db.update("delete from tai_khoan where MaTK=?",staffAccount);
                });
            }
        }
        @AfterEach void removePaymentFixture() {
            if (rentalId == 0) return;
            tx.executeWithoutResult(s -> {
                for (String attempt : db.queryForList("select txn_ref from giao_dich_thanh_toan where MaDonThue=?",String.class,rentalId)) {
                    db.update("delete from payment_receipt where signature in (select signature from payment_ipn where ref=?)",attempt);
                    db.update("delete from payment_ipn where ref=?",attempt);
                    db.update("delete from payment_fee_snapshot where txn_ref=?",attempt);
                }
                db.update("delete from payment_allocation where transaction_id in (select MaGD from giao_dich_thanh_toan where MaDonThue=?)",rentalId);
                db.update("delete from giao_dich_thanh_toan where MaDonThue=?",rentalId);
                db.update("delete from phi_phat_sinh where MaDonThue=?",rentalId);
            });
        }
    }
    List<Object> race(Callable<Integer> a, Callable<Integer> b) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2); CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
        try {
            List<Future<Object>> tasks = new ArrayList<>();
            for (Callable<Integer> task : List.of(a,b)) tasks.add(executor.submit(() -> {
                ready.countDown(); if (!go.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Race did not start");
                try { return task.call(); }
                catch (IllegalArgumentException | org.springframework.dao.ConcurrencyFailureException e) { return e; }
            }));
            assertTrue(ready.await(10,TimeUnit.SECONDS)); go.countDown();
            return List.of(tasks.get(0).get(30,TimeUnit.SECONDS),tasks.get(1).get(30,TimeUnit.SECONDS));
        } finally { go.countDown(); executor.shutdownNow(); }
    }
    @AfterEach void cleanup() {
        failNotification.set(false);
        if (fixture == null) return;
        tx.executeWithoutResult(status -> {
            String vehicles = fixture.x() + "," + fixture.y();
            String customers = fixture.customerA() + "," + fixture.customerB();
            String accounts = fixture.accountA() + "," + fixture.accountB();
            em.createQuery("delete from RentalHistory h where h.rental.vehicle.vehicleId in (" + vehicles + ")").executeUpdate();
            em.createQuery("delete from BookingNotification n where n.accountId in (" + accounts + ")").executeUpdate();
            em.createQuery("delete from Rental r where r.vehicle.vehicleId in (" + vehicles + ")").executeUpdate();
            em.createQuery("delete from DrivingLicense l where l.customer.CustomerId in (" + customers + ")").executeUpdate();
            em.createQuery("delete from Vehicle v where v.vehicleId in (" + vehicles + ")").executeUpdate();
            em.createQuery("delete from RentalPrice p where p.vehicleModel.modelId = " + fixture.model()).executeUpdate();
            em.remove(em.find(Coupon.class,fixture.coupon())); em.remove(em.find(VehicleModel.class,fixture.model()));
            em.remove(em.find(VehicleCategory.class,fixture.category())); em.remove(em.find(Station.class,fixture.station()));
            em.createQuery("delete from CustomerProfile c where c.CustomerId in (" + customers + ")").executeUpdate();
            em.createQuery("delete from Account a where a.accountId in (" + accounts + ")").executeUpdate();
        });
    }
}
