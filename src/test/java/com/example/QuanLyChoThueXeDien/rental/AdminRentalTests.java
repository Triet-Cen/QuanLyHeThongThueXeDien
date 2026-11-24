package com.example.QuanLyChoThueXeDien.rental;

import com.example.QuanLyChoThueXeDien.rental.repositories.AdminRentalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminRentalTests {
    final NamedParameterJdbcTemplate db=mock(NamedParameterJdbcTemplate.class);
    final AdminRentalRepository repo=new AdminRentalRepository(db);

    @Test void ar04RejectsInvalidCriteriaBeforeSearch() {
        var today=LocalDate.of(2026,11,25);
        assertThrows(ResponseStatusException.class,()->repo.search("",null,"","created",today,today.minusDays(1),"",1,20,today.atStartOfDay()));
        assertThrows(ResponseStatusException.class,()->repo.search("",null,"INVALID","created",null,null,"",1,20,today.atStartOfDay()));
        assertThrows(ResponseStatusException.class,()->repo.search("",null,"","created",null,null,"",1,101,today.atStartOfDay()));
        verifyNoInteractions(db);
    }
    @Test void ar07MissingRentalReturns404() {
        assertEquals(404,assertThrows(ResponseStatusException.class,()->repo.rental(32)).getStatusCode().value());
    }
    @Test void ar01AccountWithoutActiveAdminRecordIsDenied() {
        when(db.queryForObject(anyString(),anyMap(),eq(Integer.class))).thenReturn(0);
        assertEquals(403,assertThrows(ResponseStatusException.class,()->repo.requireAdmin("inactive@example.test")).getStatusCode().value());
    }
}
