package com.example.QuanLyChoThueXeDien.rental;

import com.example.QuanLyChoThueXeDien.auth.controllers.AuthController;
import com.example.QuanLyChoThueXeDien.auth.services.RegistrationService;
import com.example.QuanLyChoThueXeDien.VehicleModel.services.VehicleModelService;
import com.example.QuanLyChoThueXeDien.pricing.repositories.RentalPriceRepository;
import com.example.QuanLyChoThueXeDien.rental.controllers.BookingSession;
import com.example.QuanLyChoThueXeDien.rental.dtos.BookingForm;
import com.example.QuanLyChoThueXeDien.search.controllers.VehicleSearchController;
import com.example.QuanLyChoThueXeDien.search.dtos.*;
import com.example.QuanLyChoThueXeDien.search.services.VehicleSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
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
        BookingForm selection = (BookingForm) session.getAttribute(BookingSession.SELECTION);
        assertEquals(7,selection.getVehicleId()); assertEquals(2,selection.getStationId());
        assertEquals(3,selection.getCategoryId()); assertEquals(4,selection.getModelId());
        assertEquals("2030-01-02T08:00",selection.getStartAt().toString());
        var auth = new AuthController(mock(RegistrationService.class),mock(VehicleModelService.class),mock(RentalPriceRepository.class),search);
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
        assertNull(session.getAttribute(BookingSession.SELECTION));
    }
}
