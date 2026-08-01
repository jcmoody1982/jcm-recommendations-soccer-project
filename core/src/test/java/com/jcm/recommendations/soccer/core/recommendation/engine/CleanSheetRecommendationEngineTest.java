package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CleanSheetRecommendationEngineTest {

    private CleanSheetRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new CleanSheetRecommendationEngine();
    }

    @Test
    @DisplayName("getType returns CLEAN_SHEET")
    void getType_returnsCleanSheet() {
        assertThat(engine.getType()).isEqualTo(RecommendationType.CLEAN_SHEET);
    }

    @Test
    @DisplayName("analyze with strong defensive team returns recommendation")
    void analyze_withStrongDefensiveTeam_returnsRecommendation() {
        FixtureContext context = createContextWithCleanSheetStats(60, 40, 10, 30);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(RecommendationType.CLEAN_SHEET);
        assertThat(result.get().getMarket()).contains("Clean Sheet");
    }

    @Test
    @DisplayName("analyze picks the team with better defensive record")
    void analyze_picksBetterDefensiveTeam() {
        FixtureContext context = createContextWithCleanSheetStats(70, 30, 10, 20);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getMarket()).contains("Home Team");
    }

    @Test
    @DisplayName("analyze with poor defensive teams returns empty")
    void analyze_withPoorDefensiveTeams_returnsEmpty() {
        FixtureContext context = createContextWithCleanSheetStats(10, 15, 5, 10);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("analyze includes team factors")
    void analyze_includesTeamFactors() {
        FixtureContext context = createContextWithCleanSheetStats(55, 35, 20, 25);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("team");
        assertThat(result.get().getFactors()).containsKey("isHomeTeam");
        assertThat(result.get().getFactors()).containsKey("teamCleanSheetSeasonPct");
    }

    @Test
    @DisplayName("analyze considers opponent failed to score rate")
    void analyze_considersOpponentFailedToScoreRate() {
        FixtureContext context = createContextWithCleanSheetStats(50, 30, 40, 20);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("opponentFailedToScorePct");
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

    @Test
    @DisplayName("isApplicable returns true with complete data")
    void isApplicable_withCompleteData_returnsTrue() {
        FixtureContext context = createContextWithCleanSheetStats(50, 30, 20, 25);

        assertThat(engine.isApplicable(context)).isTrue();
    }

    private FixtureContext createContextWithCleanSheetStats(int homeCleanSheetPct, int awayCleanSheetPct,
            int homeFtsPct, int awayFtsPct) {
        int matches = 10;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .seasonCleanSheetsHome(homeCleanSheetPct * matches / 100)
                .seasonCleanSheetsAway((homeCleanSheetPct - 10) * matches / 100)
                .seasonCleanSheetsOverall((homeCleanSheetPct - 5) * matches / 100)
                .seasonConcededHome(8)
                .seasonConcededAway(12)
                .seasonFailedToScoreHome(homeFtsPct * matches / 100)
                .seasonFailedToScoreAway((homeFtsPct + 5) * matches / 100)
                .seasonFailedToScoreOverall((homeFtsPct + 2) * matches / 100)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .seasonCleanSheetsHome((awayCleanSheetPct + 5) * matches / 100)
                .seasonCleanSheetsAway(awayCleanSheetPct * matches / 100)
                .seasonCleanSheetsOverall((awayCleanSheetPct + 2) * matches / 100)
                .seasonConcededHome(10)
                .seasonConcededAway(14)
                .seasonFailedToScoreHome((awayFtsPct - 5) * matches / 100)
                .seasonFailedToScoreAway(awayFtsPct * matches / 100)
                .seasonFailedToScoreOverall((awayFtsPct - 2) * matches / 100)
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
