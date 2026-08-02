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

class ResultBttsRecommendationEngineTest {

    private ResultBttsRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ResultBttsRecommendationEngine();
    }

    @Test
    @DisplayName("getType returns RESULT_BTTS")
    void getType_returnsCorrectType() {
        assertThat(engine.getType()).isEqualTo(RecommendationType.RESULT_BTTS);
    }

    @Test
    @DisplayName("analyze recommends Home Win + BTTS for strong scoring home team")
    void analyze_recommendsHomeWinBtts() {
        FixtureContext context = createHomeWinBttsContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(RecommendationType.RESULT_BTTS);
        assertThat(result.get().getMarket()).isEqualTo("Home Team + BTTS");
        assertThat(result.get().getFactors().get("selectedResultType")).isEqualTo("HOME");
    }

    @Test
    @DisplayName("analyze recommends Away Win + BTTS for strong scoring away team")
    void analyze_recommendsAwayWinBtts() {
        FixtureContext context = createAwayWinBttsContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getMarket()).isEqualTo("Away Team + BTTS");
        assertThat(result.get().getFactors().get("selectedResultType")).isEqualTo("AWAY");
    }

    @Test
    @DisplayName("analyze recommends Draw + BTTS for evenly matched scoring teams")
    void analyze_recommendsDrawBtts() {
        FixtureContext context = createDrawBttsContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getMarket()).isEqualTo("Draw + BTTS");
        assertThat(result.get().getFactors().get("selectedResultType")).isEqualTo("DRAW");
    }

    @Test
    @DisplayName("analyze returns empty when BTTS probability below minimum")
    void analyze_withLowBtts_returnsEmpty() {
        FixtureContext context = createLowBttsContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isEmpty();
    }

    // Each exclusion test starts from a baseline that is known to produce a
    // recommendation, then changes exactly one stat. That isolates the rule under
    // test: the baseline assertion proves the scenario is otherwise viable, so the
    // recommendation disappearing can only be down to the mutated stat.

    @Test
    @DisplayName("analyze excludes home market when home team does not score enough")
    void analyze_excludesHomeMarketWhenScoringTooLow() {
        FixtureContext baseline = createHomeWinBttsContext();
        assertThat(engine.analyze(baseline)).isPresent();

        FixtureContext context = createHomeWinBttsContext();
        context.getHomeTeamStats().setSeasonGoalsHome(20);   // 1.0/game, under the 1.3 requirement

        assertThat(engine.analyze(context)).isEmpty();
    }

    @Test
    @DisplayName("analyze excludes home market when home team concedes too rarely")
    void analyze_excludesHomeMarketWhenConcedingTooRarely() {
        FixtureContext baseline = createHomeWinBttsContext();
        assertThat(engine.analyze(baseline)).isPresent();

        FixtureContext context = createHomeWinBttsContext();
        context.getHomeTeamStats().setSeasonConcededHome(12);   // 0.6/game, under the 0.8 requirement

        assertThat(engine.analyze(context)).isEmpty();
    }

    @Test
    @DisplayName("analyze excludes market when winner keeps too many clean sheets")
    void analyze_excludesWhenWinnerHasHighCleanSheetRate() {
        FixtureContext baseline = createHomeWinBttsContext();
        assertThat(engine.analyze(baseline)).isPresent();

        FixtureContext context = createHomeWinBttsContext();
        context.getHomeTeamStats().setSeasonCleanSheetsHome(10);   // 50%, over the 40% exclusion

        assertThat(engine.analyze(context)).isEmpty();
    }

    @Test
    @DisplayName("analyze excludes market when opponent fails to score too often")
    void analyze_excludesWhenOpponentFailsToScoreOften() {
        FixtureContext baseline = createHomeWinBttsContext();
        assertThat(engine.analyze(baseline)).isPresent();

        FixtureContext context = createHomeWinBttsContext();
        context.getAwayTeamStats().setSeasonFailedToScoreAway(9);   // 45%, over the 35% exclusion

        assertThat(engine.analyze(context)).isEmpty();
    }

    @Test
    @DisplayName("analyze excludes Draw market when teams are not evenly matched")
    void analyze_excludesDrawWhenNotEvenlyMatched() {
        FixtureContext baseline = createDrawBttsContext();
        Optional<Recommendation> baselineResult = engine.analyze(baseline);
        assertThat(baselineResult).isPresent();
        assertThat(baselineResult.get().getFactors().get("selectedResultType")).isEqualTo("DRAW");

        FixtureContext context = createDrawBttsContext();
        context.getAwayTeamStats().setPpgOverall(0.5);   // PPG gap 0.9, over the 0.4 maximum

        assertThat(engine.analyze(context)).isEmpty();
    }

    @Test
    @DisplayName("analyze applies recent form BTTS bonus")
    void analyze_appliesFormBttsBonus() {
        FixtureContext context = createFormBttsBonusContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        @SuppressWarnings("unchecked")
        List<String> applied = (List<String>) result.get().getFactors().get("adjustmentsApplied");
        assertThat(applied).anyMatch(s -> s.contains("BTTS-heavy"));
        assertThat((Double) result.get().getFactors().get("confidenceAdjustmentMultiplier")).isGreaterThan(1.0);
    }

    @Test
    @DisplayName("analyze applies clean sheet penalty")
    void analyze_appliesCleanSheetPenalty() {
        FixtureContext context = createCleanSheetPenaltyContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        @SuppressWarnings("unchecked")
        List<String> applied = (List<String>) result.get().getFactors().get("adjustmentsApplied");
        assertThat(applied).anyMatch(s -> s.contains("clean sheet"));
    }

    @Test
    @DisplayName("analyze applies both-concede bonus")
    void analyze_appliesBothConcedeBonus() {
        FixtureContext context = createHomeWinBttsContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        @SuppressWarnings("unchecked")
        List<String> applied = (List<String>) result.get().getFactors().get("adjustmentsApplied");
        assertThat(applied).anyMatch(s -> s.contains("concede regularly"));
    }

    @Test
    @DisplayName("analyze integrates xG data")
    void analyze_integratesXgData() {
        FixtureContext context = createHomeWinBttsContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("xgDataAvailable")).isEqualTo(true);
        assertThat(result.get().getFactors()).containsKey("xgBttsIndicator");
        assertThat(result.get().getFactors()).containsKey("homeXgFor");
        assertThat(result.get().getFactors()).containsKey("awayXgAgainst");
    }

    @Test
    @DisplayName("analyze flags missing xG data")
    void analyze_withNoXgData_flagsRisk() {
        FixtureContext context = createContextWithoutXgData();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("xgDataAvailable")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        List<String> flags = (List<String>) result.get().getFactors().get("riskFlags");
        assertThat(flags).anyMatch(s -> s.contains("xG"));
    }

    @Test
    @DisplayName("analyze uses API BTTS potential when available")
    void analyze_usesApiBttsPotential() {
        FixtureContext context = createContextWithApiPotential();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("apiBttsPotential");
    }

    @Test
    @DisplayName("analyze tracks combined and adjusted probabilities")
    void analyze_tracksCombinedAndAdjustedProbabilities() {
        FixtureContext context = createHomeWinBttsContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("combinedProbability");
        assertThat(result.get().getFactors()).containsKey("adjustedProbability");
        assertThat(result.get().getFactors()).containsKey("resultProbability");
        assertThat(result.get().getFactors()).containsKey("bttsProbability");
        // Score should be the adjusted probability
        assertThat(result.get().getScore())
                .isEqualTo((Double) result.get().getFactors().get("adjustedProbability"));
    }

    @Test
    @DisplayName("analyze tracks goals and exclusion rates")
    void analyze_tracksGoalsAndExclusionRates() {
        FixtureContext context = createHomeWinBttsContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("homeScoredAvg");
        assertThat(result.get().getFactors()).containsKey("awayScoredAvg");
        assertThat(result.get().getFactors()).containsKey("homeConcededAvg");
        assertThat(result.get().getFactors()).containsKey("awayConcededAvg");
        assertThat(result.get().getFactors()).containsKey("homeCleanSheetPct");
        assertThat(result.get().getFactors()).containsKey("awayFailedToScorePct");
    }

    @Test
    @DisplayName("analyze tracks positive indicators and risk flags")
    void analyze_tracksIndicatorsAndFlags() {
        FixtureContext context = createHomeWinBttsContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("positiveIndicators");
        assertThat(result.get().getFactors()).containsKey("riskFlags");
        @SuppressWarnings("unchecked")
        List<String> indicators = (List<String>) result.get().getFactors().get("positiveIndicators");
        assertThat(indicators).isNotEmpty();
    }

    @Test
    @DisplayName("analyze returns empty when context is incomplete")
    void analyze_withIncompleteContext_returnsEmpty() {
        FixtureContext context = FixtureContext.builder()
                .fixture(createFixture(999L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .build();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("analyze assigns STRONG confidence above threshold")
    void analyze_assignsStrongConfidence() {
        FixtureContext context = createHomeWinBttsContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        if (result.get().getScore() >= 35.0) {
            assertThat(result.get().getConfidence()).isEqualTo(ConfidenceLevel.STRONG);
        } else {
            assertThat(result.get().getConfidence()).isEqualTo(ConfidenceLevel.MODERATE);
        }
    }

    // ===== Helper builders =====

    /** Home team wins often, scores heavily and still concedes. */
    private FixtureContext createHomeWinBttsContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L).seasonId(1L).matchesPlayed(20)
                .ppgHome(2.1).ppgOverall(1.9)
                .seasonWinsHome(13).seasonDrawsHome(4).seasonLossesHome(3)
                .seasonGoalsHome(40)          // 2.0 per game > 1.3
                .seasonConcededHome(20)       // 1.0 per game > 0.8
                .seasonCleanSheetsHome(3)     // 15% < 40%
                .seasonFailedToScoreHome(1)   // 5%
                .seasonBttsPercentageHome(75.0)
                .xgForAvgHome(2.0).xgAgainstAvgHome(1.1)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L).seasonId(1L).matchesPlayed(20)
                .ppgAway(1.2).ppgOverall(1.3)
                .seasonWinsAway(5).seasonDrawsAway(6).seasonLossesAway(9)
                .seasonGoalsAway(26)          // 1.3 per game
                .seasonConcededAway(30)
                .seasonCleanSheetsAway(3)     // 15% < 25% -> both-concede bonus
                .seasonFailedToScoreAway(2)   // 10% < 35%
                .seasonBttsPercentageAway(72.0)
                .xgForAvgAway(1.4).xgAgainstAvgAway(1.6)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(101L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    /** Away team wins often on the road, scores and concedes. */
    private FixtureContext createAwayWinBttsContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L).seasonId(1L).matchesPlayed(20)
                .ppgHome(1.0).ppgOverall(1.1)
                .seasonWinsHome(4).seasonDrawsHome(5).seasonLossesHome(11)
                .seasonGoalsHome(20)
                .seasonConcededHome(34)
                .seasonCleanSheetsHome(2)
                .seasonFailedToScoreHome(3)   // 15% < 35%
                .seasonBttsPercentageHome(74.0)
                .xgForAvgHome(1.1).xgAgainstAvgHome(1.8)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L).seasonId(1L).matchesPlayed(20)
                .ppgAway(2.0).ppgOverall(2.0)
                .seasonWinsAway(13).seasonDrawsAway(4).seasonLossesAway(3)
                .seasonGoalsAway(34)          // 1.7 per game > 1.0
                .seasonConcededAway(18)       // 0.9 per game > 0.7
                .seasonCleanSheetsAway(4)     // 20% < 40%
                .seasonFailedToScoreAway(1)
                .seasonBttsPercentageAway(76.0)
                .xgForAvgAway(1.9).xgAgainstAvgAway(1.0)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(102L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    /** Evenly matched, both score freely, low win rates so draw dominates. */
    private FixtureContext createDrawBttsContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L).seasonId(1L).matchesPlayed(20)
                .ppgHome(1.4).ppgOverall(1.4)
                .seasonWinsHome(5).seasonDrawsHome(9).seasonLossesHome(6)
                .seasonGoalsHome(30)          // 1.5 per game
                .seasonConcededHome(28)
                .seasonCleanSheetsHome(2)
                .seasonFailedToScoreHome(1)
                .seasonBttsPercentageHome(80.0)
                .xgForAvgHome(1.5).xgAgainstAvgHome(1.5)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L).seasonId(1L).matchesPlayed(20)
                .ppgAway(1.35).ppgOverall(1.35)   // PPG diff 0.05 < 0.4
                .seasonWinsAway(4).seasonDrawsAway(9).seasonLossesAway(7)
                .seasonGoalsAway(28)          // 1.4 per game > 1.0
                .seasonConcededAway(30)
                .seasonCleanSheetsAway(2)
                .seasonFailedToScoreAway(1)
                .seasonBttsPercentageAway(82.0)
                .xgForAvgAway(1.4).xgAgainstAvgAway(1.6)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(103L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    /** BTTS percentages too low to clear the 55% gate. */
    private FixtureContext createLowBttsContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L).seasonId(1L).matchesPlayed(20)
                .ppgHome(2.0).ppgOverall(1.8)
                .seasonWinsHome(12).seasonDrawsHome(4).seasonLossesHome(4)
                .seasonGoalsHome(34).seasonConcededHome(18)
                .seasonCleanSheetsHome(8).seasonFailedToScoreHome(2)
                .seasonBttsPercentageHome(30.0)
                .xgForAvgHome(1.7).xgAgainstAvgHome(0.9)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L).seasonId(1L).matchesPlayed(20)
                .ppgAway(1.0).ppgOverall(1.1)
                .seasonWinsAway(4).seasonDrawsAway(5).seasonLossesAway(11)
                .seasonGoalsAway(14).seasonConcededAway(32)
                .seasonCleanSheetsAway(4).seasonFailedToScoreAway(9)
                .seasonBttsPercentageAway(28.0)
                .xgForAvgAway(0.8).xgAgainstAvgAway(1.7)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(104L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    

    

    

    

    

    /** Recent form BTTS high for both sides. */
    private FixtureContext createFormBttsBonusContext() {
        FixtureContext base = createHomeWinBttsContext();

        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .bttsPercentageHome(80.0)
                .build();

        TeamRecentForm awayForm = TeamRecentForm.builder()
                .teamId(2L)
                .bttsPercentageAway(80.0)
                .build();

        return FixtureContext.builder()
                .fixture(base.getFixture())
                .homeTeam(base.getHomeTeam())
                .awayTeam(base.getAwayTeam())
                .homeTeamStats(base.getHomeTeamStats())
                .awayTeamStats(base.getAwayTeamStats())
                .homeTeamForm(homeForm)
                .awayTeamForm(awayForm)
                .build();
    }

    /** Away side keeps clean sheets above the 35% penalty threshold but below the 40% exclusion. */
    private FixtureContext createCleanSheetPenaltyContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L).seasonId(1L).matchesPlayed(20)
                .ppgHome(2.1).ppgOverall(1.9)
                .seasonWinsHome(13).seasonDrawsHome(4).seasonLossesHome(3)
                .seasonGoalsHome(40).seasonConcededHome(20)
                .seasonCleanSheetsHome(3).seasonFailedToScoreHome(1)
                .seasonBttsPercentageHome(75.0)
                .xgForAvgHome(2.0).xgAgainstAvgHome(1.1)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L).seasonId(1L).matchesPlayed(20)
                .ppgAway(1.2).ppgOverall(1.3)
                .seasonWinsAway(5).seasonDrawsAway(6).seasonLossesAway(9)
                .seasonGoalsAway(26).seasonConcededAway(30)
                .seasonCleanSheetsAway(8)     // 40% > 35% penalty, not > 40% exclusion
                .seasonFailedToScoreAway(2)
                .seasonBttsPercentageAway(72.0)
                .xgForAvgAway(1.4).xgAgainstAvgAway(1.6)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(110L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createContextWithoutXgData() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L).seasonId(1L).matchesPlayed(20)
                .ppgHome(2.1).ppgOverall(1.9)
                .seasonWinsHome(13).seasonDrawsHome(4).seasonLossesHome(3)
                .seasonGoalsHome(40).seasonConcededHome(20)
                .seasonCleanSheetsHome(3).seasonFailedToScoreHome(1)
                .seasonBttsPercentageHome(75.0)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L).seasonId(1L).matchesPlayed(20)
                .ppgAway(1.2).ppgOverall(1.3)
                .seasonWinsAway(5).seasonDrawsAway(6).seasonLossesAway(9)
                .seasonGoalsAway(26).seasonConcededAway(30)
                .seasonCleanSheetsAway(3).seasonFailedToScoreAway(2)
                .seasonBttsPercentageAway(72.0)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(111L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createContextWithApiPotential() {
        FixtureContext base = createHomeWinBttsContext();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(101L)
                .bttsPotential(78.0)
                .build();

        return FixtureContext.builder()
                .fixture(base.getFixture())
                .homeTeam(base.getHomeTeam())
                .awayTeam(base.getAwayTeam())
                .homeTeamStats(base.getHomeTeamStats())
                .awayTeamStats(base.getAwayTeamStats())
                .potentials(potentials)
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
