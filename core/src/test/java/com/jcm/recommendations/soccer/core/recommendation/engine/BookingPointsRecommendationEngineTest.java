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
    @DisplayName("analyze returns Over recommendation for high cards with edge")
    void analyze_withHighCardsTeams_returnsOverRecommendation() {
        FixtureContext context = createVeryHighCardsContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(RecommendationType.BOOKING_POINTS);
        assertThat(result.get().getMarket()).contains("Over");
        assertThat(result.get().getFactors().get("cardsPotentialIsCardCount")).isEqualTo(true);
        assertThat((Double) result.get().getFactors().get("marketEdge")).isGreaterThanOrEqualTo(8.0);
    }

    @Test
    @DisplayName("analyze returns Under recommendation for low cards with edge")
    void analyze_withLowCardsTeams_returnsUnderRecommendation() {
        FixtureContext context = createLowCardsContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getMarket()).contains("Under");
    }

    @Test
    @DisplayName("analyze sums home and away card avgs into match total before ×10")
    void analyze_sumsTeamCardsForMatchTotal() {
        FixtureContext context = createLowCardsContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("matchCardsSeasonTotal")).isEqualTo(1.9);
        assertThat(result.get().getFactors().get("matchCardsSeasonPoints")).isEqualTo(19.0);
        assertThat((Double) result.get().getFactors().get("expectedBookingPoints")).isGreaterThan(15.0);
    }

    @Test
    @DisplayName("analyze returns empty when expected points lack edge vs line")
    void analyze_midRange_returnsEmpty() {
        FixtureContext context = createMidRangeCardsContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("analyze treats cards_potential as card count × 10")
    void analyze_convertsCardsPotentialAsCardCount() {
        FixtureContext context = createVeryHighCardsContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("apiCardsPotential")).isEqualTo(6.5);
        assertThat(result.get().getFactors().get("apiCardsPotentialAsPoints")).isEqualTo(65.0);
    }

    @Test
    @DisplayName("analyze omits missing cards_potential and renormalizes")
    void analyze_missingApiPotential_renormalizes() {
        FixtureContext context = createVeryHighCardsContextWithoutApi();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).doesNotContainKey("apiCardsPotential");
        assertThat(result.get().getFactors().get("missingDataRenormalized")).isEqualTo(true);
    }

    @Test
    @DisplayName("analyze without referee never returns STRONG")
    void analyze_withoutReferee_capsAtModerate() {
        FixtureContext context = createVeryHighCardsContextWithoutReferee();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getConfidence()).isEqualTo(ConfidenceLevel.MODERATE);
        assertThat(result.get().getFactors().get("refereeDataAvailable")).isEqualTo(false);
        assertThat(result.get().getFactors().get("refereeRequiredForStrong")).isEqualTo(true);
    }

    @Test
    @DisplayName("analyze with referee and large edge can be STRONG")
    void analyze_withRefereeAndLargeEdge_canBeStrong() {
        FixtureContext context = createExtremeHighCardsContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getConfidence()).isEqualTo(ConfidenceLevel.STRONG);
        assertThat(result.get().getFactors().get("refereeDataAvailable")).isEqualTo(true);
        assertThat((Double) result.get().getFactors().get("refereeReliability")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("analyze tracks formDataAvailable correctly")
    void analyze_tracksFormDataAvailable() {
        FixtureContext contextWithForm = createVeryHighCardsContextWithForm();
        FixtureContext contextWithoutForm = createVeryHighCardsContext();

        Optional<Recommendation> resultWithForm = engine.analyze(contextWithForm);
        Optional<Recommendation> resultWithoutForm = engine.analyze(contextWithoutForm);

        assertThat(resultWithForm).isPresent();
        assertThat(resultWithoutForm).isPresent();
        assertThat(resultWithForm.get().getFactors().get("formDataAvailable")).isEqualTo(true);
        assertThat(resultWithoutForm.get().getFactors().get("formDataAvailable")).isEqualTo(false);
    }

    @Test
    @DisplayName("analyze applies graded high-cards boost capped with strictness")
    void analyze_withHighCardsTeams_appliesGradedBoost() {
        FixtureContext context = createVeryHighCardsContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("highCardsBoostApplied")).isEqualTo(true);
        Double boost = (Double) result.get().getFactors().get("highCardsBoostAmount");
        assertThat(boost).isGreaterThan(0.0).isLessThanOrEqualTo(3.0);
        assertThat(result.get().getFactors()).containsKey("maxCombinedBoost");
    }

    @Test
    @DisplayName("analyze with strict referee applies graded strictness boost")
    void analyze_withStrictReferee_appliesStrictnessBoost() {
        FixtureContext context = createContextWithStrictReferee();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("refereeStrictnessBoostApplied")).isEqualTo(true);
        Double boost = (Double) result.get().getFactors().get("refereeStrictnessBoostAmount");
        assertThat(boost).isGreaterThan(0.0).isLessThanOrEqualTo(3.0);
    }

    @Test
    @DisplayName("analyze uses additive intensity points not multiplier")
    void analyze_tracksMatchIntensityPoints() {
        FixtureContext context = createClosePositionsHighCardsContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("matchIntensityPoints")).isEqualTo(4.0);
        assertThat(result.get().getFactors().get("positionDifference")).isEqualTo(2);
        assertThat(result.get().getFactors()).doesNotContainKey("matchIntensityFactor");
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
        FixtureContext context = createVeryHighCardsContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("expectedBookingPoints");
        assertThat(result.get().getFactors()).containsKey("homeCardsSeasonAvg");
        assertThat(result.get().getFactors()).containsKey("awayCardsSeasonAvg");
        assertThat(result.get().getFactors()).containsKey("marketLine");
    }

    private FixtureContext createVeryHighCardsContext() {
        return highCardsBuilder(3.2, 3.4, 5.5, 6.5, 3, 4, 70.0, 20)
                .build();
    }

    private FixtureContext createVeryHighCardsContextWithoutApi() {
        // Slightly higher season/ref cards so edge still clears without API signal
        return highCardsBuilder(3.5, 3.6, 5.8, null, 3, 4, 72.0, 20)
                .build();
    }

    private FixtureContext createVeryHighCardsContextWithoutReferee() {
        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(teamStats(1L, 3.5, 3, 4))
                .awayTeamStats(teamStats(2L, 3.6, 4, 5))
                .potentials(potentials(7.0))
                .build();
    }

    private FixtureContext createExtremeHighCardsContext() {
        return highCardsBuilder(3.8, 4.0, 6.0, 7.0, 2, 3, 80.0, 25)
                .build();
    }

    private FixtureContext createVeryHighCardsContextWithForm() {
        return highCardsBuilder(3.2, 3.4, 5.5, 6.5, 3, 4, 70.0, 20)
                .homeTeamForm(TeamRecentForm.builder().teamId(1L).cardsAvgHome(3.5).build())
                .awayTeamForm(TeamRecentForm.builder().teamId(2L).cardsAvgAway(3.6).build())
                .build();
    }

    private FixtureContext createLowCardsContext() {
        return highCardsBuilder(0.9, 1.0, 2.0, 2.0, 10, 18, 25.0, 12)
                .build();
    }

    private FixtureContext createMidRangeCardsContext() {
        // Target ~36 expected — no ±8 edge vs 30/40/50 lines
        return highCardsBuilder(2.5, 2.5, 4.5, 4.5, 10, 18, 50.0, 15)
                .build();
    }

    private FixtureContext createContextWithStrictReferee() {
        return highCardsBuilder(3.0, 3.1, 5.5, 6.0, 5, 8, 75.0, 18)
                .build();
    }

    private FixtureContext createClosePositionsHighCardsContext() {
        return highCardsBuilder(3.2, 3.3, 5.2, 6.2, 5, 7, 55.0, 15)
                .build();
    }

    private FixtureContext.FixtureContextBuilder highCardsBuilder(
            double homeCardsHome,
            double awayCardsAway,
            double refCardsPerMatch,
            Double apiCardsCount,
            int homePos,
            int awayPos,
            double o35Pct,
            int appearances) {
        var builder = FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(teamStats(1L, homeCardsHome, homePos, awayPos))
                .awayTeamStats(teamStats(2L, awayCardsAway, awayPos, homePos))
                .refereeStats(RefereeStats.builder()
                        .refereeId(100L)
                        .seasonId(100L)
                        .appearancesOverall(appearances)
                        .cardsPerMatchOverall(refCardsPerMatch)
                        .yellowCardsOverall((int) (refCardsPerMatch * appearances * 0.9))
                        .redCardsOverall(Math.max(1, appearances / 5))
                        .over35CardsPercentageOverall(o35Pct)
                        .build());
        if (apiCardsCount != null) {
            builder.potentials(potentials(apiCardsCount));
        }
        return builder;
    }

    private TeamSeasonStats teamStats(long teamId, double venueCards, int position, int otherPos) {
        // home team uses cardsAvgHome; away uses cardsAvgAway — set both sensibly
        boolean homeSide = teamId == 1L;
        return TeamSeasonStats.builder()
                .teamId(teamId)
                .seasonId(100L)
                .matchesPlayed(20)
                .cardsAvgHome(homeSide ? venueCards : venueCards - 0.2)
                .cardsAvgAway(homeSide ? venueCards - 0.2 : venueCards)
                .position(position)
                .build();
    }

    private FixturePotentials potentials(double cardsCount) {
        return FixturePotentials.builder()
                .fixtureId(1000L)
                .cardsPotential(cardsCount)
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
