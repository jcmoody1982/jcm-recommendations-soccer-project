package com.jcm.recommendations.soccer.core.recommendation;

import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.core.service.FixtureService;
import com.jcm.recommendations.soccer.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private FixtureService fixtureService;

    @Mock
    private FixtureContextBuilder contextBuilder;

    @Mock
    private RecommendationEngine mockEngine;

    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService(
                fixtureService,
                contextBuilder,
                List.of(mockEngine)
        );
    }

    @Test
    void generateAllRecommendations_withNoFixtures_returnsEmptyList() {
        when(fixtureService.getUpcomingFixtures(anyInt())).thenReturn(Collections.emptyList());

        List<Recommendation> result = recommendationService.generateAllRecommendations();

        assertThat(result).isEmpty();
    }

    @Test
    void generateAllRecommendations_withFixtures_returnsRecommendations() {
        Fixture fixture = createFixture();
        FixtureContext context = createContext(fixture);
        Recommendation recommendation = createRecommendation(fixture);

        when(fixtureService.getUpcomingFixtures(anyInt())).thenReturn(List.of(fixture));
        when(contextBuilder.buildContextsForFixtures(any())).thenReturn(List.of(context));
        when(mockEngine.getType()).thenReturn(RecommendationType.BTTS);
        when(mockEngine.analyzeAll(any())).thenReturn(List.of(recommendation));

        List<Recommendation> result = recommendationService.generateAllRecommendations();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo(RecommendationType.BTTS);
    }

    @Test
    void getStrongRecommendations_filtersCorrectly() {
        Fixture fixture = createFixture();
        FixtureContext context = createContext(fixture);
        
        Recommendation strongRec = Recommendation.builder()
                .fixtureId(1L)
                .type(RecommendationType.BTTS)
                .confidence(ConfidenceLevel.STRONG)
                .score(85.0)
                .build();
        
        Recommendation moderateRec = Recommendation.builder()
                .fixtureId(2L)
                .type(RecommendationType.OVER_GOALS)
                .confidence(ConfidenceLevel.MODERATE)
                .score(70.0)
                .build();

        when(fixtureService.getUpcomingFixtures(anyInt())).thenReturn(List.of(fixture));
        when(contextBuilder.buildContextsForFixtures(any())).thenReturn(List.of(context));
        when(mockEngine.getType()).thenReturn(RecommendationType.BTTS);
        when(mockEngine.analyzeAll(any())).thenReturn(List.of(strongRec, moderateRec));

        List<Recommendation> result = recommendationService.getStrongRecommendations();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getConfidence()).isEqualTo(ConfidenceLevel.STRONG);
    }

    @Test
    void getRecommendationsForFixture_withValidFixture_returnsRecommendations() {
        Long fixtureId = 1000L;
        Fixture fixture = createFixture();
        FixtureContext context = createContext(fixture);
        Recommendation recommendation = createRecommendation(fixture);

        when(fixtureService.getFixtureById(fixtureId)).thenReturn(fixture);
        when(contextBuilder.buildContext(fixture)).thenReturn(context);
        when(mockEngine.analyze(any())).thenReturn(Optional.of(recommendation));

        List<Recommendation> result = recommendationService.getRecommendationsForFixture(fixtureId);

        assertThat(result).hasSize(1);
    }

    @Test
    void getRecommendationsForFixture_withInvalidFixture_returnsEmptyList() {
        when(fixtureService.getFixtureById(999L)).thenReturn(null);

        List<Recommendation> result = recommendationService.getRecommendationsForFixture(999L);

        assertThat(result).isEmpty();
    }

    @Test
    void getRecommendationsByType_returnsCorrectType() {
        Fixture fixture = createFixture();
        FixtureContext context = createContext(fixture);
        Recommendation recommendation = createRecommendation(fixture);

        when(fixtureService.getUpcomingFixtures(anyInt())).thenReturn(List.of(fixture));
        when(contextBuilder.buildContextsForFixtures(any())).thenReturn(List.of(context));
        when(mockEngine.getType()).thenReturn(RecommendationType.BTTS);
        when(mockEngine.analyzeAll(any())).thenReturn(List.of(recommendation));

        List<Recommendation> result = recommendationService.getRecommendationsByType(RecommendationType.BTTS);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo(RecommendationType.BTTS);
    }

    @Test
    void getRecommendationsGroupedByType_groupsCorrectly() {
        Fixture fixture = createFixture();
        FixtureContext context = createContext(fixture);
        Recommendation recommendation = createRecommendation(fixture);

        when(fixtureService.getUpcomingFixtures(anyInt())).thenReturn(List.of(fixture));
        when(contextBuilder.buildContextsForFixtures(any())).thenReturn(List.of(context));
        when(mockEngine.getType()).thenReturn(RecommendationType.BTTS);
        when(mockEngine.analyzeAll(any())).thenReturn(List.of(recommendation));

        Map<RecommendationType, List<Recommendation>> result = 
                recommendationService.getRecommendationsGroupedByType();

        assertThat(result).containsKey(RecommendationType.BTTS);
        assertThat(result.get(RecommendationType.BTTS)).hasSize(1);
    }

    @Test
    void getSummary_returnsCorrectCounts() {
        Fixture fixture = createFixture();
        FixtureContext context = createContext(fixture);
        
        Recommendation strongRec = Recommendation.builder()
                .fixtureId(1L)
                .type(RecommendationType.BTTS)
                .confidence(ConfidenceLevel.STRONG)
                .score(85.0)
                .build();

        when(fixtureService.getUpcomingFixtures(anyInt())).thenReturn(List.of(fixture));
        when(contextBuilder.buildContextsForFixtures(any())).thenReturn(List.of(context));
        when(mockEngine.getType()).thenReturn(RecommendationType.BTTS);
        when(mockEngine.analyzeAll(any())).thenReturn(List.of(strongRec));

        RecommendationService.RecommendationSummary result = recommendationService.getSummary();

        assertThat(result.fixturesAnalyzed()).isEqualTo(1);
        assertThat(result.totalRecommendations()).isEqualTo(1);
        assertThat(result.strongRecommendations()).isEqualTo(1);
    }

    private Fixture createFixture() {
        return Fixture.builder()
                .id(1000L)
                .seasonId(100L)
                .homeTeamId(1L)
                .awayTeamId(2L)
                .homeTeamName("Home Team")
                .awayTeamName("Away Team")
                .dateUnix(System.currentTimeMillis() / 1000 + 86400)
                .status("incomplete")
                .build();
    }

    private FixtureContext createContext(Fixture fixture) {
        return FixtureContext.builder()
                .fixture(fixture)
                .homeTeam(Team.builder().id(1L).name("Home Team").build())
                .awayTeam(Team.builder().id(2L).name("Away Team").build())
                .homeTeamStats(TeamSeasonStats.builder().teamId(1L).matchesPlayed(20).build())
                .awayTeamStats(TeamSeasonStats.builder().teamId(2L).matchesPlayed(20).build())
                .build();
    }

    private Recommendation createRecommendation(Fixture fixture) {
        return Recommendation.builder()
                .fixtureId(fixture.getId())
                .homeTeamId(fixture.getHomeTeamId())
                .awayTeamId(fixture.getAwayTeamId())
                .homeTeamName(fixture.getHomeTeamName())
                .awayTeamName(fixture.getAwayTeamName())
                .type(RecommendationType.BTTS)
                .confidence(ConfidenceLevel.STRONG)
                .score(85.0)
                .market("BTTS Yes")
                .build();
    }
}
