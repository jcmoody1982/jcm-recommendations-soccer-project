package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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
        assertThat(result.get().getFactors().get("filtersVenueAware")).isEqualTo(true);
        assertThat(result.get().getFactors().get("missingDataRenormalized")).isEqualTo(true);
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

    @Test
    void analyze_withProlificScorers_appliesGoalsBoost() {
        FixtureContext context = createContextWithGoalsData(75.0, 75.0, 18, 12);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("goalsBoostApplied")).isEqualTo(true);
        Double boost = (Double) result.get().getFactors().get("goalsBoostAmount");
        assertThat(boost).isGreaterThan(0.0).isLessThanOrEqualTo(5.0);
        assertThat(result.get().getFactors().get("homeVenueMatches")).isEqualTo(10);
        assertThat(result.get().getFactors().get("awayVenueMatches")).isEqualTo(10);
    }

    @Test
    void analyze_withLowScorers_noGoalsBoost() {
        FixtureContext context = createContextWithGoalsData(75.0, 75.0, 10, 8);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("goalsBoostApplied")).isEqualTo(false);
        assertThat(result.get().getFactors()).doesNotContainKey("goalsBoostAmount");
    }

    @Test
    void analyze_withoutFormData_usesRedistributedWeights() {
        FixtureContext context = createContextWithBttsStats(80.0, 80.0);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("formDataAvailable")).isEqualTo(false);
    }

    @Test
    void analyze_withFormData_tracksFormDataAvailable() {
        FixtureContext context = createContextWithFormData(80.0, 80.0, 85.0, 85.0);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("formDataAvailable")).isEqualTo(true);
        assertThat(result.get().getFactors()).containsKey("homeBttsFormPct");
        assertThat(result.get().getFactors()).containsKey("awayBttsFormPct");
    }

    @Test
    void analyze_withFormData_producesHigherScore() {
        FixtureContext contextWithForm = createContextWithFormData(75.0, 75.0, 85.0, 85.0);
        FixtureContext contextWithoutForm = createContextWithBttsStats(75.0, 75.0);

        Optional<Recommendation> resultWithForm = engine.analyze(contextWithForm);
        Optional<Recommendation> resultWithoutForm = engine.analyze(contextWithoutForm);

        assertThat(resultWithForm).isPresent();
        assertThat(resultWithoutForm).isPresent();
        assertThat(resultWithForm.get().getScore()).isGreaterThan(resultWithoutForm.get().getScore());
    }

    @Test
    void analyze_thinFormSample_shrinksTowardSeason() {
        FixtureContext context = createContextWithThinFormSample(70.0, 70.0, 100.0, 100.0);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("homeFormSampleSize")).isEqualTo(3);
        // Shrunk toward season: (100*3 + 70*6) / (3 + 6) = 720 / 9 = 80
        assertThat((Double) result.get().getFactors().get("homeBttsFormPct"))
                .isCloseTo(80.0, within(0.01));
    }

    @Test
    void analyze_perfectRecords_staysBelowRealisticCeiling() {
        FixtureContext context = createContextWithPerfectBttsRecords();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        // Every input says 100%; the score must still read as a probability, not a certainty.
        assertThat(result.get().getScore()).isLessThan(85.0);
        assertThat(result.get().getFactors().get("ceilingApplied")).isEqualTo(true);
    }

    @Test
    void analyze_fiveFromFiveForm_doesNotReadAsCertainty() {
        FixtureContext context = createContextWithFormData(90.0, 90.0, 100.0, 100.0);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat((Double) result.get().getFactors().get("homeBttsFormPct")).isLessThan(100.0);
        assertThat((Double) result.get().getFactors().get("awayBttsFormPct")).isLessThan(100.0);
    }

    @Test
    void analyze_bothTeamsScoreEstimate_isBelowEitherSideAlone() {
        FixtureContext context = createContextWithBttsStats(80.0, 80.0);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        double bothScore = (Double) result.get().getFactors().get("bothTeamsScoreEstimate");
        double homeScoredPct = (Double) result.get().getFactors().get("homeVenueScoredPct");
        double awayScoredPct = (Double) result.get().getFactors().get("awayVenueScoredPct");
        assertThat(bothScore).isLessThan(Math.min(homeScoredPct, awayScoredPct));
    }

    @Test
    void analyze_tracksGoalsAverages() {
        FixtureContext context = createContextWithGoalsData(75.0, 75.0, 15, 10);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("homeGoalsAvgHome");
        assertThat(result.get().getFactors()).containsKey("awayGoalsAvgAway");
        assertThat((Double) result.get().getFactors().get("homeGoalsAvgHome")).isEqualTo(1.5);
    }

    @Test
    void analyze_withLeakyDefenses_appliesLeakyDefenseBoost() {
        FixtureContext context = createContextWithDefensiveData(75.0, 75.0, 14, 12);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("leakyDefenseBoostApplied")).isEqualTo(true);
        Double boost = (Double) result.get().getFactors().get("leakyDefenseBoostAmount");
        assertThat(boost).isGreaterThan(0.0).isLessThanOrEqualTo(4.0);
    }

    @Test
    void analyze_withSolidDefenses_noLeakyDefenseBoost() {
        FixtureContext context = createContextWithDefensiveData(75.0, 75.0, 8, 6);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("leakyDefenseBoostApplied")).isEqualTo(false);
        assertThat(result.get().getFactors()).doesNotContainKey("leakyDefenseBoostAmount");
    }

    @Test
    void analyze_tracksConcededAverages() {
        FixtureContext context = createContextWithDefensiveData(75.0, 75.0, 12, 10);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("homeConcededAvgHome");
        assertThat(result.get().getFactors()).containsKey("awayConcededAvgAway");
    }

    @Test
    void analyze_withBothBoosts_appliesBoth() {
        FixtureContext context = createContextWithBothBoosts();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("goalsBoostApplied")).isEqualTo(true);
        assertThat(result.get().getFactors().get("leakyDefenseBoostApplied")).isEqualTo(true);
        assertThat(result.get().getFactors()).containsKey("maxCombinedBoost");
    }

    @Test
    void analyze_withHighXgTeams_appliesXgBoost() {
        FixtureContext context = createContextWithXgData(75.0, 75.0, 1.6, 1.5);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("xgDataAvailable")).isEqualTo(true);
        assertThat(result.get().getFactors().get("xgBoostApplied")).isEqualTo(true);
        Double boost = (Double) result.get().getFactors().get("xgBoostAmount");
        assertThat(boost).isGreaterThan(0.0).isLessThanOrEqualTo(3.0);
    }

    @Test
    void analyze_withLowXgTeams_noXgBoost() {
        FixtureContext context = createContextWithXgData(75.0, 75.0, 1.0, 1.0);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("xgDataAvailable")).isEqualTo(true);
        assertThat(result.get().getFactors().get("xgBoostApplied")).isEqualTo(false);
    }

    @Test
    void analyze_withoutXgData_noXgBoost() {
        FixtureContext context = createContextWithBttsStats(75.0, 75.0);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("xgDataAvailable")).isEqualTo(false);
        assertThat(result.get().getFactors().get("xgBoostApplied")).isEqualTo(false);
    }

    @Test
    void analyze_tracksXgData() {
        FixtureContext context = createContextWithXgData(75.0, 75.0, 1.5, 1.3);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("homeXgForAvgHome");
        assertThat(result.get().getFactors()).containsKey("awayXgForAvgAway");
        assertThat(result.get().getFactors().get("homeXgForAvgHome")).isEqualTo(1.5);
        assertThat(result.get().getFactors().get("awayXgForAvgAway")).isEqualTo(1.3);
    }

    @Test
    void analyze_missingApiPotential_renormalizesWithoutFakeFifty() {
        FixtureContext context = createContextWithBttsStats(80.0, 80.0, false);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).doesNotContainKey("apiPotential");
        assertThat(result.get().getFactors().get("missingDataRenormalized")).isEqualTo(true);
    }

    @Test
    void analyze_venueAwareFilter_rejectsHighVenueFts() {
        // Overall FTS looks fine, but venue FTS is terrible
        TeamSeasonStats homeStats = baseHomeStats(70.0)
                .seasonFailedToScoreOverall(4)
                .seasonFailedToScoreHome(6) // 6/10 = 60% venue FTS
                .seasonFailedToScoreAway(0)
                .build();
        TeamSeasonStats awayStats = baseAwayStats(70.0)
                .seasonFailedToScoreOverall(4)
                .seasonFailedToScoreHome(0)
                .seasonFailedToScoreAway(2)
                .build();

        FixtureContext context = FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .potentials(createPotentials(70.0))
                .build();

        assertThat(engine.analyze(context)).isEmpty();
    }

    private FixtureContext createContextWithBttsStats(double homeBtts, double awayBtts) {
        return createContextWithBttsStats(homeBtts, awayBtts, true);
    }

    private FixtureContext createContextWithBttsStats(double homeBtts, double awayBtts, boolean withPotential) {
        var builder = FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(baseHomeStats(homeBtts).build())
                .awayTeamStats(baseAwayStats(awayBtts).build());
        if (withPotential) {
            builder.potentials(createPotentials(70.0));
        }
        return builder.build();
    }

    private FixtureContext createContextWithFailedToScore(double homeFts, double awayFts) {
        int homeMatches = 20;
        int awayMatches = 20;

        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(homeMatches)
                .seasonWinsHome(5).seasonDrawsHome(3).seasonLossesHome(2)
                .seasonWinsAway(4).seasonDrawsAway(3).seasonLossesAway(3)
                .seasonBttsPercentageHome(60.0)
                .seasonFailedToScoreOverall((int) (homeMatches * homeFts / 100))
                .seasonFailedToScoreHome((int) (10 * homeFts / 100))
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(awayMatches)
                .seasonWinsHome(4).seasonDrawsHome(3).seasonLossesHome(3)
                .seasonWinsAway(5).seasonDrawsAway(3).seasonLossesAway(2)
                .seasonBttsPercentageAway(60.0)
                .seasonFailedToScoreOverall((int) (awayMatches * awayFts / 100))
                .seasonFailedToScoreAway((int) (10 * awayFts / 100))
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

    private TeamSeasonStats.TeamSeasonStatsBuilder baseHomeStats(double homeBtts) {
        return TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(20)
                .seasonWinsHome(5).seasonDrawsHome(3).seasonLossesHome(2)
                .seasonWinsAway(4).seasonDrawsAway(3).seasonLossesAway(3)
                .seasonBttsPercentageHome(homeBtts)
                .seasonBttsPercentageAway(homeBtts - 5)
                .seasonFailedToScoreOverall(3)
                .seasonFailedToScoreHome(1)
                .seasonFailedToScoreAway(2)
                .seasonGoalsHome(12)
                .seasonGoalsAway(10)
                .seasonConcededHome(10)
                .seasonConcededAway(11);
    }

    private TeamSeasonStats.TeamSeasonStatsBuilder baseAwayStats(double awayBtts) {
        return TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(20)
                .seasonWinsHome(4).seasonDrawsHome(3).seasonLossesHome(3)
                .seasonWinsAway(5).seasonDrawsAway(3).seasonLossesAway(2)
                .seasonBttsPercentageHome(awayBtts - 5)
                .seasonBttsPercentageAway(awayBtts)
                .seasonFailedToScoreOverall(4)
                .seasonFailedToScoreHome(2)
                .seasonFailedToScoreAway(2)
                .seasonGoalsHome(11)
                .seasonGoalsAway(10)
                .seasonConcededHome(10)
                .seasonConcededAway(10);
    }

    private FixtureContext createContextWithGoalsData(double homeBtts, double awayBtts,
                                                       int homeGoalsHome, int awayGoalsAway) {
        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(baseHomeStats(homeBtts).seasonGoalsHome(homeGoalsHome).build())
                .awayTeamStats(baseAwayStats(awayBtts).seasonGoalsAway(awayGoalsAway).build())
                .potentials(createPotentials(70.0))
                .build();
    }

    private FixtureContext createContextWithDefensiveData(double homeBtts, double awayBtts,
                                                          int homeConcededHome, int awayConcededAway) {
        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(baseHomeStats(homeBtts).seasonConcededHome(homeConcededHome).build())
                .awayTeamStats(baseAwayStats(awayBtts).seasonConcededAway(awayConcededAway).build())
                .potentials(createPotentials(70.0))
                .build();
    }

    private FixtureContext createContextWithXgData(double homeBtts, double awayBtts,
                                                   double homeXgFor, double awayXgFor) {
        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(baseHomeStats(homeBtts)
                        .xgForAvgHome(homeXgFor)
                        .xgAgainstAvgHome(1.0)
                        .build())
                .awayTeamStats(baseAwayStats(awayBtts)
                        .xgForAvgAway(awayXgFor)
                        .xgAgainstAvgAway(1.0)
                        .build())
                .potentials(createPotentials(70.0))
                .build();
    }

    private FixtureContext createContextWithBothBoosts() {
        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(baseHomeStats(80.0)
                        .seasonFailedToScoreOverall(2)
                        .seasonFailedToScoreHome(1)
                        .seasonGoalsHome(18)
                        .seasonConcededHome(14)
                        .build())
                .awayTeamStats(baseAwayStats(80.0)
                        .seasonFailedToScoreOverall(2)
                        .seasonFailedToScoreAway(1)
                        .seasonGoalsAway(12)
                        .seasonConcededAway(12)
                        .build())
                .potentials(createPotentials(75.0))
                .build();
    }

    private FixtureContext createContextWithFormData(double homeBttsSeason, double awayBttsSeason,
                                                      double homeBttsForm, double awayBttsForm) {
        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .winsHome(3).drawsHome(1).lossesHome(1)
                .bttsPercentageHome(homeBttsForm)
                .bttsPercentageAway(homeBttsForm - 10)
                .build();

        TeamRecentForm awayForm = TeamRecentForm.builder()
                .teamId(2L)
                .winsAway(2).drawsAway(2).lossesAway(1)
                .bttsPercentageHome(awayBttsForm - 10)
                .bttsPercentageAway(awayBttsForm)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(baseHomeStats(homeBttsSeason).build())
                .awayTeamStats(baseAwayStats(awayBttsSeason).build())
                .homeTeamForm(homeForm)
                .awayTeamForm(awayForm)
                .potentials(createPotentials(70.0))
                .build();
    }

    /** Every available signal maxed out: 100% season BTTS, 100% form, always scores. */
    private FixtureContext createContextWithPerfectBttsRecords() {
        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .winsHome(3).drawsHome(1).lossesHome(1)
                .bttsPercentageHome(100.0)
                .build();

        TeamRecentForm awayForm = TeamRecentForm.builder()
                .teamId(2L)
                .winsAway(3).drawsAway(1).lossesAway(1)
                .bttsPercentageAway(100.0)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(baseHomeStats(100.0)
                        .seasonFailedToScoreOverall(0)
                        .seasonFailedToScoreHome(0)
                        .seasonFailedToScoreAway(0)
                        .seasonGoalsHome(25)
                        .seasonConcededHome(18)
                        .xgForAvgHome(2.4)
                        .xgAgainstAvgHome(1.8)
                        .build())
                .awayTeamStats(baseAwayStats(100.0)
                        .seasonFailedToScoreOverall(0)
                        .seasonFailedToScoreHome(0)
                        .seasonFailedToScoreAway(0)
                        .seasonGoalsAway(22)
                        .seasonConcededAway(17)
                        .xgForAvgAway(2.2)
                        .xgAgainstAvgAway(1.9)
                        .build())
                .homeTeamForm(homeForm)
                .awayTeamForm(awayForm)
                .potentials(createPotentials(100.0))
                .build();
    }

    private FixtureContext createContextWithThinFormSample(double homeBttsSeason, double awayBttsSeason,
                                                            double homeBttsForm, double awayBttsForm) {
        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .winsHome(2).drawsHome(1).lossesHome(0) // sample 3
                .bttsPercentageHome(homeBttsForm)
                .build();

        TeamRecentForm awayForm = TeamRecentForm.builder()
                .teamId(2L)
                .winsAway(1).drawsAway(1).lossesAway(1) // sample 3
                .bttsPercentageAway(awayBttsForm)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(baseHomeStats(homeBttsSeason).build())
                .awayTeamStats(baseAwayStats(awayBttsSeason).build())
                .homeTeamForm(homeForm)
                .awayTeamForm(awayForm)
                .potentials(createPotentials(70.0))
                .build();
    }
}
