package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BttsRecommendationEngineTest {

    private BttsRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new BttsRecommendationEngine();
    }

    @Test
    void getType_returnsBtts() {
        assertThat(engine.getType()).isEqualTo(RecommendationType.BTTS);
    }

    @Test
    void analyze_withHighBttsTeams_returnsRecommendation() {
        FixtureContext context = createContextWithBttsStats(90.0, 90.0);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(RecommendationType.BTTS);
        assertThat(result.get().getConfidence()).isIn(ConfidenceLevel.STRONG, ConfidenceLevel.MODERATE);
        assertThat(result.get().getMarket()).isEqualTo("BTTS Yes");
        assertThat(result.get().getScore()).isGreaterThan(65.0);
    }

    @Test
    void analyze_withModerateBttsTeams_returnsModerateRecommendation() {
        FixtureContext context = createContextWithBttsStats(75.0, 75.0);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getConfidence()).isIn(ConfidenceLevel.STRONG, ConfidenceLevel.MODERATE);
    }

    @Test
    void analyze_withLowBttsTeams_returnsEmpty() {
        FixtureContext context = createContextWithBttsStats(30.0, 35.0);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isEmpty();
    }

    @Test
    void analyze_withHighFailedToScore_returnsEmpty() {
        FixtureContext context = createContextWithFailedToScore(50.0, 45.0);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isEmpty();
    }

    @Test
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
    void analyze_includesCorrectFactors() {
        FixtureContext context = createContextWithBttsStats(80.0, 75.0);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("homeBttsSeasonPct");
        assertThat(result.get().getFactors()).containsKey("awayBttsSeasonPct");
        assertThat(result.get().getFactors()).containsKey("calculatedScore");
    }

    @Test
    void isApplicable_withCompleteData_returnsTrue() {
        FixtureContext context = createContextWithBttsStats(70.0, 70.0);

        assertThat(engine.isApplicable(context)).isTrue();
    }

    @Test
    void isApplicable_withMissingStats_returnsFalse() {
        FixtureContext context = FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home"))
                .awayTeam(createTeam(2L, "Away"))
                .build();

        assertThat(engine.isApplicable(context)).isFalse();
    }

    private FixtureContext createContextWithBttsStats(double homeBtts, double awayBtts) {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(20)
                .seasonBttsPercentageHome(homeBtts)
                .seasonBttsPercentageAway(homeBtts - 5)
                .seasonFailedToScoreOverall(3)
                .seasonFailedToScoreHome(1)
                .seasonFailedToScoreAway(2)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(20)
                .seasonBttsPercentageHome(awayBtts - 5)
                .seasonBttsPercentageAway(awayBtts)
                .seasonFailedToScoreOverall(4)
                .seasonFailedToScoreHome(2)
                .seasonFailedToScoreAway(2)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .potentials(createPotentials(70.0))
                .build();
    }

    private FixtureContext createContextWithFailedToScore(double homeFts, double awayFts) {
        int homeMatches = 20;
        int awayMatches = 20;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(homeMatches)
                .seasonBttsPercentageHome(60.0)
                .seasonFailedToScoreOverall((int) (homeMatches * homeFts / 100))
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(awayMatches)
                .seasonBttsPercentageAway(60.0)
                .seasonFailedToScoreOverall((int) (awayMatches * awayFts / 100))
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

    private FixturePotentials createPotentials(double bttsPotential) {
        return FixturePotentials.builder()
                .fixtureId(1000L)
                .bttsPotential(bttsPotential)
                .build();
    }
}
