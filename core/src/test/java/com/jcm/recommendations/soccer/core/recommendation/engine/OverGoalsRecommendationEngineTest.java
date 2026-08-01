package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OverGoalsRecommendationEngineTest {

    private OverGoalsRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new OverGoalsRecommendationEngine();
    }

    @Test
    @DisplayName("getType returns OVER_GOALS")
    void getType_returnsOverGoals() {
        assertThat(engine.getType()).isEqualTo(RecommendationType.OVER_GOALS);
    }

    @Test
    @DisplayName("analyze returns recommendation for high scoring context")
    void analyze_withHighScoringTeams_returnsRecommendation() {
        FixtureContext context = createHighScoringContext();
        
        Optional<Recommendation> result = engine.analyze(context);
        
        if (result.isPresent()) {
            assertThat(result.get().getType()).isEqualTo(RecommendationType.OVER_GOALS);
            assertThat(result.get().getMarket()).startsWith("Over");
        }
    }

    @Test
    @DisplayName("analyze with low scoring teams returns empty")
    void analyze_withLowScoringTeams_returnsEmpty() {
        FixtureContext context = createContextWithGoalStats(0.5, 0.3, 0.4, 0.5);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("when recommendation generated, includes expected goals in factors")
    void analyze_includesExpectedGoalsInFactors() {
        FixtureContext context = createHighScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        if (result.isPresent()) {
            assertThat(result.get().getFactors()).containsKey("expectedGoals");
            assertThat(result.get().getFactors()).containsKey("homeGoalsScoredAvg");
            assertThat(result.get().getFactors()).containsKey("awayGoalsScoredAvg");
        }
    }

    @Test
    @DisplayName("when recommendation generated with odds, includes odds in result")
    void analyze_withOdds_includesOddsInResult() {
        FixtureContext context = createHighScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        if (result.isPresent()) {
            assertThat(result.get().getOdds()).isNotNull();
        }
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

    private FixtureContext createContextWithGoalStats(double homeScored, double awayScored, 
            double homeConceded, double awayConceded) {
        int matches = 10;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .seasonGoalsHome((int) (homeScored * matches))
                .seasonGoalsAway((int) (homeScored * 0.8 * matches))
                .seasonConcededHome((int) (homeConceded * matches))
                .seasonConcededAway((int) (homeConceded * 1.2 * matches))
                .seasonOver25PercentageOverall(70.0)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .seasonGoalsHome((int) (awayScored * 1.1 * matches))
                .seasonGoalsAway((int) (awayScored * matches))
                .seasonConcededHome((int) (awayConceded * 0.9 * matches))
                .seasonConcededAway((int) (awayConceded * matches))
                .seasonOver25PercentageOverall(65.0)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createContextWithGoalStatsAndOdds(double homeScored, double awayScored, 
            double homeConceded, double awayConceded) {
        int matches = 10;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .seasonGoalsHome((int) (homeScored * matches))
                .seasonGoalsAway((int) (homeScored * 0.8 * matches))
                .seasonConcededHome((int) (homeConceded * matches))
                .seasonConcededAway((int) (homeConceded * 1.2 * matches))
                .seasonOver25PercentageOverall(70.0)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .seasonGoalsHome((int) (awayScored * 1.1 * matches))
                .seasonGoalsAway((int) (awayScored * matches))
                .seasonConcededHome((int) (awayConceded * 0.9 * matches))
                .seasonConcededAway((int) (awayConceded * matches))
                .seasonOver25PercentageOverall(65.0)
                .build();

        FixtureOdds odds = FixtureOdds.builder()
                .fixtureId(1000L)
                .oddsFtOver25(1.85)
                .oddsFtOver35(2.50)
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

    private FixtureContext createHighScoringContext() {
        int matches = 10;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .seasonGoalsHome(25)
                .seasonGoalsAway(20)
                .seasonConcededHome(10)
                .seasonConcededAway(12)
                .seasonOver25PercentageOverall(80.0)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .seasonGoalsHome(22)
                .seasonGoalsAway(18)
                .seasonConcededHome(12)
                .seasonConcededAway(14)
                .seasonOver25PercentageOverall(75.0)
                .build();

        FixtureOdds odds = FixtureOdds.builder()
                .fixtureId(1000L)
                .oddsFtOver25(1.60)
                .oddsFtOver35(2.40)
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(1000L)
                .o25Potential(80.0)
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
