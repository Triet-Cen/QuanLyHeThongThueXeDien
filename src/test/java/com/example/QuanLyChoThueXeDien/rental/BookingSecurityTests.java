package com.example.QuanLyChoThueXeDien.rental;

import com.example.QuanLyChoThueXeDien.auth.services.CustomUserDetailsService;
import com.example.QuanLyChoThueXeDien.config.SecurityConfig;
import com.example.QuanLyChoThueXeDien.rental.controllers.*;
import com.example.QuanLyChoThueXeDien.session.BookingSession;
import com.example.QuanLyChoThueXeDien.rental.dtos.*;
import com.example.QuanLyChoThueXeDien.rental.services.*;
import com.example.QuanLyChoThueXeDien.pricing.services.RentalQuoteService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.*;
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
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.*;

@ExtendWith(SpringExtension.class) @WebAppConfiguration
@ContextConfiguration(classes = BookingSecurityTests.Config.class)
class BookingSecurityTests {
    @Configuration @EnableWebSecurity @EnableWebMvc
    @Import({SecurityConfig.class, BookingController.class, RentalClock.class})
    static class Config {
        @Bean CustomUserDetailsService users() { return mock(CustomUserDetailsService.class); }
        @Bean BookingService bookings() { return mock(BookingService.class); }
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
    MockMvc mvc;
    @BeforeEach void setup() { reset(service); mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    @Test void anonymousMustLoginBeforeConfirmation() throws Exception {
        mvc.perform(get("/bookings/confirm")).andExpect(status().is3xxRedirection()); verifyNoInteractions(service);
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
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-hold-countdown")));
        mvc.perform(get("/bookings").with(user("a").roles("CUSTOMER"))).andExpect(status().isOk());
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
