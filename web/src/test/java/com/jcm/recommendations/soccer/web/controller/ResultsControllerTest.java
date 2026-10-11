package com.jcm.recommendations.soccer.web.controller;

import com.jcm.recommendations.soccer.core.config.JacksonConfig;
import com.jcm.recommendations.soccer.core.results.ResultsPerformanceService;
import com.jcm.recommendations.soccer.core.results.ResultsQueryService;
import com.jcm.recommendations.soccer.web.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResultsController.class)
@Import({SecurityConfig.class, JacksonConfig.class})
@TestPropertySource(properties = "beta.auth.enabled=false")
class ResultsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResultsQueryService resultsQueryService;
    @MockitoBean
    private ResultsPerformanceService resultsPerformanceService;

    @Test
    void datesAreSerialisedAsIsoStrings() throws Exception {
        when(resultsQueryService.listSnapshotDates())
                .thenReturn(List.of(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 8)));

        mockMvc.perform(get("/api/results/dates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("2026-10-09"))
                .andExpect(jsonPath("$[1]").value("2026-10-08"));
    }

    @Test
    void dayResultsParsesIsoDateAndOutcome() throws Exception {
        mockMvc.perform(get("/api/results").param("date", "2026-10-09").param("outcome", "WIN"))
                .andExpect(status().isOk());

        verify(resultsQueryService).getDayResults(LocalDate.of(2026, 10, 9), "WIN");
    }

    @Test
    void dayResultsWithoutParamsUsesDefaults() throws Exception {
        mockMvc.perform(get("/api/results")).andExpect(status().isOk());

        verify(resultsQueryService).getDayResults(isNull(), isNull());
    }

    @Test
    void malformedDateIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/results").param("date", "09/10/2026"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(resultsQueryService);
    }

    @Test
    void invalidOutcomeFromServiceIsBadRequest() throws Exception {
        when(resultsQueryService.getDayResults(any(), anyString()))
                .thenThrow(new IllegalArgumentException("bad outcome"));

        mockMvc.perform(get("/api/results").param("outcome", "MAYBE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void performanceDefaultsToThirtyDays() throws Exception {
        mockMvc.perform(get("/api/results/performance")).andExpect(status().isOk());

        verify(resultsPerformanceService).getPerformance("30d");
    }

    @Test
    void invalidPerformancePeriodIsBadRequest() throws Exception {
        when(resultsPerformanceService.getPerformance("forever"))
                .thenThrow(new IllegalArgumentException("Unsupported period"));

        mockMvc.perform(get("/api/results/performance").param("period", "forever"))
                .andExpect(status().isBadRequest());
    }
}
