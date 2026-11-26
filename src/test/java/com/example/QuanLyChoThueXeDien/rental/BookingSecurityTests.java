package com.example.QuanLyChoThueXeDien.rental;

import com.example.QuanLyChoThueXeDien.payment.dtos.PaymentInvoice;
import com.example.QuanLyChoThueXeDien.payment.dtos.PaymentResult;
import com.example.QuanLyChoThueXeDien.payment.dtos.PaymentFee;
import com.example.QuanLyChoThueXeDien.payment.controllers.PaymentController;
import com.example.QuanLyChoThueXeDien.payment.services.PaymentService;
import com.example.QuanLyChoThueXeDien.auth.services.CustomUserDetailsService;
import com.example.QuanLyChoThueXeDien.config.SecurityConfig;
import com.example.QuanLyChoThueXeDien.rental.controllers.*;
import com.example.QuanLyChoThueXeDien.session.BookingSession;
import com.example.QuanLyChoThueXeDien.rental.dtos.*;
import com.example.QuanLyChoThueXeDien.rental.services.*;
import com.example.QuanLyChoThueXeDien.pricing.services.RentalQuoteService;
import com.example.QuanLyChoThueXeDien.search.controllers.VehicleSearchController;
import com.example.QuanLyChoThueXeDien.search.services.VehicleSearchService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import java.math.BigDecimal;
import java.sql.SQLSyntaxErrorException;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.*;

@ExtendWith(SpringExtension.class) @WebAppConfiguration
@ContextConfiguration(classes = BookingSecurityTests.Config.class)
class BookingSecurityTests {
    @Configuration @EnableWebSecurity @EnableWebMvc
    @Import({SecurityConfig.class, BookingController.class, RentalClock.class, PaymentController.class, VehicleSearchController.class})
    static class Config {
        @Bean CustomUserDetailsService users() { return mock(CustomUserDetailsService.class); }
        @Bean RentalRefundService refunds() { return mock(RentalRefundService.class); }
        @Bean BookingService bookings() { return mock(BookingService.class); }
        @Bean PaymentService payments() { return mock(PaymentService.class); }
        @Bean VehicleSearchService search() { return mock(VehicleSearchService.class); }
        @Bean org.thymeleaf.spring6.view.ThymeleafViewResolver views() {
            var resolver = new org.thymeleaf.templateresolver.ClassLoaderTemplateResolver();
            resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setCharacterEncoding("UTF-8");
            var engine = new org.thymeleaf.spring6.SpringTemplateEngine(); engine.setTemplateResolver(resolver);
            var views = new org.thymeleaf.spring6.view.ThymeleafViewResolver();
            views.setTemplateEngine(engine); views.setCharacterEncoding("UTF-8"); return views;
        }
    }
    @Autowired WebApplicationContext context;
    @Autowired BookingService service;
    @Autowired RentalRefundService refunds;
    @Autowired PaymentService payments;
    @Autowired VehicleSearchService search;
    MockMvc mvc;
    @BeforeEach void setup() { reset(service, payments); mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    @Test void anonymousMustLoginBeforeConfirmation() throws Exception {
        mvc.perform(get("/bookings/confirm")).andExpect(status().is3xxRedirection()); verifyNoInteractions(service);
    }

    @Test void guestCanChooseDatesForSelectedModelBeforeLogin() throws Exception {
        var vehicleModel = mock(com.example.QuanLyChoThueXeDien.VehicleModel.entities.VehicleModel.class);
        when(vehicleModel.getModelId()).thenReturn(4);
        when(vehicleModel.getModelName()).thenReturn("VF 5");
        var station = new com.example.QuanLyChoThueXeDien.station.entities.Station();
        station.setStationId(2);
        station.setStationName("Trạm A");
        when(search.findBookableModel(4)).thenReturn(java.util.Optional.of(vehicleModel));
        when(search.bookingStations(4)).thenReturn(java.util.List.of(station));
        mvc.perform(get("/vehicles").param("bookModelId","4"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Đặt VF 5")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Tiếp tục đặt xe")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"startAt\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"endAt\"")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("search-errors"))));
    }
    @Test void anonymousIpnReachesHandlerWhileReturnRequiresLogin() throws Exception {
        when(payments.handleIpn(anyMap())).thenReturn("97");
        mvc.perform(get("/payments/vnpay/ipn")).andExpect(status().isOk())
            .andExpect(jsonPath("$.RspCode").value("97"));
        verify(payments).handleIpn(anyMap());
        mvc.perform(get("/payments/vnpay/return").param("vnp_TxnRef","test"))
            .andExpect(status().is3xxRedirection());
    }
    @Test void staffAndAdminCannotCreateCustomerOrder() throws Exception {
        for (String role : new String[]{"STAFF", "ADMIN"})
            mvc.perform(post("/bookings").with(user("staff").roles(role)).with(csrf()).param("quoteToken","x"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void csrfRequiredForCreateAndCancel() throws Exception {
        mvc.perform(post("/bookings").with(user("a").roles("CUSTOMER")).param("quoteToken","x"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/bookings/8/cancel").with(user("a").roles("CUSTOMER")))
            .andExpect(status().isForbidden()); verifyNoInteractions(service);
    }
    @Test void cannotSupplyForeignCustomerThroughCancellationForm() throws Exception {
        mvc.perform(post("/bookings/8/cancel").with(user("a").roles("CUSTOMER")).with(csrf()).param("customerId","999"))
            .andExpect(status().is3xxRedirection()); verify(service).cancel("a",8);
    }
    @Test void confirmationRendersServerPriceAndCsrf() throws Exception {
        BookingForm form = selection(); MockHttpSession session = new MockHttpSession();
        BookingSession.saveSelection(session, form);
        when(service.quote(eq("a"), any())).thenReturn(quote(form));
        mvc.perform(get("/bookings/confirm").session(session).with(user("a").roles("CUSTOMER")))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("quoteToken")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("_csrf")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("120.000")));
    }
    @Test void postIgnoresForgedCustomerAndAmount() throws Exception {
        MockHttpSession session = new MockHttpSession(); BookingQuote quote = quote(selection());
        String token = BookingSession.of(session,"a").add(quote); when(service.create("a",quote)).thenReturn(8);
        mvc.perform(post("/bookings").session(session).with(user("a").roles("CUSTOMER")).with(csrf())
                .param("quoteToken",token).param("amount","1").param("customerId","999"))
            .andExpect(redirectedUrl("/bookings/8"));
        mvc.perform(post("/bookings").session(session).with(user("a").roles("CUSTOMER")).with(csrf()).param("quoteToken",token))
            .andExpect(redirectedUrl("/bookings/8"));
        verify(service, times(1)).create("a",quote);
    }
    @Test void pendingAndListTemplatesRender() throws Exception {
        var r = new com.example.QuanLyChoThueXeDien.rental.entities.Rental();
        var model = new com.example.QuanLyChoThueXeDien.VehicleModel.entities.VehicleModel();
        var category = new com.example.QuanLyChoThueXeDien.category.entities.VehicleCategory();
        model.updateDetails("Xe X","Brand",5,null,category);
        var vehicle = new com.example.QuanLyChoThueXeDien.vehicle.entities.Vehicle(); vehicle.setVehicleModel(model);
        var station = new com.example.QuanLyChoThueXeDien.station.entities.Station(); station.setStationName("Trạm A");
        r.setVehicle(vehicle); r.setPickupStation(station); r.setRentalId(8); r.setStatus("PENDING_PAYMENT");
        r.setStartAt(LocalDateTime.now().plusDays(1)); r.setEndAt(r.getStartAt().plusDays(1));
        r.setHoldExpiresAt(LocalDateTime.now().plusMinutes(15)); r.setRentalDays(1);
        r.setDailyRate(BigDecimal.TEN); r.setRentalAmount(BigDecimal.TEN); r.setDiscountAmount(BigDecimal.ZERO); r.setTotalAmount(BigDecimal.TEN);
        when(service.view("a",8)).thenReturn(r); when(service.own("a")).thenReturn(java.util.List.of(r));
        mvc.perform(get("/bookings/8").with(user("a").roles("CUSTOMER"))).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-hold-countdown")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"/bookings/8/vnpay\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Thanh toán qua VNPay")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(PaymentService.rentalInvoice(r).key())));
        mvc.perform(get("/bookings").with(user("a").roles("CUSTOMER"))).andExpect(status().isOk());
        r.setStatus("CANCELLED");
        when(refunds.customerView("a",8)).thenReturn(java.util.Map.of("amount",BigDecimal.TEN,"requestAvailable",false));
        mvc.perform(get("/bookings/8").with(user("a").roles("CUSTOMER"))).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("chưa sẵn sàng")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/refund-request"))));
        when(refunds.customerView("a",8)).thenReturn(java.util.Map.of("amount",BigDecimal.TEN,"requestAvailable",true));
        mvc.perform(get("/bookings/8").with(user("a").roles("CUSTOMER"))).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"/bookings/8/refund-request\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(">Gửi yêu cầu hoàn tiền</button>")));
        r.setStatus("PENDING_PAYMENT");
        when(payments.getInvoice("a",8,false)).thenReturn(new PaymentInvoice(r,false,java.util.List.of(),BigDecimal.TEN,"snapshot"));
        mvc.perform(get("/bookings/8/invoice").with(user("a").roles("CUSTOMER"))).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("snapshot")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("_csrf")));
        var fee = new PaymentFee(1,BigDecimal.TEN,"DAMAGE","Đèn trước","UNPAID");
        when(payments.getInvoice("a",8,true)).thenReturn(new PaymentInvoice(r,true,java.util.List.of(fee),BigDecimal.TEN,"fees"));
        mvc.perform(get("/bookings/8/invoice?fees=true").with(user("a").roles("CUSTOMER"))).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Đèn trước")));
    }

    @Test void vp01_paymentPostRequiresCustomerAndCsrf() throws Exception {
        mvc.perform(post("/bookings/8/vnpay").with(user("a").roles("CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(post("/bookings/8/vnpay").with(user("a").roles("STAFF")).with(csrf())).andExpect(status().isForbidden());
    }

    @Test void missingPaymentTableReturnsCustomerToBookingWithMessage() throws Exception {
        var missingTable = new BadSqlGrammarException("check payment", "SELECT ...",
            new SQLSyntaxErrorException("Table 'giao_dich_thanh_toan' doesn't exist", "42S02", 1146));
        when(payments.getInvoice("a",8,false)).thenThrow(missingTable);
        when(payments.startPayment(eq("a"),eq(8),eq(false),eq("snapshot"),anyString()))
            .thenThrow(missingTable);

        for (var request : java.util.List.of(get("/bookings/8/invoice"),
                post("/bookings/8/vnpay").with(csrf()).param("fees","false").param("invoiceKey","snapshot")
                    .param("requestKey",UUID.randomUUID().toString()))) {
            mvc.perform(request.with(user("a").roles("CUSTOMER")))
                .andExpect(redirectedUrl("/bookings/8"))
                .andExpect(flash().attribute("errorMessage",
                    "Chưa thể thanh toán qua VNPay vì cơ sở dữ liệu chưa được chuẩn bị cho thanh toán."));
        }
    }

    @Test void vp06_14_returnWithForgedSuccessDisplaysPersistedPending() throws Exception {
        when(payments.handleReturn(eq("a"),eq("ref"),argThat(params ->
                "00".equals(params.get("vnp_ResponseCode")[0]))))
            .thenReturn(new PaymentResult(8,"ref","PENDING","PENDING_PAYMENT",BigDecimal.TEN));
        mvc.perform(get("/payments/vnpay/return?vnp_TxnRef=ref&vnp_ResponseCode=00").with(user("a").roles("CUSTOMER")))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Đang chờ VNPay xác nhận")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/bookings/8\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Về chi tiết đơn thuê")));
    }

    @Test void reconciliationViewAndNotesAreAdminOnly() throws Exception {
        mvc.perform(get("/admin/payments/reconciliation").with(user("a").roles("CUSTOMER"))).andExpect(status().isForbidden());
        when(payments.listPaymentsForReview()).thenReturn(java.util.List.of(java.util.Map.of("event_id",1,"ref","REF","disposition","REVIEW_AMOUNT",
            "rental",8,"provider_id","123","expected",10,"received_amount",20,"currency","VND","received_at","08:00","resolution","")));
        mvc.perform(get("/admin/payments/reconciliation").with(user("a").roles("ADMIN"))).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/reconciliation/1/notes")));
    }
    private BookingForm selection() {
        BookingForm f = new BookingForm(); f.setStationId(1); f.setVehicleId(1);
        f.setStartAt(LocalDateTime.of(2030,1,2,8,0)); f.setEndAt(f.getStartAt().plusDays(1)); return f;
    }
    private BookingQuote quote(BookingForm f) {
        return new BookingQuote(f,"Xe X",null,null,"Trạm A",1,new BigDecimal("120000"),BigDecimal.ZERO,
            RentalQuoteService.amounts(new BigDecimal("120000"),1,BigDecimal.ZERO),null,LocalDateTime.now());
    }
}
