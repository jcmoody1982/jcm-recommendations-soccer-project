package com.jcm.recommendations.soccer.web.controller;

import com.jcm.recommendations.soccer.core.config.JacksonConfig;
import com.jcm.recommendations.soccer.core.service.FixtureService;
import com.jcm.recommendations.soccer.domain.Fixture;
import com.jcm.recommendations.soccer.web.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FixtureController.class)
@Import({SecurityConfig.class, JacksonConfig.class})
@TestPropertySource(properties = "beta.auth.enabled=false")
class FixtureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FixtureService fixtureService;

    @Test
    void upcomingFixturesUseDaysAhead() throws Exception {
        when(fixtureService.getUpcomingFixtures(3.0)).thenReturn(List.of());

        mockMvc.perform(get("/api/fixtures").param("daysAhead", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(fixtureService, never()).getFixturesBySeasonId(anyLong());
    }

    @Test
    void seasonIdTakesPrecedenceOverDaysAhead() throws Exception {
        when(fixtureService.getFixturesBySeasonId(1625L)).thenReturn(List.of());

        mockMvc.perform(get("/api/fixtures").param("seasonId", "1625").param("daysAhead", "3"))
                .andExpect(status().isOk());

        verify(fixtureService).getFixturesBySeasonId(1625L);
        verify(fixtureService, never()).getUpcomingFixtures(anyDouble());
    }

    @Test
    void knownFixtureIsReturned() throws Exception {
        Fixture fixture = new Fixture();
        fixture.setId(42L);
        fixture.setHomeTeamName("Arsenal");
        fixture.setAwayTeamName("Chelsea");
        when(fixtureService.getFixtureById(42L)).thenReturn(fixture);

        mockMvc.perform(get("/api/fixtures/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.homeTeamName").value("Arsenal"));
    }

    @Test
    void missingFixtureOddsAndPotentialsAre404() throws Exception {
        mockMvc.perform(get("/api/fixtures/7")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/fixtures/7/odds")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/fixtures/7/potentials")).andExpect(status().isNotFound());
    }
}
