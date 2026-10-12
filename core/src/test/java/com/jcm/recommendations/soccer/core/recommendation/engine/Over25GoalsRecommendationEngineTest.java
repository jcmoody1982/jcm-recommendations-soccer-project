package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.domain.Fixture;
import com.jcm.recommendations.soccer.domain.FixtureOdds;
import com.jcm.recommendations.soccer.domain.FixturePotentials;
import com.jcm.recommendations.soccer.domain.Team;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Over25GoalsRecommendationEngineTest {

    private Over25GoalsRecommendationEngine engine;
    private OverGoalsRecommendationEngine overGoalsEngine;

    @BeforeEach
    void setUp() {
        overGoalsEngine = mock(OverGoalsRecommendationEngine.class);
        when(overGoalsEngine.assess(any())).thenReturn(Optional.of(mock(Recommendation.class)));
        engine = new Over25GoalsRecommendationEngine(overGoalsEngine);
    }

    @Test
    @DisplayName("analyze publishes nothing when the Over Goals cross-check does not also reach Over 2.5")
    void analyze_withoutCrossCheckAgreement_returnsEmpty() {
        when(overGoalsEngine.assess(any())).thenReturn(Optional.empty());

        assertThat(engine.analyze(veryHighScoringContext())).isEmpty();
    }

    @Test
    @DisplayName("published score blends 60% toward the de-vigged Over/Under 2.5 market")
    void analyze_withBothPrices_blendsTowardFairMarket() {
        FixtureContext base = veryHighScoringContext();
        FixtureContext priced = withOdds(base, FixtureOdds.builder()
                .fixtureId(1000L).oddsFtOver25(1.60).oddsFtUnder25(2.30).build());

        Recommendation rec = engine.analyze(priced).orElseThrow();

        double fair = (1 / 1.60) / ((1 / 1.60) + (1 / 2.30)) * 100.0;
        double model = (double) rec.getFactors().get("modelScore");
        assertThat(rec.getFactors()).containsEntry("marketBlendApplied", true);
        assertThat((double) rec.getFactors().get("fairMarketProbability")).isCloseTo(fair, within(1e-9));
        assertThat(rec.getScore()).isCloseTo(0.4 * model + 0.6 * fair, within(1e-9));
        assertThat(rec.getScore()).isLessThan(model);
    }

    @Test
    @DisplayName("without an Under 2.5 price the blend falls back to the raw implied probability")
    void fairOverProbability_fallsBackToRawImplied() {
        assertThat(TotalGoalsOverRecommendationEngine.fairOverProbability(2.0, null)).isCloseTo(50.0, within(1e-9));
        assertThat(TotalGoalsOverRecommendationEngine.fairOverProbability(2.0, 2.0)).isCloseTo(50.0, within(1e-9));
        assertThat(TotalGoalsOverRecommendationEngine.fairOverProbability(null, 2.0)).isNull();
        assertThat(TotalGoalsOverRecommendationEngine.fairOverProbability(1.0, 2.0)).isNull();
    }

    @Test
    @DisplayName("a blend that drags the score below Moderate drops the pick")
    void analyze_whenMarketDisagrees_dropsPick() {
        FixtureContext longPriced = withOdds(veryHighScoringContext(), FixtureOdds.builder()
                .fixtureId(1000L).oddsFtOver25(3.50).oddsFtUnder25(1.30).build());

        assertThat(engine.analyze(longPriced)).isEmpty();
    }

    private FixtureContext withOdds(FixtureContext base, FixtureOdds odds) {
        return FixtureContext.builder()
                .fixture(base.getFixture())
                .homeTeam(base.getHomeTeam())
                .awayTeam(base.getAwayTeam())
                .homeTeamStats(base.getHomeTeamStats())
                .awayTeamStats(base.getAwayTeamStats())
                .odds(odds)
                .potentials(base.getPotentials())
                .build();
    }

    @Test
    @DisplayName("getType returns OVER_25_GOALS")
    void getType_returnsOver25Goals() {
        assertThat(engine.getType()).isEqualTo(RecommendationType.OVER_25_GOALS);
    }

    @Test
    @DisplayName("analyze recommends Over 2.5 Goals and never steps up to Over 3.5")
    void analyze_withHighScoringTeams_alwaysMarketsOver25() {
        Optional<Recommendation> result = engine.analyze(veryHighScoringContext());

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(RecommendationType.OVER_25_GOALS);
        assertThat(result.get().getMarket()).isEqualTo("Over 2.5 Goals");
        assertThat(result.get().getOdds()).isEqualTo(1.60);
        assertThat(result.get().getScore()).isLessThan(80.0);
        assertThat(result.get().getFactors()).containsKeys("expectedGoals", "over25PctHome", "over25PctAway", "apiO25Potential");
        assertThat(result.get().getFactors().get("ceilingApplied")).isEqualTo(true);
    }

    @Test
    @DisplayName("applyPublishCeiling compresses 80+ scores below 80")
    void applyPublishCeiling_compressesEightyPlusBand() {
        assertThat(engine.applyPublishCeiling(86.0)).isLessThan(80.0);
        assertThat(engine.applyPublishCeiling(68.0)).isEqualTo(68.0);
        assertThat(engine.applyPublishCeiling(100.0))
                .isLessThan(Over25GoalsRecommendationEngine.MAX_REALISTIC_PROBABILITY);
    }

    @Test
    @DisplayName("analyze with low expected goals returns empty")
    void analyze_withLowExpectedGoals_returnsEmpty() {
        Optional<Recommendation> result = engine.analyze(contextWithGoalStats(0.5, 0.3, 0.4, 0.5, 70.0, 65.0));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("analyze with very weak Over 2.5 rates still falls below Moderate")
    void analyze_withWeakOver25Rates_returnsEmpty() {
        Optional<Recommendation> result = engine.analyze(contextWithGoalStats(2.5, 1.8, 1.0, 1.4, 12.0, 10.0));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("analyze with incomplete data returns empty")
    void analyze_withIncompleteData_returnsEmpty() {
        FixtureContext context = FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home"))
                .awayTeam(createTeam(2L, "Away"))
                .build();

        assertThat(engine.analyze(context)).isEmpty();
    }

    @Test
    @DisplayName("season Over 2.5 rate falls back to count / matches played")
    void seasonOverPercentage_fallsBackToCount() {
        assertThat(engine.seasonOverPercentage(null)).isNull();
        assertThat(engine.seasonOverPercentage(TeamSeasonStats.builder().seasonOver25PercentageOverall(55.0).build()))
                .isEqualTo(55.0);
        assertThat(engine.seasonOverPercentage(TeamSeasonStats.builder().seasonOver25Overall(6).matchesPlayed(8).build()))
                .isEqualTo(75.0);
        assertThat(engine.seasonOverPercentage(TeamSeasonStats.builder().seasonOver25Overall(6).matchesPlayed(0).build()))
                .isNull();
    }

    @Test
    @DisplayName("form Over 2.5 rate falls back to count over the last five")
    void formOverPercentage_fallsBackToLastFive() {
        assertThat(engine.formOverPercentage(null)).isNull();
        assertThat(engine.formOverPercentage(TeamRecentForm.builder().over25PercentageOverall(40.0).build()))
                .isEqualTo(40.0);
        assertThat(engine.formOverPercentage(TeamRecentForm.builder().over25Overall(3).build())).isEqualTo(60.0);
        assertThat(engine.formOverPercentage(TeamRecentForm.builder().build())).isNull();
    }

    @Test
    @DisplayName("missing odds and potentials publish unpriced without the API factor")
    void analyze_withoutOddsOrPotentials_publishesUnpriced() {
        FixtureContext full = veryHighScoringContext();
        FixtureContext bare = FixtureContext.builder()
                .fixture(full.getFixture())
                .homeTeam(full.getHomeTeam())
                .awayTeam(full.getAwayTeam())
                .homeTeamStats(full.getHomeTeamStats())
                .awayTeamStats(full.getAwayTeamStats())
                .build();

        Recommendation rec = engine.analyze(bare).orElseThrow();

        assertThat(rec.getOdds()).isNull();
        assertThat(rec.getFactors()).doesNotContainKey("apiO25Potential");
        assertThat(rec.getFactors()).containsEntry("marketBlendApplied", false);
        assertThat(rec.getScore()).isEqualTo((double) rec.getFactors().get("modelScore"));
    }

    private FixtureContext veryHighScoringContext() {
        int matches = 10;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .matchesPlayedHome(matches)
                .matchesPlayedAway(matches)
                .seasonGoalsHome(30)
                .seasonGoalsAway(25)
                .seasonConcededHome(18)
                .seasonConcededAway(15)
                .seasonOver25PercentageOverall(90.0)
                .seasonOver35PercentageOverall(60.0)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .matchesPlayedHome(matches)
                .matchesPlayedAway(matches)
                .seasonGoalsHome(28)
                .seasonGoalsAway(22)
                .seasonConcededHome(15)
                .seasonConcededAway(20)
                .seasonOver25PercentageOverall(85.0)
                .seasonOver35PercentageOverall(55.0)
                .build();

        FixtureOdds odds = FixtureOdds.builder()
                .fixtureId(1000L)
                .oddsFtOver25(1.60)
                .oddsFtOver35(1.90)
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(1000L)
                .o25Potential(85.0)
                .o35Potential(60.0)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .odds(odds)
                .potentials(potentials)
                .build();
    }

    private FixtureContext contextWithGoalStats(
            double homeScored, double awayScored, double homeConceded, double awayConceded,
            double homeOver25, double awayOver25) {
        int matches = 10;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .matchesPlayedHome(matches)
                .matchesPlayedAway(matches)
                .seasonGoalsHome((int) (homeScored * matches))
                .seasonGoalsAway((int) (homeScored * 0.8 * matches))
                .seasonConcededHome((int) (homeConceded * matches))
                .seasonConcededAway((int) (homeConceded * 1.2 * matches))
                .seasonOver25PercentageOverall(homeOver25)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .matchesPlayedHome(matches)
                .matchesPlayedAway(matches)
                .seasonGoalsHome((int) (awayScored * 1.1 * matches))
                .seasonGoalsAway((int) (awayScored * matches))
                .seasonConcededHome((int) (awayConceded * 0.9 * matches))
                .seasonConcededAway((int) (awayConceded * matches))
                .seasonOver25PercentageOverall(awayOver25)
                .build();

        FixtureOdds odds = FixtureOdds.builder()
                .fixtureId(1000L)
                .oddsFtOver25(1.85)
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(1000L)
                .o25Potential(70.0)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .odds(odds)
                .potentials(potentials)
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
