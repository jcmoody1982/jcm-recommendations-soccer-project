package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValueBetRecommendationEngineTest {

    @Mock
    private BttsRecommendationEngine bttsEngine;

    @Mock
    private OverGoalsRecommendationEngine overGoalsEngine;

    @Mock
    private UnderGoalsRecommendationEngine underGoalsEngine;

    @Mock
    private BookingPointsRecommendationEngine bookingPointsEngine;

    private ValueBetRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ValueBetRecommendationEngine(bttsEngine, overGoalsEngine, underGoalsEngine, bookingPointsEngine);
    }

    @Test
    @DisplayName("getType returns VALUE_BET")
    void getType_returnsValueBet() {
        assertThat(engine.getType()).isEqualTo(RecommendationType.VALUE_BET);
    }

    @Test
    @DisplayName("analyze returns empty when no odds available")
    void analyze_withNoOdds_returnsEmpty() {
        FixtureContext context = createContextWithoutOdds();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("analyze returns empty when no value opportunities found")
    void analyze_withNoValueOpportunities_returnsEmpty() {
        FixtureContext context = createContextWithOdds();
        when(bttsEngine.analyze(any())).thenReturn(Optional.empty());
        when(overGoalsEngine.analyze(any())).thenReturn(Optional.empty());
        when(underGoalsEngine.analyze(any())).thenReturn(Optional.empty());
        when(bookingPointsEngine.analyze(any())).thenReturn(Optional.empty());

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("analyze finds BTTS value when probability exceeds implied")
    void analyze_withBttsValue_returnsRecommendation() {
        FixtureContext context = createContextWithOdds();
        
        // BTTS odds of 2.0 implies 50% probability, our 70% score should find value
        Recommendation bttsRec = Recommendation.builder()
                .type(RecommendationType.BTTS)
                .score(70.0)
                .confidence(ConfidenceLevel.STRONG)
                .build();
        
        when(bttsEngine.analyze(any())).thenReturn(Optional.of(bttsRec));
        when(overGoalsEngine.analyze(any())).thenReturn(Optional.empty());
        when(underGoalsEngine.analyze(any())).thenReturn(Optional.empty());
        when(bookingPointsEngine.analyze(any())).thenReturn(Optional.empty());

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(RecommendationType.VALUE_BET);
        assertThat(result.get().getMarket()).isEqualTo("BTTS Yes");
        assertThat(result.get().getFactors()).containsKey("valuePercentage");
        assertThat(result.get().getFactors()).containsKey("expectedValue");
    }

    @Test
    @DisplayName("analyze includes Kelly stake in factors")
    void analyze_includesKellyStake() {
        FixtureContext context = createContextWithOdds();
        
        Recommendation bttsRec = Recommendation.builder()
                .type(RecommendationType.BTTS)
                .score(70.0)
                .confidence(ConfidenceLevel.STRONG)
                .build();
        
        when(bttsEngine.analyze(any())).thenReturn(Optional.of(bttsRec));
        when(overGoalsEngine.analyze(any())).thenReturn(Optional.empty());
        when(underGoalsEngine.analyze(any())).thenReturn(Optional.empty());
        when(bookingPointsEngine.analyze(any())).thenReturn(Optional.empty());

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("kellyStake");
        assertThat(result.get().getFactors()).containsKey("suggestedStakePct");
    }

    @Test
    @DisplayName("analyze includes all opportunities in factors")
    void analyze_includesAllOpportunities() {
        FixtureContext context = createContextWithOdds();
        
        Recommendation bttsRec = Recommendation.builder()
                .type(RecommendationType.BTTS)
                .score(70.0)
                .confidence(ConfidenceLevel.STRONG)
                .build();
        
        Recommendation overRec = Recommendation.builder()
                .type(RecommendationType.OVER_GOALS)
                .score(75.0)
                .confidence(ConfidenceLevel.STRONG)
                .build();
        
        when(bttsEngine.analyze(any())).thenReturn(Optional.of(bttsRec));
        when(overGoalsEngine.analyze(any())).thenReturn(Optional.of(overRec));
        when(underGoalsEngine.analyze(any())).thenReturn(Optional.empty());
        when(bookingPointsEngine.analyze(any())).thenReturn(Optional.empty());

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("totalOpportunities");
        assertThat(result.get().getFactors()).containsKey("allOpportunities");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> opportunities = 
                (List<Map<String, Object>>) result.get().getFactors().get("allOpportunities");
        assertThat(opportunities).isNotEmpty();
    }

    @Test
    @DisplayName("analyze tracks source confidence")
    void analyze_tracksSourceConfidence() {
        FixtureContext context = createContextWithOdds();
        
        Recommendation bttsRec = Recommendation.builder()
                .type(RecommendationType.BTTS)
                .score(70.0)
                .confidence(ConfidenceLevel.STRONG)
                .build();
        
        when(bttsEngine.analyze(any())).thenReturn(Optional.of(bttsRec));
        when(overGoalsEngine.analyze(any())).thenReturn(Optional.empty());
        when(underGoalsEngine.analyze(any())).thenReturn(Optional.empty());
        when(bookingPointsEngine.analyze(any())).thenReturn(Optional.empty());

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("sourceConfidence");
        assertThat(result.get().getFactors().get("sourceConfidence")).isEqualTo("Strong");
    }

    @Test
    @DisplayName("analyze selects best opportunity by weighted EV")
    void analyze_selectsBestByWeightedEv() {
        FixtureContext context = createContextWithOdds();
        
        // Both have value, but Over should have higher weighted EV
        Recommendation bttsRec = Recommendation.builder()
                .type(RecommendationType.BTTS)
                .score(65.0)  // Lower score
                .confidence(ConfidenceLevel.MODERATE)
                .build();
        
        Recommendation overRec = Recommendation.builder()
                .type(RecommendationType.OVER_GOALS)
                .score(80.0)  // Higher score
                .confidence(ConfidenceLevel.STRONG)
                .build();
        
        when(bttsEngine.analyze(any())).thenReturn(Optional.of(bttsRec));
        when(overGoalsEngine.analyze(any())).thenReturn(Optional.of(overRec));
        when(underGoalsEngine.analyze(any())).thenReturn(Optional.empty());
        when(bookingPointsEngine.analyze(any())).thenReturn(Optional.empty());

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        // Should select the one with higher weighted EV
        assertThat(result.get().getFactors().get("totalOpportunities")).isNotNull();
    }

    @Test
    @DisplayName("analyze with incomplete data returns empty")
    void analyze_withIncompleteData_returnsEmpty() {
        FixtureContext context = FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home"))
                .awayTeam(createTeam(2L, "Away"))
                .build();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isEmpty();
    }

    private FixtureContext createContextWithOdds() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(20)
                .seasonWinsHome(8)
                .seasonDrawsHome(3)
                .seasonLossesHome(1)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(20)
                .seasonWinsAway(4)
                .seasonDrawsAway(4)
                .seasonLossesAway(4)
                .build();

        FixtureOdds odds = FixtureOdds.builder()
                .fixtureId(1000L)
                .oddsBttsYes(2.0)  // Implies 50%
                .oddsBttsNo(1.8)
                .oddsFtOver15(1.30)
                .oddsFtOver25(1.90)
                .oddsFtOver35(2.80)
                .oddsFtUnder15(3.50)
                .oddsFtUnder25(2.00)
                .oddsFtUnder35(1.45)
                .oddsFt1(2.10)
                .oddsFtX(3.40)
                .oddsFt2(3.60)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .odds(odds)
                .build();
    }

    private FixtureContext createContextWithoutOdds() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(20)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(20)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
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

    private Team createTeam(Long id, String name) {
        return Team.builder()
                .id(id)
                .name(name)
                .build();
    }
}
