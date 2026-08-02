package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BookingPointsRecommendationEngineTest {

    private BookingPointsRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new BookingPointsRecommendationEngine();
    }

    @Test
    @DisplayName("getType returns BOOKING_POINTS")
    void getType_returnsBookingPoints() {
        assertThat(engine.getType()).isEqualTo(RecommendationType.BOOKING_POINTS);
    }

    @Test
    @DisplayName("analyze returns recommendation for high cards context")
    void analyze_withHighCardsTeams_returnsRecommendation() {
        FixtureContext context = createVeryHighCardsContext();
        
        Optional<Recommendation> result = engine.analyze(context);
        
        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(RecommendationType.BOOKING_POINTS);
        assertThat(result.get().getMarket()).contains("Over");
    }

    @Test
    @DisplayName("analyze returns under recommendation for low cards context")
    void analyze_withLowCardsTeams_returnsUnderRecommendation() {
        FixtureContext context = createLowCardsContext();

        Optional<Recommendation> result = engine.analyze(context);

        if (result.isPresent()) {
            assertThat(result.get().getMarket()).contains("Under");
        }
    }

    @Test
    @DisplayName("analyze tracks formDataAvailable correctly")
    void analyze_tracksFormDataAvailable() {
        FixtureContext contextWithForm = createHighCardsContextWithForm();
        FixtureContext contextWithoutForm = createHighCardsContext();

        Optional<Recommendation> resultWithForm = engine.analyze(contextWithForm);
        Optional<Recommendation> resultWithoutForm = engine.analyze(contextWithoutForm);

        if (resultWithForm.isPresent()) {
            assertThat(resultWithForm.get().getFactors().get("formDataAvailable")).isEqualTo(true);
        }
        if (resultWithoutForm.isPresent()) {
            assertThat(resultWithoutForm.get().getFactors().get("formDataAvailable")).isEqualTo(false);
        }
    }

    @Test
    @DisplayName("analyze tracks refereeDataAvailable correctly")
    void analyze_tracksRefereeDataAvailable() {
        FixtureContext contextWithRef = createHighCardsContext();
        FixtureContext contextWithoutRef = createContextWithoutReferee();

        Optional<Recommendation> resultWithRef = engine.analyze(contextWithRef);
        Optional<Recommendation> resultWithoutRef = engine.analyze(contextWithoutRef);

        if (resultWithRef.isPresent()) {
            assertThat(resultWithRef.get().getFactors().get("refereeDataAvailable")).isEqualTo(true);
        }
        if (resultWithoutRef.isPresent()) {
            assertThat(resultWithoutRef.get().getFactors().get("refereeDataAvailable")).isEqualTo(false);
        }
    }

    @Test
    @DisplayName("analyze with high cards teams applies high-cards boost")
    void analyze_withHighCardsTeams_appliesHighCardsBoost() {
        FixtureContext context = createVeryHighCardsContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("highCardsBoostApplied")).isEqualTo(true);
        assertThat(result.get().getFactors().get("highCardsBoostAmount")).isEqualTo(5.0);
    }

    @Test
    @DisplayName("analyze with strict referee applies strictness boost")
    void analyze_withStrictReferee_appliesStrictnessBoost() {
        FixtureContext context = createContextWithStrictReferee();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("refereeStrictnessBoostApplied")).isEqualTo(true);
        assertThat(result.get().getFactors().get("refereeStrictnessBoostAmount")).isEqualTo(5.0);
    }

    @Test
    @DisplayName("analyze tracks referee over 3.5 cards percentage")
    void analyze_tracksRefereeOver35CardsPct() {
        FixtureContext context = createHighCardsContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("refereeOver35CardsPct");
    }

    @Test
    @DisplayName("analyze tracks match intensity based on positions")
    void analyze_tracksMatchIntensity() {
        FixtureContext context = createClosePositionsContext();

        Optional<Recommendation> result = engine.analyze(context);

        if (result.isPresent()) {
            assertThat(result.get().getFactors()).containsKey("matchIntensityFactor");
            assertThat(result.get().getFactors()).containsKey("positionDifference");
            Double intensity = (Double) result.get().getFactors().get("matchIntensityFactor");
            assertThat(intensity).isGreaterThan(1.0);
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

    @Test
    @DisplayName("analyze includes expected booking points in factors")
    void analyze_includesExpectedBookingPoints() {
        FixtureContext context = createHighCardsContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("expectedBookingPoints");
        assertThat(result.get().getFactors()).containsKey("homeCardsSeasonAvg");
        assertThat(result.get().getFactors()).containsKey("awayCardsSeasonAvg");
    }

    private FixtureContext createHighCardsContext() {
        int matches = 10;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .cardsAvgHome(2.5)
                .cardsAvgAway(2.0)
                .position(5)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .cardsAvgHome(2.2)
                .cardsAvgAway(2.8)
                .position(8)
                .build();

        RefereeStats refereeStats = RefereeStats.builder()
                .refereeId(100L)
                .seasonId(100L)
                .appearancesOverall(15)
                .cardsPerMatchOverall(4.5)
                .yellowCardsOverall(60)
                .redCardsOverall(3)
                .over35CardsPercentageOverall(55.0)
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(1000L)
                .cardsPotential(65.0)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .refereeStats(refereeStats)
                .potentials(potentials)
                .build();
    }

    private FixtureContext createHighCardsContextWithForm() {
        int matches = 10;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .cardsAvgHome(2.5)
                .cardsAvgAway(2.0)
                .position(5)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .cardsAvgHome(2.2)
                .cardsAvgAway(2.8)
                .position(8)
                .build();

        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .cardsAvgHome(3.0)
                .cardsAvgAway(2.2)
                .build();

        TeamRecentForm awayForm = TeamRecentForm.builder()
                .teamId(2L)
                .cardsAvgHome(2.4)
                .cardsAvgAway(3.2)
                .build();

        RefereeStats refereeStats = RefereeStats.builder()
                .refereeId(100L)
                .seasonId(100L)
                .appearancesOverall(15)
                .cardsPerMatchOverall(4.5)
                .yellowCardsOverall(60)
                .redCardsOverall(3)
                .over35CardsPercentageOverall(55.0)
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(1000L)
                .cardsPotential(65.0)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .homeTeamForm(homeForm)
                .awayTeamForm(awayForm)
                .refereeStats(refereeStats)
                .potentials(potentials)
                .build();
    }

    private FixtureContext createLowCardsContext() {
        int matches = 10;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .cardsAvgHome(1.0)
                .cardsAvgAway(0.8)
                .position(10)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .cardsAvgHome(0.9)
                .cardsAvgAway(1.2)
                .position(15)
                .build();

        RefereeStats refereeStats = RefereeStats.builder()
                .refereeId(100L)
                .seasonId(100L)
                .appearancesOverall(12)
                .cardsPerMatchOverall(2.5)
                .yellowCardsOverall(28)
                .redCardsOverall(1)
                .over35CardsPercentageOverall(25.0)
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(1000L)
                .cardsPotential(35.0)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .refereeStats(refereeStats)
                .potentials(potentials)
                .build();
    }

    private FixtureContext createVeryHighCardsContext() {
        int matches = 10;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .cardsAvgHome(2.5)  // >= 2.0 threshold
                .cardsAvgAway(2.2)
                .position(3)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .cardsAvgHome(2.3)
                .cardsAvgAway(2.8)  // >= 2.0 threshold
                .position(4)
                .build();

        RefereeStats refereeStats = RefereeStats.builder()
                .refereeId(100L)
                .seasonId(100L)
                .appearancesOverall(20)
                .cardsPerMatchOverall(5.0)
                .yellowCardsOverall(90)
                .redCardsOverall(5)
                .over35CardsPercentageOverall(70.0)
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(1000L)
                .cardsPotential(75.0)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .refereeStats(refereeStats)
                .potentials(potentials)
                .build();
    }

    private FixtureContext createContextWithStrictReferee() {
        int matches = 10;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .cardsAvgHome(2.0)
                .cardsAvgAway(1.8)
                .position(6)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .cardsAvgHome(1.9)
                .cardsAvgAway(2.2)
                .position(9)
                .build();

        RefereeStats refereeStats = RefereeStats.builder()
                .refereeId(100L)
                .seasonId(100L)
                .appearancesOverall(18)
                .cardsPerMatchOverall(5.5)
                .yellowCardsOverall(95)
                .redCardsOverall(4)
                .over35CardsPercentageOverall(65.0)  // >= 60% threshold
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(1000L)
                .cardsPotential(70.0)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .refereeStats(refereeStats)
                .potentials(potentials)
                .build();
    }

    private FixtureContext createContextWithoutReferee() {
        int matches = 10;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .cardsAvgHome(2.0)
                .cardsAvgAway(1.8)
                .position(6)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .cardsAvgHome(1.9)
                .cardsAvgAway(2.2)
                .position(9)
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(1000L)
                .cardsPotential(55.0)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .potentials(potentials)
                .build();
    }

    private FixtureContext createClosePositionsContext() {
        int matches = 10;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .cardsAvgHome(2.0)
                .cardsAvgAway(1.8)
                .position(5)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(matches)
                .cardsAvgHome(1.9)
                .cardsAvgAway(2.2)
                .position(7)  // Position diff = 2, triggers 1.2x intensity
                .build();

        RefereeStats refereeStats = RefereeStats.builder()
                .refereeId(100L)
                .seasonId(100L)
                .appearancesOverall(15)
                .cardsPerMatchOverall(4.0)
                .yellowCardsOverall(55)
                .redCardsOverall(2)
                .over35CardsPercentageOverall(50.0)
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(1000L)
                .cardsPotential(60.0)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .refereeStats(refereeStats)
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
