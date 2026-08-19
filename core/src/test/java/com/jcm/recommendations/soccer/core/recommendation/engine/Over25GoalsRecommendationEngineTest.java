package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.domain.Fixture;
import com.jcm.recommendations.soccer.domain.FixtureOdds;
import com.jcm.recommendations.soccer.domain.FixturePotentials;
import com.jcm.recommendations.soccer.domain.Team;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class Over25GoalsRecommendationEngineTest {

    private Over25GoalsRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new Over25GoalsRecommendationEngine();
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
        assertThat(result.get().getFactors()).containsKeys("expectedGoals", "over25PctHome", "over25PctAway", "apiO25Potential");
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

    private FixtureContext veryHighScoringContext() {
        int matches = 10;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
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
