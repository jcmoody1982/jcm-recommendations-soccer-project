package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FormMismatchRecommendationEngineTest {

    private FormMismatchRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new FormMismatchRecommendationEngine();
    }

    @Test
    @DisplayName("getType returns WINNING_FORM_MISMATCH")
    void getType_returnsWinningFormMismatch() {
        assertThat(engine.getType()).isEqualTo(RecommendationType.WINNING_FORM_MISMATCH);
    }

    @Test
    @DisplayName("analyze returns empty when no recent form data")
    void analyze_withNoForm_returnsEmpty() {
        FixtureContext context = createContextWithoutForm();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("analyze detects winning form mismatch for hot team")
    void analyze_withHotTeam_returnsWinningMismatch() {
        FixtureContext context = createHotTeamContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(RecommendationType.WINNING_FORM_MISMATCH);
        assertThat(result.get().getDescription()).contains("Hot streak");
    }

    @Test
    @DisplayName("analyze detects losing form mismatch for cold team")
    void analyze_withColdTeam_returnsLosingMismatch() {
        FixtureContext context = createColdTeamContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(RecommendationType.LOSING_FORM_MISMATCH);
        assertThat(result.get().getDescription()).contains("Cold streak");
    }

    @Test
    @DisplayName("analyze applies home/away context weighting")
    void analyze_appliesHomeAwayContextWeighting() {
        FixtureContext context = createHotTeamContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("homeAwayContextMultiplier");
        assertThat(result.get().getFactors()).containsKey("playingAtStrength");
    }

    @Test
    @DisplayName("analyze tracks conceded delta")
    void analyze_tracksConcededDelta() {
        FixtureContext context = createHotTeamContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("concededDelta");
    }

    @Test
    @DisplayName("analyze detects scoring trend")
    void analyze_detectsScoringTrend() {
        FixtureContext context = createScoringTrendContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("scoringTrendUp")).isEqualTo(true);
        assertThat(result.get().getDescription()).contains("scoring trending up");
    }

    @Test
    @DisplayName("analyze detects defensive trend")
    void analyze_detectsDefensiveTrend() {
        FixtureContext context = createDefensiveTrendContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("defensiveTrendUp")).isEqualTo(true);
        assertThat(result.get().getDescription()).contains("defense improving");
    }

    @Test
    @DisplayName("analyze flags xG regression risk")
    void analyze_flagsXgRegressionRisk() {
        FixtureContext context = createXgRegressionRiskContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("xgRegressionRisk")).isEqualTo(true);
        assertThat(result.get().getDescription()).contains("xG regression risk");
    }

    @Test
    @DisplayName("analyze adds streak bonus")
    void analyze_addsStreakBonus() {
        FixtureContext context = createWinningStreakContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("hasWinningStreak")).isEqualTo(true);
    }

    @Test
    @DisplayName("analyze tracks momentum indicators")
    void analyze_tracksMomentumIndicators() {
        FixtureContext context = createHotTeamContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("positiveMomentumIndicators");
    }

    @Test
    @DisplayName("analyze tracks risk flags")
    void analyze_tracksRiskFlags() {
        FixtureContext context = createXgRegressionRiskContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("riskFlags");
        @SuppressWarnings("unchecked")
        List<String> risks = (List<String>) result.get().getFactors().get("riskFlags");
        assertThat(risks).isNotEmpty();
    }

    @Test
    @DisplayName("analyze returns empty for small mismatch")
    void analyze_withSmallMismatch_returnsEmpty() {
        FixtureContext context = createSmallMismatchContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isEmpty();
    }

    // Helper methods to create test contexts

    private FixtureContext createContextWithoutForm() {
        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home"))
                .awayTeam(createTeam(2L, "Away"))
                .homeTeamStats(createSeasonStats(1L))
                .awayTeamStats(createSeasonStats(2L))
                .build();
    }

    private FixtureContext createHotTeamContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(20)
                .ppgHome(1.2)
                .ppgAway(0.8)
                .seasonGoalsHome(15)
                .seasonGoalsAway(10)
                .seasonConcededHome(12)
                .seasonConcededAway(15)
                .seasonWinsOverall(8)
                .seasonCleanSheetsOverall(4)
                .build();

        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .ppgHome(2.4)  // Much better than season
                .ppgAway(1.5)
                .ppgOverall(2.0)
                .scoredAvgHome(2.5)  // Much better
                .scoredAvgAway(1.5)
                .scoredAvgOverall(2.0)
                .concededAvgHome(0.5)  // Much better
                .concededAvgAway(1.0)
                .concededAvgOverall(0.75)
                .winsOverall(4)
                .lossesOverall(0)
                .cleanSheetsOverall(3)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Hot Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(createSeasonStats(2L))
                .homeTeamForm(homeForm)
                .awayTeamForm(createNeutralForm(2L))
                .build();
    }

    private FixtureContext createColdTeamContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(20)
                .ppgHome(2.0)  // Good season
                .ppgAway(1.5)
                .seasonGoalsHome(25)
                .seasonGoalsAway(15)
                .seasonConcededHome(8)
                .seasonConcededAway(12)
                .seasonWinsOverall(12)
                .seasonCleanSheetsOverall(6)
                .build();

        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .ppgHome(0.6)  // Much worse than season
                .ppgAway(0.4)
                .ppgOverall(0.5)
                .scoredAvgHome(0.8)  // Much worse
                .scoredAvgAway(0.5)
                .scoredAvgOverall(0.65)
                .concededAvgHome(2.0)  // Much worse
                .concededAvgAway(2.5)
                .concededAvgOverall(2.25)
                .winsOverall(0)
                .lossesOverall(4)
                .cleanSheetsOverall(0)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Cold Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(createSeasonStats(2L))
                .homeTeamForm(homeForm)
                .awayTeamForm(createNeutralForm(2L))
                .build();
    }

    private FixtureContext createScoringTrendContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(20)
                .ppgHome(1.5)
                .ppgAway(1.0)
                .seasonGoalsHome(18)
                .seasonGoalsAway(12)
                .seasonConcededHome(10)
                .seasonConcededAway(14)
                .seasonWinsOverall(9)
                .seasonCleanSheetsOverall(4)
                .build();

        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .ppgHome(2.2)
                .ppgAway(1.4)
                .ppgOverall(1.8)
                .scoredAvgHome(2.8)  // High home scoring
                .scoredAvgAway(1.5)
                .scoredAvgOverall(2.0)  // Lower overall - trend is home specific
                .concededAvgHome(0.8)
                .concededAvgAway(1.2)
                .concededAvgOverall(1.0)
                .winsOverall(3)
                .lossesOverall(1)
                .cleanSheetsOverall(2)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Scoring Trend Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(createSeasonStats(2L))
                .homeTeamForm(homeForm)
                .awayTeamForm(createNeutralForm(2L))
                .build();
    }

    private FixtureContext createDefensiveTrendContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(20)
                .ppgHome(1.5)
                .ppgAway(1.0)
                .seasonGoalsHome(18)
                .seasonGoalsAway(12)
                .seasonConcededHome(15)
                .seasonConcededAway(18)
                .seasonWinsOverall(8)
                .seasonCleanSheetsOverall(3)
                .build();

        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .ppgHome(2.0)
                .ppgAway(1.2)
                .ppgOverall(1.6)
                .scoredAvgHome(1.8)
                .scoredAvgAway(1.2)
                .scoredAvgOverall(1.5)
                .concededAvgHome(0.4)  // Much better at home
                .concededAvgAway(1.2)
                .concededAvgOverall(0.9)  // Higher overall - home defense improving
                .winsOverall(3)
                .lossesOverall(1)
                .cleanSheetsOverall(3)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Defensive Trend Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(createSeasonStats(2L))
                .homeTeamForm(homeForm)
                .awayTeamForm(createNeutralForm(2L))
                .build();
    }

    private FixtureContext createXgRegressionRiskContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(20)
                .ppgHome(1.5)
                .ppgAway(1.0)
                .seasonGoalsHome(18)
                .seasonGoalsAway(12)
                .seasonConcededHome(10)
                .seasonConcededAway(14)
                .seasonWinsOverall(9)
                .seasonCleanSheetsOverall(4)
                .xgForAvgHome(1.2)  // Low xG
                .xgForAvgAway(0.9)
                .build();

        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .ppgHome(2.4)
                .ppgAway(1.6)
                .ppgOverall(2.0)
                .scoredAvgHome(2.5)  // High actual goals >> xG (regression risk)
                .scoredAvgAway(1.8)
                .scoredAvgOverall(2.1)
                .concededAvgHome(0.6)
                .concededAvgAway(1.0)
                .concededAvgOverall(0.8)
                .winsOverall(4)
                .lossesOverall(0)
                .cleanSheetsOverall(2)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "xG Risk Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(createSeasonStats(2L))
                .homeTeamForm(homeForm)
                .awayTeamForm(createNeutralForm(2L))
                .build();
    }

    private FixtureContext createWinningStreakContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(20)
                .ppgHome(1.4)
                .ppgAway(1.0)
                .seasonGoalsHome(16)
                .seasonGoalsAway(12)
                .seasonConcededHome(12)
                .seasonConcededAway(14)
                .seasonWinsOverall(8)
                .seasonCleanSheetsOverall(4)
                .build();

        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .ppgHome(2.2)
                .ppgAway(1.6)
                .ppgOverall(1.9)
                .scoredAvgHome(2.2)
                .scoredAvgAway(1.6)
                .scoredAvgOverall(1.9)
                .concededAvgHome(0.6)
                .concededAvgAway(0.8)
                .concededAvgOverall(0.7)
                .winsOverall(4)  // 4 wins = winning streak
                .lossesOverall(0)
                .cleanSheetsOverall(2)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Streak Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(createSeasonStats(2L))
                .homeTeamForm(homeForm)
                .awayTeamForm(createNeutralForm(2L))
                .build();
    }

    private FixtureContext createSmallMismatchContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(20)
                .ppgHome(1.5)
                .ppgAway(1.0)
                .seasonGoalsHome(18)
                .seasonGoalsAway(12)
                .seasonConcededHome(12)
                .seasonConcededAway(14)
                .seasonWinsOverall(8)
                .seasonCleanSheetsOverall(4)
                .build();

        // Form nearly identical to season - no significant mismatch
        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .ppgHome(1.5)  // Same as season
                .ppgAway(1.0)
                .ppgOverall(1.25)
                .scoredAvgHome(1.8)  // Same as season avg (18/10 = 1.8)
                .scoredAvgAway(1.2)
                .scoredAvgOverall(1.5)
                .concededAvgHome(1.2)  // Same as season avg (12/10 = 1.2)
                .concededAvgAway(1.4)
                .concededAvgOverall(1.3)
                .winsOverall(2)  // 40% win rate, same as 8/20 season
                .lossesOverall(2)
                .cleanSheetsOverall(1)  // 20%, same as 4/20 season
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Neutral Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(createSeasonStats(2L))
                .homeTeamForm(homeForm)
                .awayTeamForm(createNeutralForm(2L))
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

    private TeamSeasonStats createSeasonStats(Long teamId) {
        return TeamSeasonStats.builder()
                .teamId(teamId)
                .seasonId(100L)
                .matchesPlayed(20)
                .ppgHome(1.5)
                .ppgAway(1.0)
                .seasonGoalsHome(18)
                .seasonGoalsAway(12)
                .seasonConcededHome(12)
                .seasonConcededAway(15)
                .seasonWinsOverall(8)
                .seasonCleanSheetsOverall(4)
                .build();
    }

    private TeamRecentForm createNeutralForm(Long teamId) {
        return TeamRecentForm.builder()
                .teamId(teamId)
                .ppgHome(1.5)
                .ppgAway(1.0)
                .ppgOverall(1.25)
                .scoredAvgHome(1.5)
                .scoredAvgAway(1.0)
                .scoredAvgOverall(1.25)
                .concededAvgHome(1.2)
                .concededAvgAway(1.4)
                .concededAvgOverall(1.3)
                .winsOverall(2)
                .lossesOverall(1)
                .cleanSheetsOverall(1)
                .build();
    }
}
