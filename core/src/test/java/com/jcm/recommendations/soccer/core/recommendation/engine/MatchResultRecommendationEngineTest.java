package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MatchResultRecommendationEngineTest {

    private MatchResultRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MatchResultRecommendationEngine();
    }

    @Test
    @DisplayName("getType returns MATCH_RESULT")
    void getType_returnsCorrectType() {
        assertThat(engine.getType()).isEqualTo(RecommendationType.MATCH_RESULT);
    }

    @Test
    @DisplayName("analyze returns home win recommendation for dominant home team")
    void analyze_withDominantHomeTeam_returnsHomeWin() {
        FixtureContext context = createDominantHomeTeamContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(RecommendationType.MATCH_RESULT);
        assertThat(result.get().getMarket()).isEqualTo("Home Team");
        assertThat(result.get().getConfidence()).isIn(ConfidenceLevel.MODERATE, ConfidenceLevel.STRONG);
    }

    @Test
    @DisplayName("analyze skips Away tips while Away side is paused")
    void analyze_withDominantAwayTeam_returnsEmpty() {
        FixtureContext context = createDominantAwayTeamContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("analyze integrates xG data when available")
    void analyze_integratesXgData() {
        FixtureContext context = createContextWithXgData();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("xgDataAvailable")).isEqualTo(true);
        assertThat(result.get().getFactors()).containsKey("homeXgForAvg");
        assertThat(result.get().getFactors()).containsKey("awayXgForAvg");
        assertThat(result.get().getFactors()).containsKey("homeXgDominance");
        assertThat(result.get().getFactors()).containsKey("homeXgDominanceMultiplier");
    }

    @Test
    @DisplayName("analyze tracks xG dominance but does not re-apply it as a probability multiplier")
    void analyze_tracksXgDominanceWithoutReapplyingMultiplier() {
        FixtureContext context = createContextWithStrongXgDominance();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        Double xgMultiplier = (Double) result.get().getFactors().get("homeXgDominanceMultiplier");
        assertThat(xgMultiplier).isGreaterThan(1.0);
        assertThat(result.get().getFactors().get("xgDominanceAppliedAsMultiplier")).isEqualTo(false);
    }

    @Test
    @DisplayName("analyze never recommends Draw — deferred to Draw engine")
    void analyze_neverRecommendsDraw() {
        FixtureContext context = createBalancedContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getMarket()).isNotEqualTo("Draw");
        assertThat(result.get().getFactors().get("drawsDeferredToDrawEngine")).isEqualTo(true);
    }

    @Test
    @DisplayName("analyze can be STRONG without odds when probability is high")
    void analyze_strongWithoutOdds_whenProbabilityHigh() {
        FixtureContext context = createDominantHomeTeamContextWithoutOdds();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getConfidence()).isEqualTo(ConfidenceLevel.STRONG);
        assertThat(result.get().getOdds()).isNull();
    }

    @Test
    @DisplayName("analyze dampens form momentum with thin venue sample")
    void analyze_dampensFormMomentumWithThinSample() {
        FixtureContext context = createThinFormSampleHotStreakContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        Double formMultiplier = (Double) result.get().getFactors().get("homeFormMomentumMultiplier");
        // 3-game perfect sample → hot-streak raw then dampened (1.0 + 0.20 * 3/5 = 1.12)
        assertThat(formMultiplier).isLessThan(1.20);
        assertThat(formMultiplier).isCloseTo(1.12, org.assertj.core.data.Offset.offset(0.001));
        assertThat(result.get().getFactors().get("homeFormSampleSize")).isEqualTo(3);
    }

    @Test
    @DisplayName("analyze detects hot streak form momentum")
    void analyze_detectsHotStreak() {
        FixtureContext context = createHotStreakContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("homeFormStatus")).isEqualTo("Hot streak");
        Double formMultiplier = (Double) result.get().getFactors().get("homeFormMomentumMultiplier");
        assertThat(formMultiplier).isEqualTo(1.20);
    }

    @Test
    @DisplayName("analyze detects poor form momentum")
    void analyze_detectsPoorForm() {
        FixtureContext context = createPoorFormContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("homeFormStatus")).isEqualTo("Poor form");
        Double formMultiplier = (Double) result.get().getFactors().get("homeFormMomentumMultiplier");
        assertThat(formMultiplier).isEqualTo(0.85);
    }

    @Test
    @DisplayName("analyze applies home advantage boost")
    void analyze_appliesHomeAdvantage() {
        FixtureContext context = createBalancedContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("homeAdvantageApplied");
        assertThat((Double) result.get().getFactors().get("homeAdvantageApplied")).isGreaterThan(0);
    }

    @Test
    @DisplayName("analyze applies position gap factor")
    void analyze_appliesPositionGapFactor() {
        FixtureContext context = createLargePositionGapContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("positionGap");
        Integer gap = (Integer) result.get().getFactors().get("positionGap");
        assertThat(gap).isGreaterThanOrEqualTo(10);
    }

    @Test
    @DisplayName("analyze detects title race motivation")
    void analyze_detectsTitleRaceMotivation() {
        FixtureContext context = createTitleRaceContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("homeMotivation")).isEqualTo("Title race");
    }

    @Test
    @DisplayName("analyze detects relegation battle motivation")
    void analyze_detectsRelegationMotivation() {
        FixtureContext context = createRelegationBattleContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("awayMotivation")).isEqualTo("Relegation battle");
    }

    @Test
    @DisplayName("analyze tracks all three outcome probabilities")
    void analyze_tracksAllProbabilities() {
        FixtureContext context = createBalancedContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("homeWinProbability");
        assertThat(result.get().getFactors()).containsKey("drawProbability");
        assertThat(result.get().getFactors()).containsKey("awayWinProbability");
        
        double home = (Double) result.get().getFactors().get("homeWinProbability");
        double draw = (Double) result.get().getFactors().get("drawProbability");
        double away = (Double) result.get().getFactors().get("awayWinProbability");
        
        // Probabilities should sum to approximately 100%
        assertThat(home + draw + away).isBetween(99.0, 101.0);
    }

    @Test
    @DisplayName("analyze tracks value vs odds")
    void analyze_tracksValueVsOdds() {
        FixtureContext context = createContextWithOdds();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("valueVsOdds");
        assertThat(result.get().getFactors()).containsKey("oddsFt1");
        assertThat(result.get().getFactors()).containsKey("impliedHomeWinPct");
    }

    @Test
    @DisplayName("analyze tracks positive indicators and risk flags")
    void analyze_tracksIndicatorsAndFlags() {
        FixtureContext context = createDominantHomeTeamContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("positiveIndicators");
        assertThat(result.get().getFactors()).containsKey("riskFlags");
        assertThat(result.get().getFactors().get("positiveIndicators")).isInstanceOf(List.class);
    }

    @Test
    @DisplayName("analyze returns empty when context is incomplete")
    void analyze_withIncompleteContext_returnsEmpty() {
        FixtureContext context = createIncompleteContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("analyze handles missing xG data with redistributed weights")
    void analyze_handlesMissingXgData() {
        FixtureContext context = createContextWithoutXgData();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("xgDataAvailable")).isEqualTo(false);
    }

    @Test
    @DisplayName("analyze still tracks draw probability within bounds for transparency")
    void analyze_constrainsDrawProbability() {
        FixtureContext context = createBalancedContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        Double drawProb = (Double) result.get().getFactors().get("drawProbability");
        assertThat(drawProb).isBetween(10.0, 40.0);
    }

    @Test
    @DisplayName("analyze with odds but no value stays MODERATE even at high probability")
    void analyze_highProbabilityWithoutValue_isModerate() {
        FixtureContext context = createDominantHomeTeamContextWithShortOdds();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getScore()).isGreaterThanOrEqualTo(55.0);
        assertThat(result.get().getConfidence()).isEqualTo(ConfidenceLevel.MODERATE);
    }

    @Test
    @DisplayName("analyze applies additive squad value boost without replacing FS probability")
    void analyze_appliesSquadValueBoost() {
        FixtureContext baseline = createDominantHomeTeamContext();
        double baselineScore = engine.analyze(baseline).orElseThrow().getScore();

        FixtureContext withSquadValue = dominantHomeBuilder(131L)
                .odds(FixtureOdds.builder()
                        .fixtureId(131L)
                        .oddsFt1(1.45)
                        .oddsFtX(4.50)
                        .oddsFt2(7.00)
                        .build())
                .homeSquadProfile(TeamSquadProfile.builder()
                        .teamId(1L)
                        .totalMarketValueEur(900_000_000L)
                        .engineUsable(true)
                        .build())
                .awaySquadProfile(TeamSquadProfile.builder()
                        .teamId(2L)
                        .totalMarketValueEur(200_000_000L)
                        .engineUsable(true)
                        .build())
                .build();

        Optional<Recommendation> result = engine.analyze(withSquadValue);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("squadValueApplied")).isEqualTo(true);
        assertThat(result.get().getFactors().get("squadValueBoostApplied")).isEqualTo(4.0);
        assertThat(result.get().getFactors().get("squadValueDifference"))
                .isEqualTo("Home €900m vs Away €200m (home 4.5×)");
        assertThat(result.get().getDescription()).contains("Squad value");
        assertThat(result.get().getScore()).isGreaterThan(baselineScore);
        @SuppressWarnings("unchecked")
        List<String> positiveIndicators = (List<String>) result.get().getFactors().get("positiveIndicators");
        assertThat(positiveIndicators).contains("Home squad value supports favorite");
    }

    // Helper methods to create test contexts

    private FixtureContext createDominantHomeTeamContext() {
        return dominantHomeBuilder(101L)
                .odds(FixtureOdds.builder()
                        .fixtureId(101L)
                        .oddsFt1(1.45)
                        .oddsFtX(4.50)
                        .oddsFt2(7.00)
                        .build())
                .build();
    }

    private FixtureContext createDominantHomeTeamContextWithoutOdds() {
        return dominantHomeBuilder(121L).build();
    }

    private FixtureContext createDominantHomeTeamContextWithShortOdds() {
        // Odds so short that model probability has no +5% value edge
        return dominantHomeBuilder(122L)
                .odds(FixtureOdds.builder()
                        .fixtureId(122L)
                        .oddsFt1(1.20)
                        .oddsFtX(6.00)
                        .oddsFt2(12.00)
                        .build())
                .build();
    }

    private FixtureContext.FixtureContextBuilder dominantHomeBuilder(long fixtureId) {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsHome(14)
                .seasonDrawsHome(4)
                .seasonLossesHome(2)
                .seasonGoalsHome(35)
                .seasonConcededHome(12)
                .seasonGoalDifference(23)
                .ppgHome(2.3)
                .position(2)
                .xgForAvgHome(2.0)
                .xgAgainstAvgHome(0.8)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsAway(3)
                .seasonDrawsAway(5)
                .seasonLossesAway(12)
                .seasonGoalsAway(15)
                .seasonConcededAway(30)
                .seasonGoalDifference(-15)
                .ppgAway(0.7)
                .position(16)
                .xgForAvgAway(0.9)
                .xgAgainstAvgAway(1.5)
                .build();

        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .winsHome(4)
                .drawsHome(1)
                .lossesHome(0)
                .build();

        TeamRecentForm awayForm = TeamRecentForm.builder()
                .teamId(2L)
                .winsAway(1)
                .drawsAway(1)
                .lossesAway(3)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(fixtureId))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .homeTeamForm(homeForm)
                .awayTeamForm(awayForm);
    }

    private FixtureContext createThinFormSampleHotStreakContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsHome(12)
                .seasonDrawsHome(5)
                .seasonLossesHome(3)
                .seasonGoalsHome(30)
                .seasonConcededHome(14)
                .seasonGoalDifference(16)
                .ppgHome(2.05)
                .position(5)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsAway(6)
                .seasonDrawsAway(6)
                .seasonLossesAway(8)
                .seasonGoalsAway(20)
                .seasonConcededAway(24)
                .seasonGoalDifference(-4)
                .ppgAway(1.2)
                .position(12)
                .build();

        // Only 3 venue form games — all wins. Full hot streak needs 5; should dampen.
        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .winsHome(3)
                .drawsHome(0)
                .lossesHome(0)
                .build();

        TeamRecentForm awayForm = TeamRecentForm.builder()
                .teamId(2L)
                .winsAway(2)
                .drawsAway(1)
                .lossesAway(2)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(123L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .homeTeamForm(homeForm)
                .awayTeamForm(awayForm)
                .build();
    }

    private FixtureContext createDominantAwayTeamContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsHome(3)
                .seasonDrawsHome(4)
                .seasonLossesHome(13)
                .seasonGoalsHome(12)
                .seasonConcededHome(35)
                .seasonGoalDifference(-20)
                .ppgHome(0.65)
                .position(19)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsAway(12)
                .seasonDrawsAway(5)
                .seasonLossesAway(3)
                .seasonGoalsAway(32)
                .seasonConcededAway(14)
                .seasonGoalDifference(18)
                .ppgAway(2.05)
                .position(3)
                .build();

        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .winsHome(0)
                .drawsHome(1)
                .lossesHome(4)
                .build();

        TeamRecentForm awayForm = TeamRecentForm.builder()
                .teamId(2L)
                .winsAway(4)
                .drawsAway(1)
                .lossesAway(0)
                .build();

        FixtureOdds odds = FixtureOdds.builder()
                .fixtureId(102L)
                .oddsFt1(6.00)
                .oddsFtX(4.00)
                .oddsFt2(1.55)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(102L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .homeTeamForm(homeForm)
                .awayTeamForm(awayForm)
                .odds(odds)
                .build();
    }

    private FixtureContext createContextWithXgData() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsHome(10)
                .seasonDrawsHome(5)
                .seasonLossesHome(5)
                .seasonGoalsHome(28)
                .seasonConcededHome(18)
                .seasonGoalDifference(10)
                .ppgHome(1.75)
                .position(6)
                .xgForAvgHome(1.6)
                .xgForAvgAway(1.4)
                .xgAgainstAvgHome(1.0)
                .xgAgainstAvgAway(1.2)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsAway(7)
                .seasonDrawsAway(6)
                .seasonLossesAway(7)
                .seasonGoalsAway(22)
                .seasonConcededAway(24)
                .seasonGoalDifference(-2)
                .ppgAway(1.35)
                .position(10)
                .xgForAvgHome(1.3)
                .xgForAvgAway(1.2)
                .xgAgainstAvgHome(1.3)
                .xgAgainstAvgAway(1.4)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(103L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createContextWithStrongXgDominance() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsHome(12)
                .seasonDrawsHome(5)
                .seasonLossesHome(3)
                .seasonGoalsHome(32)
                .seasonConcededHome(15)
                .seasonGoalDifference(17)
                .ppgHome(2.05)
                .position(4)
                .xgForAvgHome(2.2)  // Very high xG
                .xgAgainstAvgHome(0.9)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsAway(5)
                .seasonDrawsAway(6)
                .seasonLossesAway(9)
                .seasonGoalsAway(18)
                .seasonConcededAway(28)
                .seasonGoalDifference(-10)
                .ppgAway(1.05)
                .position(14)
                .xgForAvgAway(1.0)
                .xgAgainstAvgAway(1.8)  // Very leaky xGA
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(104L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createHotStreakContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsHome(12)
                .seasonDrawsHome(5)
                .seasonLossesHome(3)
                .seasonGoalsHome(30)
                .seasonConcededHome(14)
                .seasonGoalDifference(16)
                .ppgHome(2.05)
                .position(5)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsAway(6)
                .seasonDrawsAway(6)
                .seasonLossesAway(8)
                .seasonGoalsAway(20)
                .seasonConcededAway(24)
                .seasonGoalDifference(-4)
                .ppgAway(1.2)
                .position(12)
                .build();

        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .winsHome(5)  // 5 wins = hot streak
                .drawsHome(0)
                .lossesHome(0)
                .build();

        TeamRecentForm awayForm = TeamRecentForm.builder()
                .teamId(2L)
                .winsAway(2)
                .drawsAway(1)
                .lossesAway(2)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(105L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .homeTeamForm(homeForm)
                .awayTeamForm(awayForm)
                .build();
    }

    private FixtureContext createPoorFormContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsHome(12)    // Strong season record
                .seasonDrawsHome(4)
                .seasonLossesHome(4)
                .seasonGoalsHome(32)
                .seasonConcededHome(18)
                .seasonGoalDifference(14)
                .ppgHome(2.0)
                .position(4)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsAway(4)
                .seasonDrawsAway(5)
                .seasonLossesAway(11)
                .seasonGoalsAway(16)
                .seasonConcededAway(30)
                .seasonGoalDifference(-14)
                .ppgAway(0.85)
                .position(16)
                .build();

        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .winsHome(1)
                .drawsHome(1)
                .lossesHome(3)  // 3 losses = poor form (but still should win due to season strength)
                .build();

        TeamRecentForm awayForm = TeamRecentForm.builder()
                .teamId(2L)
                .winsAway(1)
                .drawsAway(2)
                .lossesAway(2)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(106L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .homeTeamForm(homeForm)
                .awayTeamForm(awayForm)
                .build();
    }

    private FixtureContext createBalancedContext() {
        // Mild home edge so the tip clears the Moderate floor (≥55%) while draw residual stays meaningful
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsHome(11)
                .seasonDrawsHome(5)
                .seasonLossesHome(4)
                .seasonGoalsHome(28)
                .seasonConcededHome(16)
                .seasonGoalDifference(12)
                .ppgHome(1.90)
                .position(8)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsAway(5)
                .seasonDrawsAway(6)
                .seasonLossesAway(9)
                .seasonGoalsAway(18)
                .seasonConcededAway(26)
                .seasonGoalDifference(-8)
                .ppgAway(1.05)
                .position(14)
                .build();

        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .winsHome(3)
                .drawsHome(1)
                .lossesHome(1)
                .build();

        TeamRecentForm awayForm = TeamRecentForm.builder()
                .teamId(2L)
                .winsAway(1)
                .drawsAway(2)
                .lossesAway(2)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(107L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .homeTeamForm(homeForm)
                .awayTeamForm(awayForm)
                .build();
    }

    private FixtureContext createLargePositionGapContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsHome(13)
                .seasonDrawsHome(4)
                .seasonLossesHome(3)
                .seasonGoalsHome(35)
                .seasonConcededHome(14)
                .seasonGoalDifference(21)
                .ppgHome(2.15)
                .position(1)  // Top of table
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsAway(2)
                .seasonDrawsAway(5)
                .seasonLossesAway(13)
                .seasonGoalsAway(12)
                .seasonConcededAway(35)
                .seasonGoalDifference(-23)
                .ppgAway(0.55)
                .position(20)  // Bottom of table
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(108L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createTitleRaceContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsHome(14)
                .seasonDrawsHome(4)
                .seasonLossesHome(2)
                .seasonGoalsHome(38)
                .seasonConcededHome(12)
                .seasonGoalDifference(26)
                .ppgHome(2.3)
                .position(1)  // Title race
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsAway(6)
                .seasonDrawsAway(6)
                .seasonLossesAway(8)
                .seasonGoalsAway(20)
                .seasonConcededAway(24)
                .seasonGoalDifference(-4)
                .ppgAway(1.2)
                .position(12)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(109L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createRelegationBattleContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsHome(7)
                .seasonDrawsHome(6)
                .seasonLossesHome(7)
                .seasonGoalsHome(22)
                .seasonConcededHome(24)
                .seasonGoalDifference(-2)
                .ppgHome(1.35)
                .position(10)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsAway(3)
                .seasonDrawsAway(4)
                .seasonLossesAway(13)
                .seasonGoalsAway(14)
                .seasonConcededAway(32)
                .seasonGoalDifference(-18)
                .ppgAway(0.65)
                .position(18)  // Relegation zone
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(110L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createContextWithOdds() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsHome(10)
                .seasonDrawsHome(5)
                .seasonLossesHome(5)
                .seasonGoalsHome(28)
                .seasonConcededHome(20)
                .seasonGoalDifference(8)
                .ppgHome(1.75)
                .position(7)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsAway(6)
                .seasonDrawsAway(6)
                .seasonLossesAway(8)
                .seasonGoalsAway(20)
                .seasonConcededAway(26)
                .seasonGoalDifference(-6)
                .ppgAway(1.2)
                .position(13)
                .build();

        FixtureOdds odds = FixtureOdds.builder()
                .fixtureId(111L)
                .oddsFt1(1.80)
                .oddsFtX(3.50)
                .oddsFt2(4.50)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(111L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .odds(odds)
                .build();
    }

    private FixtureContext createIncompleteContext() {
        return FixtureContext.builder()
                .fixture(createFixture(112L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .build();
    }

    private FixtureContext createContextWithoutXgData() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsHome(10)
                .seasonDrawsHome(5)
                .seasonLossesHome(5)
                .seasonGoalsHome(28)
                .seasonConcededHome(18)
                .seasonGoalDifference(10)
                .ppgHome(1.75)
                .position(6)
                // No xG data
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .seasonWinsAway(7)
                .seasonDrawsAway(6)
                .seasonLossesAway(7)
                .seasonGoalsAway(22)
                .seasonConcededAway(24)
                .seasonGoalDifference(-2)
                .ppgAway(1.35)
                .position(10)
                // No xG data
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(113L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private Fixture createFixture(Long id) {
        return Fixture.builder()
                .id(id)
                .homeTeamId(1L)
                .awayTeamId(2L)
                .seasonId(1L)
                .status("upcoming")
                .build();
    }

    private Team createTeam(Long id, String name) {
        return Team.builder()
                .id(id)
                .name(name)
                .build();
    }
}
