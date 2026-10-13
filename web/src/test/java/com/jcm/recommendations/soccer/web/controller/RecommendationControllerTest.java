package com.jcm.recommendations.soccer.web.controller;

import com.jcm.recommendations.soccer.core.config.JacksonConfig;
import com.jcm.recommendations.soccer.core.recommendation.RecommendationService;
import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.core.results.ElitePicksService;
import com.jcm.recommendations.soccer.web.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecommendationController.class)
@Import({SecurityConfig.class, JacksonConfig.class})
@TestPropertySource(properties = "beta.auth.enabled=false")
class RecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecommendationService recommendationService;

    @MockitoBean
    private ElitePicksService elitePicksService;

    @Test
    void eliteReturnsRankedPicksPerDay() throws Exception {
        when(elitePicksService.elitePicks(7.0)).thenReturn(List.of(
                new ElitePicksService.ElitePick(java.time.LocalDate.of(2026, 10, 13), 1, 42L, "BTTS")));

        mockMvc.perform(get("/api/recommendations/elite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-10-13"))
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].fixtureId").value(42))
                .andExpect(jsonPath("$[0].type").value("BTTS"));
    }

    private static Recommendation rec(RecommendationType type, double score) {
        return Recommendation.builder()
                .fixtureId(42L)
                .homeTeamName("Arsenal")
                .awayTeamName("Chelsea")
                .type(type)
                .confidence(ConfidenceLevel.STRONG)
                .score(score)
                .market("Both teams to score")
                .odds(1.85)
                .build();
    }

    @Test
    void groupedDefaultsToSevenDays() throws Exception {
        when(recommendationService.getRecommendationsGroupedByType(7.0))
                .thenReturn(Map.of(RecommendationType.BTTS, List.of(rec(RecommendationType.BTTS, 82))));

        mockMvc.perform(get("/api/recommendations/grouped"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.BTTS[0].fixtureId").value(42))
                .andExpect(jsonPath("$.BTTS[0].confidence").value("STRONG"))
                .andExpect(jsonPath("$.BTTS[0].odds").value(1.85));
    }

    @Test
    void groupedPassesFractionalDaysAhead() throws Exception {
        when(recommendationService.getRecommendationsGroupedByType(anyDouble())).thenReturn(Map.of());

        mockMvc.perform(get("/api/recommendations/grouped").param("daysAhead", "0.5"))
                .andExpect(status().isOk());

        verify(recommendationService).getRecommendationsGroupedByType(0.5);
    }

    @Test
    void nonNumericDaysAheadIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/recommendations/grouped").param("daysAhead", "soon"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(recommendationService);
    }

    @Test
    void typeEndpointAcceptsKebabCase() throws Exception {
        when(recommendationService.getRecommendationsByType(eq(RecommendationType.OVER_25_GOALS), anyDouble()))
                .thenReturn(List.of(rec(RecommendationType.OVER_25_GOALS, 72)));

        mockMvc.perform(get("/api/recommendations/type/over-25-goals").param("daysAhead", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("OVER_25_GOALS"));

        verify(recommendationService).getRecommendationsByType(RecommendationType.OVER_25_GOALS, 3.0);
    }

    @Test
    void unknownTypeIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/recommendations/type/not-a-market"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(recommendationService);
    }

    @Test
    void cornersMergesOverAndUnderSortedByScore() throws Exception {
        when(recommendationService.getRecommendationsByType(eq(RecommendationType.OVER_CORNERS), anyDouble()))
                .thenReturn(List.of(rec(RecommendationType.OVER_CORNERS, 70)));
        when(recommendationService.getRecommendationsByType(eq(RecommendationType.UNDER_CORNERS), anyDouble()))
                .thenReturn(List.of(rec(RecommendationType.UNDER_CORNERS, 85)));

        mockMvc.perform(get("/api/recommendations/corners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("UNDER_CORNERS"))
                .andExpect(jsonPath("$[1].type").value("OVER_CORNERS"));
    }

    @Test
    void fixtureEndpointReturnsEmptyListWhenNoEnginesFire() throws Exception {
        when(recommendationService.getRecommendationsForFixture(99L)).thenReturn(List.of());

        mockMvc.perform(get("/api/recommendations/fixture/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void typesListsEveryRecommendationType() throws Exception {
        mockMvc.perform(get("/api/recommendations/types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(RecommendationType.values().length));
    }
}
