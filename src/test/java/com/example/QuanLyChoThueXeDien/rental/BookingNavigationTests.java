package com.example.QuanLyChoThueXeDien.rental;

import com.example.QuanLyChoThueXeDien.auth.controllers.AuthController;
import com.example.QuanLyChoThueXeDien.auth.services.RegistrationService;
import com.example.QuanLyChoThueXeDien.VehicleModel.services.VehicleModelService;
import com.example.QuanLyChoThueXeDien.VehicleModel.entities.VehicleModel;
import com.example.QuanLyChoThueXeDien.category.entities.VehicleCategory;
import com.example.QuanLyChoThueXeDien.pricing.entities.RentalPrice;
import com.example.QuanLyChoThueXeDien.pricing.repositories.RentalPriceRepository;
import com.example.QuanLyChoThueXeDien.pricing.services.RentalQuoteService;
import com.example.QuanLyChoThueXeDien.session.BookingSession;
import com.example.QuanLyChoThueXeDien.rental.dtos.BookingForm;
import com.example.QuanLyChoThueXeDien.search.controllers.VehicleSearchController;
import com.example.QuanLyChoThueXeDien.search.dtos.*;
import com.example.QuanLyChoThueXeDien.search.services.VehicleSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BookingNavigationTests {
    @Test void selectionRetainsExactCriteriaAcrossLogin() throws Exception {
        var search = mock(VehicleSearchService.class);
        when(search.options()).thenReturn(new SearchOptions(List.of(),List.of(),List.of()));
        when(search.search(any())).thenReturn(List.of(new VehicleSearchCard(7,"Xe X",null,"Brand",5,"EV","Trạm A",BigDecimal.TEN,1,BigDecimal.TEN,null)));
        var mvc = MockMvcBuilders.standaloneSetup(new VehicleSearchController(search)).build();
        var session = new MockHttpSession();
        mvc.perform(get("/vehicles").session(session).param("selectVehicleId","7").param("stationId","2")
                .param("categoryId","3").param("modelId","4").param("startAt","2030-01-02T08:00:00").param("endAt","2030-01-03T08:00:00"))
            .andExpect(redirectedUrl("/bookings/confirm"));
        BookingForm selection = BookingSession.getSelection(session);
        assertEquals(7,selection.getVehicleId()); assertEquals(2,selection.getStationId());
        assertEquals(3,selection.getCategoryId()); assertEquals(4,selection.getModelId());
        assertEquals("2030-01-02T08:00",selection.getStartAt().toString());
        var auth = new AuthController(mock(RegistrationService.class),mock(VehicleModelService.class),mock(RentalQuoteService.class),search);
        var user = new UsernamePasswordAuthenticationToken("a","",List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        assertEquals("redirect:/bookings/confirm",auth.redirectAfterLogin(user,session));
    }
    @Test void staleVehicleSelectionCannotBecomeBookingDraft() throws Exception {
        var search = mock(VehicleSearchService.class);
        when(search.options()).thenReturn(new SearchOptions(List.of(),List.of(),List.of())); when(search.search(any())).thenReturn(List.of());
        var mvc = MockMvcBuilders.standaloneSetup(new VehicleSearchController(search)).build(); var session = new MockHttpSession();
        mvc.perform(get("/vehicles").session(session).param("selectVehicleId","7").param("stationId","2")
                .param("startAt","2030-01-02T08:00:00").param("endAt","2030-01-03T08:00:00"))
            .andExpect(view().name("home")).andExpect(model().attributeHasErrors("searchForm"));
        assertNull(BookingSession.getSelection(session));
    }

    @Test void homeUsesFirstDisplayRateAndOmitsModelsWithoutPrice() throws Exception {
        var category = new VehicleCategory();
        var pricedModel = mock(VehicleModel.class);
        when(pricedModel.getModelId()).thenReturn(7);
        when(pricedModel.getModelStatus()).thenReturn("ACTIVE");
        when(pricedModel.getCategory()).thenReturn(category);
        var unpricedModel = mock(VehicleModel.class);
        when(unpricedModel.getModelId()).thenReturn(8);
        when(unpricedModel.getModelStatus()).thenReturn("ACTIVE");
        when(unpricedModel.getCategory()).thenReturn(category);

        var models = mock(VehicleModelService.class);
        when(models.findAll()).thenReturn(List.of(pricedModel, unpricedModel));
        var prices = mock(RentalPriceRepository.class);
        var currentRate = new BigDecimal("500000");
        when(prices.findEffectivePrices(any(LocalDate.class))).thenReturn(List.of(
            new RentalPrice(pricedModel, currentRate, LocalDate.of(2026, 11, 1), null),
            new RentalPrice(pricedModel, new BigDecimal("450000"), LocalDate.of(2026, 10, 1), null)
        ));
        var search = mock(VehicleSearchService.class);
        when(search.options()).thenReturn(new SearchOptions(List.of(), List.of(), List.of()));
        var auth = new AuthController(mock(RegistrationService.class), models, new RentalQuoteService(prices), search);

        var mvc = MockMvcBuilders.standaloneSetup(auth)
            .setViewResolvers(new InternalResourceViewResolver("/templates/", ".html"))
            .build();
        mvc.perform(get("/home"))
            .andExpect(status().isOk())
            .andExpect(view().name("home"))
            .andExpect(model().attribute("vehicleModels", List.of(pricedModel)))
            .andExpect(model().attribute("dailyRates", Map.of(7, currentRate)));
    }
}
