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

    @Test
    void analyze_withProlificScorers_appliesGoalsBoost() {
        // Home team scores 1.5+ at home, away team scores 1.0+ away
        FixtureContext context = createContextWithGoalsData(75.0, 75.0, 18, 12);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("goalsBoostApplied")).isEqualTo(true);
        assertThat(result.get().getFactors().get("goalsBoostAmount")).isEqualTo(5.0);
    }

    @Test
    void analyze_withLowScorers_noGoalsBoost() {
        // Home team scores less than 1.5 at home
        FixtureContext context = createContextWithGoalsData(75.0, 75.0, 10, 8);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("goalsBoostApplied")).isEqualTo(false);
        assertThat(result.get().getFactors()).doesNotContainKey("goalsBoostAmount");
    }

    @Test
    void analyze_withoutFormData_usesRedistributedWeights() {
        // Create context without form data
        FixtureContext context = createContextWithBttsStats(80.0, 80.0);
        // This context doesn't have form data by default

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
        // Same season stats, but one has supporting form data
        FixtureContext contextWithForm = createContextWithFormData(75.0, 75.0, 85.0, 85.0);
        FixtureContext contextWithoutForm = createContextWithBttsStats(75.0, 75.0);

        Optional<Recommendation> resultWithForm = engine.analyze(contextWithForm);
        Optional<Recommendation> resultWithoutForm = engine.analyze(contextWithoutForm);

        assertThat(resultWithForm).isPresent();
        assertThat(resultWithoutForm).isPresent();
        // With strong form data (85%) supporting season data (75%), score should be higher
        assertThat(resultWithForm.get().getScore()).isGreaterThan(resultWithoutForm.get().getScore());
    }

    @Test
    void analyze_tracksGoalsAverages() {
        FixtureContext context = createContextWithGoalsData(75.0, 75.0, 15, 10);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("homeGoalsAvgHome");
        assertThat(result.get().getFactors()).containsKey("awayGoalsAvgAway");
    }

    @Test
    void analyze_withLeakyDefenses_appliesLeakyDefenseBoost() {
        // Home team concedes 1.2+ at home, away team concedes 1.0+ away
        FixtureContext context = createContextWithDefensiveData(75.0, 75.0, 14, 12);

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("leakyDefenseBoostApplied")).isEqualTo(true);
        assertThat(result.get().getFactors().get("leakyDefenseBoostAmount")).isEqualTo(4.0);
    }

    @Test
    void analyze_withSolidDefenses_noLeakyDefenseBoost() {
        // Home team concedes less than 1.2 at home
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
        // Both prolific scorers AND leaky defenses
        FixtureContext context = createContextWithBothBoosts();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("goalsBoostApplied")).isEqualTo(true);
        assertThat(result.get().getFactors().get("leakyDefenseBoostApplied")).isEqualTo(true);
        // Combined boost should result in higher score
        assertThat(result.get().getScore()).isGreaterThanOrEqualTo(80.0);
    }

    private FixtureContext createContextWithGoalsData(double homeBtts, double awayBtts, 
                                                       int homeGoalsHome, int awayGoalsAway) {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(20)
                .seasonBttsPercentageHome(homeBtts)
                .seasonBttsPercentageAway(homeBtts - 5)
                .seasonFailedToScoreOverall(3)
                .seasonGoalsHome(homeGoalsHome)  // 10 home matches
                .seasonGoalsAway(10)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(20)
                .seasonBttsPercentageHome(awayBtts - 5)
                .seasonBttsPercentageAway(awayBtts)
                .seasonFailedToScoreOverall(4)
                .seasonGoalsHome(12)
                .seasonGoalsAway(awayGoalsAway)  // 10 away matches
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

    private FixtureContext createContextWithDefensiveData(double homeBtts, double awayBtts,
                                                          int homeConcededHome, int awayConcededAway) {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(20)
                .seasonBttsPercentageHome(homeBtts)
                .seasonBttsPercentageAway(homeBtts - 5)
                .seasonFailedToScoreOverall(3)
                .seasonGoalsHome(12)
                .seasonGoalsAway(10)
                .seasonConcededHome(homeConcededHome)  // 10 home matches
                .seasonConcededAway(8)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(20)
                .seasonBttsPercentageHome(awayBtts - 5)
                .seasonBttsPercentageAway(awayBtts)
                .seasonFailedToScoreOverall(4)
                .seasonGoalsHome(12)
                .seasonGoalsAway(10)
                .seasonConcededHome(10)
                .seasonConcededAway(awayConcededAway)  // 10 away matches
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

    private FixtureContext createContextWithBothBoosts() {
        // High scoring AND leaky defenses
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(20)
                .seasonBttsPercentageHome(80.0)
                .seasonBttsPercentageAway(75.0)
                .seasonFailedToScoreOverall(2)
                .seasonGoalsHome(18)      // 1.8 goals/home game (≥1.5 threshold)
                .seasonGoalsAway(12)
                .seasonConcededHome(14)   // 1.4 conceded/home game (≥1.2 threshold)
                .seasonConcededAway(10)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(20)
                .seasonBttsPercentageHome(75.0)
                .seasonBttsPercentageAway(80.0)
                .seasonFailedToScoreOverall(2)
                .seasonGoalsHome(14)
                .seasonGoalsAway(12)      // 1.2 goals/away game (≥1.0 threshold)
                .seasonConcededHome(10)
                .seasonConcededAway(12)   // 1.2 conceded/away game (≥1.0 threshold)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .potentials(createPotentials(75.0))
                .build();
    }

    private FixtureContext createContextWithFormData(double homeBttsSeason, double awayBttsSeason,
                                                      double homeBttsForm, double awayBttsForm) {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(100L)
                .matchesPlayed(20)
                .seasonBttsPercentageHome(homeBttsSeason)
                .seasonBttsPercentageAway(homeBttsSeason - 5)
                .seasonFailedToScoreOverall(3)
                .seasonGoalsHome(15)
                .seasonGoalsAway(10)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(100L)
                .matchesPlayed(20)
                .seasonBttsPercentageHome(awayBttsSeason - 5)
                .seasonBttsPercentageAway(awayBttsSeason)
                .seasonFailedToScoreOverall(4)
                .seasonGoalsHome(12)
                .seasonGoalsAway(10)
                .build();

        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .bttsPercentageHome(homeBttsForm)
                .bttsPercentageAway(homeBttsForm - 10)
                .build();

        TeamRecentForm awayForm = TeamRecentForm.builder()
                .teamId(2L)
                .bttsPercentageHome(awayBttsForm - 10)
                .bttsPercentageAway(awayBttsForm)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture())
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .homeTeamForm(homeForm)
                .awayTeamForm(awayForm)
                .potentials(createPotentials(70.0))
                .build();
    }
}
