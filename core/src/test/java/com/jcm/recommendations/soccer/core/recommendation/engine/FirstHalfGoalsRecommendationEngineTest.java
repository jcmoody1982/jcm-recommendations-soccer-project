package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.poissonAtLeast;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FirstHalfGoalsRecommendationEngineTest {

    private FirstHalfGoalsRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new FirstHalfGoalsRecommendationEngine();
    }

    @Test
    @DisplayName("getType returns FIRST_HALF_GOALS")
    void getType_returnsCorrectType() {
        assertThat(engine.getType()).isEqualTo(RecommendationType.FIRST_HALF_GOALS);
    }

    @Test
    @DisplayName("analyze returns recommendation for high-scoring matchup")
    void analyze_withHighScoringMatchup_returnsRecommendation() {
        FixtureContext context = createHighScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(RecommendationType.FIRST_HALF_GOALS);
        assertThat(result.get().getMarket()).contains("HT Goals");
        assertThat(result.get().getConfidence()).isIn(ConfidenceLevel.MODERATE, ConfidenceLevel.STRONG);
    }

    @Test
    @DisplayName("analyze returns empty for low-scoring matchup")
    void analyze_withLowScoringMatchup_returnsEmpty() {
        FixtureContext context = createLowScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("analyze integrates xG data when available")
    void analyze_integratesXgData() {
        FixtureContext context = createContextWithXgData();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("xgDataAvailable")).isEqualTo(true);
        assertThat(result.get().getFactors()).containsKey("homeXgForAvgHome");
        assertThat(result.get().getFactors()).containsKey("awayXgForAvgAway");
        assertThat(result.get().getFactors()).containsKey("combinedXg");
        assertThat(result.get().getFactors()).containsKey("xgRating");
    }

    @Test
    @DisplayName("analyze uses API potentials")
    void analyze_usesApiPotentials() {
        FixtureContext context = createContextWithApiPotentials();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("apiO05HtPotential");
        assertThat(result.get().getFactors()).containsKey("apiO15HtPotential");
    }

    @Test
    @DisplayName("analyze flags fast starters when the provider reads the half hotter than a flat split")
    void analyze_flagsFastStarters() {
        FixtureContext context = createFastStarterContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        Map<String, Object> factors = result.get().getFactors();
        assertThat(factors).containsKey("frontLoadedRatio");
        assertThat((Double) factors.get("frontLoadedRatio")).isGreaterThan(1.0);
        assertThat(factors.get("fastStarterStatus")).isEqualTo("Fast starters detected");
    }

    @Test
    @DisplayName("analyze tracks 1H goals proxy values")
    void analyze_tracks1HGoalsProxy() {
        FixtureContext context = createHighScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("expected1HGoals");
        assertThat(result.get().getFactors()).containsKey("home1HScoredProxyAvg");
        assertThat(result.get().getFactors()).containsKey("away1HScoredProxyAvg");
        assertThat(result.get().getFactors()).containsKey("home1HConcededProxyAvg");
        assertThat(result.get().getFactors()).containsKey("away1HConcededProxyAvg");
        assertThat(result.get().getFactors()).containsKey("firstHalfRatioUsed");
        assertThat(result.get().getFactors().get("firstHalfRatioUsed")).isEqualTo(0.45);
        assertThat(result.get().getFactors().get("halfStatsFromApi")).isEqualTo(false);
    }

    @Test
    @DisplayName("analyze prefers real HT averages when present")
    void analyze_prefersRealHtAverages() {
        FixtureContext base = createHighScoringContext();
        base.getHomeTeamStats().setScoredAvgHtHome(1.2);
        base.getHomeTeamStats().setConcededAvgHtHome(0.9);
        base.getHomeTeamStats().setBttsFhgPercentageHome(48.0);
        base.getAwayTeamStats().setScoredAvgHtAway(1.0);
        base.getAwayTeamStats().setConcededAvgHtAway(1.1);
        base.getAwayTeamStats().setBttsFhgPercentageAway(44.0);

        Optional<Recommendation> result = engine.analyze(base);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("halfStatsFromApi")).isEqualTo(true);
        assertThat(result.get().getFactors().get("home1HScoredProxyAvg")).isEqualTo(1.2);
        assertThat(result.get().getFactors().get("away1HScoredProxyAvg")).isEqualTo(1.0);
        assertThat(result.get().getFactors().get("homeBttsSeasonPct")).isEqualTo(48.0);
    }

    @Test
    @DisplayName("analyze tracks early concede status")
    void analyze_tracksEarlyConcede() {
        FixtureContext context = createHighConcedingContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("combinedConcededAvg");
        assertThat(result.get().getFactors()).containsKey("earlyConcedeStatus");
    }

    @Test
    @DisplayName("analyze tracks recent form O1.5 for hot/cold detection")
    void analyze_tracksRecentFormO15() {
        FixtureContext context = createContextWithHotForm();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("homeO15RecentForm");
        assertThat(result.get().getFactors()).containsKey("awayO15RecentForm");
        assertThat(result.get().getFactors()).containsKey("totalO15InForm");
    }

    @Test
    @DisplayName("analyze recommends Over 1.5 HT for very high expected goals")
    void analyze_recommendsOver15HT_forVeryHighExpected() {
        FixtureContext context = createVeryHighScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getMarket()).isEqualTo("Over 1.5 HT Goals");
        assertThat(result.get().getFactors().get("goalsNeeded")).isEqualTo(2);
    }

    @Test
    @DisplayName("Over 1.5 HT is scored as a two-goal event, not a one-goal event")
    void over15Ht_isScoredAsTwoGoalEvent() {
        FixtureContext context = createVeryHighScoringContext();

        Recommendation result = engine.analyze(context).orElseThrow();
        Map<String, Object> factors = result.getFactors();

        double expected1HGoals = (Double) factors.get("expected1HGoals");
        double poisson = (Double) factors.get("poissonProbability");

        // A first half of this expectation delivers two goals well under half the time. The old
        // engine published this fixture in the high eighties.
        assertThat(poisson).isEqualTo(poissonAtLeast(expected1HGoals, 2), within(0.01));
        assertThat(result.getScore()).isLessThan(65.0);
    }

    @Test
    @DisplayName("Over 1.5 HT never approaches certainty, however extreme the fixture")
    void over15Ht_neverApproachesCertainty() {
        FixtureContext context = createVeryHighScoringContext();
        // Absurd inputs: every signal pushed past anything a real fixture produces.
        context.getHomeTeamStats().setScoredAvgHtHome(3.0);
        context.getHomeTeamStats().setConcededAvgHtHome(3.0);
        context.getAwayTeamStats().setScoredAvgHtAway(3.0);
        context.getAwayTeamStats().setConcededAvgHtAway(3.0);
        context.getHomeTeamStats().setXgForAvgHome(4.0);
        context.getAwayTeamStats().setXgForAvgAway(4.0);
        context.getPotentials().setO05HtPotential(99.0);
        context.getPotentials().setO15HtPotential(95.0);
        context.setHomeTeamForm(TeamRecentForm.builder().teamId(1L).over15Overall(5).build());
        context.setAwayTeamForm(TeamRecentForm.builder().teamId(2L).over15Overall(5).build());

        Recommendation result = engine.analyze(context).orElseThrow();

        assertThat(result.getMarket()).isEqualTo("Over 1.5 HT Goals");
        assertThat(result.getScore()).isLessThan(95.0);
    }

    @Test
    @DisplayName("Over 0.5 HT stays inside the band a first half can actually reach")
    void over05Ht_staysWithinRealisticBand() {
        FixtureContext context = createHighScoringContext();

        Recommendation result = engine.analyze(context).orElseThrow();

        assertThat(result.getMarket()).isEqualTo("Over 0.5 HT Goals");
        assertThat(result.getScore()).isBetween(60.0, 90.0);
    }

    @Test
    @DisplayName("a livelier first half still scores strictly higher on the same line")
    void higherExpectedGoals_scoresStrictlyHigher() {
        FixtureContext tame = withHalfTimeAverages(createHighScoringContext(), 0.55, 0.50);
        FixtureContext lively = withHalfTimeAverages(createHighScoringContext(), 0.75, 0.70);

        Recommendation tameResult = engine.analyze(tame).orElseThrow();
        Recommendation livelyResult = engine.analyze(lively).orElseThrow();

        // Scores only compare within a line, so the assertion is only meaningful while both sit
        // on the same one.
        assertThat(livelyResult.getMarket()).isEqualTo(tameResult.getMarket());
        assertThat((Double) livelyResult.getFactors().get("expected1HGoals"))
                .isGreaterThan((Double) tameResult.getFactors().get("expected1HGoals"));
        assertThat(livelyResult.getScore()).isGreaterThan(tameResult.getScore());
    }

    private FixtureContext withHalfTimeAverages(FixtureContext context, double homeScored, double awayScored) {
        context.getHomeTeamStats().setScoredAvgHtHome(homeScored);
        context.getHomeTeamStats().setConcededAvgHtHome(awayScored);
        context.getAwayTeamStats().setScoredAvgHtAway(awayScored);
        context.getAwayTeamStats().setConcededAvgHtAway(homeScored);
        return context;
    }

    @Test
    @DisplayName("Over 1.5 HT is withheld when the provider does not corroborate the line")
    void over15Ht_withheldWithoutProviderCorroboration() {
        FixtureContext context = createVeryHighScoringContext();
        context.getPotentials().setO15HtPotential(35.0);

        Recommendation result = engine.analyze(context).orElseThrow();

        assertThat(result.getMarket()).isEqualTo("Over 0.5 HT Goals");
    }

    @Test
    @DisplayName("correlated goal signals cannot compound past the adjustment ceiling")
    void expectedGoalsAdjustment_isBounded() {
        FixtureContext context = createXgUnderperformingContext();
        context.setHomeTeamForm(TeamRecentForm.builder().teamId(1L).over15Overall(5).build());
        context.setAwayTeamForm(TeamRecentForm.builder().teamId(2L).over15Overall(5).build());

        Recommendation result = engine.analyze(context).orElseThrow();

        double adjustment = (Double) result.getFactors().get("expectedGoalsAdjustment");
        assertThat(adjustment).isBetween(0.85, 1.20);
    }

    @Test
    @DisplayName("the published score is the blend of the Poisson tail and the provider potential")
    void score_isBlendOfPoissonAndProviderPotential() {
        FixtureContext context = createHighScoringContext();

        Recommendation result = engine.analyze(context).orElseThrow();
        Map<String, Object> factors = result.getFactors();

        double poisson = (Double) factors.get("poissonProbability");
        double potential = (Double) factors.get("apiPotentialForLine");

        assertThat(result.getScore()).isEqualTo((poisson * 0.65) + (potential * 0.35), within(0.01));
        assertThat(result.getScore()).isBetween(Math.min(poisson, potential), Math.max(poisson, potential));
    }

    @Test
    @DisplayName("analyze tracks positive indicators and risk flags")
    void analyze_tracksIndicatorsAndFlags() {
        FixtureContext context = createHighScoringContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("positiveIndicators");
        assertThat(result.get().getFactors()).containsKey("riskFlags");
        assertThat(result.get().getFactors().get("positiveIndicators")).isInstanceOf(List.class);
        assertThat(result.get().getFactors().get("riskFlags")).isInstanceOf(List.class);
    }

    @Test
    @DisplayName("analyze returns empty when fixture context is incomplete")
    void analyze_withIncompleteContext_returnsEmpty() {
        FixtureContext context = createIncompleteContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("analyze applies xG regression multiplier for underperforming teams")
    void analyze_appliesXgRegressionMultiplier() {
        FixtureContext context = createXgUnderperformingContext();

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors()).containsKey("homeXgPerformance");
        assertThat(result.get().getFactors()).containsKey("awayXgPerformance");
    }

    // Helper methods to create test contexts

    private FixtureContext createHighScoringContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .matchesPlayedHome(20)
                .matchesPlayedAway(20)
                .seasonGoalsHome(40)    // 2.0 per game
                .seasonConcededHome(25) // 1.25 per game
                .seasonBttsPercentageHome(65.0)
                .seasonOver25PercentageOverall(70.0)
                .xgForAvgHome(2.0)
                .xgAgainstAvgHome(1.2)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .matchesPlayedHome(20)
                .matchesPlayedAway(20)
                .seasonGoalsAway(35)    // 1.75 per game
                .seasonConcededAway(28) // 1.4 per game
                .seasonBttsPercentageAway(60.0)
                .seasonOver25PercentageOverall(65.0)
                .xgForAvgAway(1.8)
                .xgAgainstAvgAway(1.3)
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(101L)
                .o05HtPotential(78.0)
                .o15HtPotential(50.0)
                .o25Potential(70.0)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(101L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .potentials(potentials)
                .build();
    }

    private FixtureContext createLowScoringContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .matchesPlayedHome(20)
                .matchesPlayedAway(20)
                .seasonGoalsHome(12)    // 0.6 per game
                .seasonConcededHome(10) // 0.5 per game
                .seasonBttsPercentageHome(25.0)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .matchesPlayedHome(20)
                .matchesPlayedAway(20)
                .seasonGoalsAway(10)    // 0.5 per game
                .seasonConcededAway(12) // 0.6 per game
                .seasonBttsPercentageAway(30.0)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(102L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .build();
    }

    private FixtureContext createContextWithXgData() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .matchesPlayedHome(20)
                .matchesPlayedAway(20)
                .seasonGoalsHome(30)
                .seasonConcededHome(18)
                .seasonBttsPercentageHome(55.0)
                .xgForAvgHome(1.8)
                .xgForAvgAway(1.4)
                .xgAgainstAvgHome(0.9)
                .xgAgainstAvgAway(1.1)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .matchesPlayedHome(20)
                .matchesPlayedAway(20)
                .seasonGoalsAway(25)
                .seasonConcededAway(22)
                .seasonBttsPercentageAway(50.0)
                .xgForAvgHome(1.5)
                .xgForAvgAway(1.6)
                .xgAgainstAvgHome(1.2)
                .xgAgainstAvgAway(1.3)
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(103L)
                .o05HtPotential(70.0)
                .o15HtPotential(40.0)
                .o25Potential(60.0)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(103L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .potentials(potentials)
                .build();
    }

    private FixtureContext createContextWithApiPotentials() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .matchesPlayedHome(20)
                .matchesPlayedAway(20)
                .seasonGoalsHome(38)    // 1.9 per game
                .seasonConcededHome(24)
                .seasonBttsPercentageHome(60.0)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .matchesPlayedHome(20)
                .matchesPlayedAway(20)
                .seasonGoalsAway(32)    // 1.6 per game
                .seasonConcededAway(26)
                .seasonBttsPercentageAway(55.0)
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(104L)
                .o05HtPotential(80.0)
                .o15HtPotential(55.0)
                .o25Potential(70.0)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(104L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .potentials(potentials)
                .build();
    }

    private FixtureContext createFastStarterContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .matchesPlayedHome(20)
                .matchesPlayedAway(20)
                .seasonGoalsHome(35)
                .seasonConcededHome(20)
                .seasonBttsPercentageHome(60.0)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .matchesPlayedHome(20)
                .matchesPlayedAway(20)
                .seasonGoalsAway(30)
                .seasonConcededAway(25)
                .seasonBttsPercentageAway(55.0)
                .build();

        // High O05HT relative to O25 = fast starters
        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(105L)
                .o05HtPotential(90.0)  // Very high
                .o15HtPotential(60.0)
                .o25Potential(70.0)    // Not proportionally as high
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(105L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .potentials(potentials)
                .build();
    }

    private FixtureContext createHighConcedingContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .matchesPlayedHome(20)
                .matchesPlayedAway(20)
                .seasonGoalsHome(30)
                .seasonConcededHome(35)  // 1.75 per game - high
                .seasonBttsPercentageHome(70.0)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .matchesPlayedHome(20)
                .matchesPlayedAway(20)
                .seasonGoalsAway(28)
                .seasonConcededAway(30)  // 1.5 per game - high
                .seasonBttsPercentageAway(65.0)
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(106L)
                .o05HtPotential(75.0)
                .o15HtPotential(50.0)
                .o25Potential(70.0)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(106L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .potentials(potentials)
                .build();
    }

    private FixtureContext createContextWithHotForm() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .matchesPlayedHome(20)
                .matchesPlayedAway(20)
                .seasonGoalsHome(38)    // 1.9 per game
                .seasonConcededHome(24)
                .seasonBttsPercentageHome(60.0)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .matchesPlayedHome(20)
                .matchesPlayedAway(20)
                .seasonGoalsAway(32)    // 1.6 per game
                .seasonConcededAway(26)
                .seasonBttsPercentageAway(55.0)
                .build();

        TeamRecentForm homeForm = TeamRecentForm.builder()
                .teamId(1L)
                .over15Overall(5)  // 5 of 5 had O1.5
                .over25Overall(4)
                .build();

        TeamRecentForm awayForm = TeamRecentForm.builder()
                .teamId(2L)
                .over15Overall(4)  // 4 of 5 had O1.5
                .over25Overall(3)
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(107L)
                .o05HtPotential(75.0)
                .o15HtPotential(50.0)
                .o25Potential(70.0)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(107L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .homeTeamForm(homeForm)
                .awayTeamForm(awayForm)
                .potentials(potentials)
                .build();
    }

    private FixtureContext createVeryHighScoringContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .matchesPlayedHome(20)
                .matchesPlayedAway(20)
                .seasonGoalsHome(45)    // 2.25 per game
                .seasonConcededHome(25)
                .seasonBttsPercentageHome(70.0)
                .xgForAvgHome(2.2)
                .xgAgainstAvgHome(1.2)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .matchesPlayedHome(20)
                .matchesPlayedAway(20)
                .seasonGoalsAway(38)    // 1.9 per game
                .seasonConcededAway(30)
                .seasonBttsPercentageAway(65.0)
                .xgForAvgAway(1.9)
                .xgAgainstAvgAway(1.4)
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(108L)
                .o05HtPotential(85.0)
                .o15HtPotential(60.0)  // High O1.5 HT potential
                .o25Potential(80.0)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(108L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .potentials(potentials)
                .build();
    }

    private FixtureContext createIncompleteContext() {
        return FixtureContext.builder()
                .fixture(createFixture(109L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .build();
    }

    private FixtureContext createXgUnderperformingContext() {
        TeamSeasonStats homeStats = TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .matchesPlayedHome(20)
                .matchesPlayedAway(20)
                .seasonGoalsHome(26)    // 1.3 actual - underperforming vs xG of 2.0
                .seasonConcededHome(22)
                .seasonBttsPercentageHome(55.0)
                .xgForAvgHome(2.0)      // xG higher than actual
                .xgAgainstAvgHome(1.1)
                .build();

        TeamSeasonStats awayStats = TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .matchesPlayedHome(20)
                .matchesPlayedAway(20)
                .seasonGoalsAway(24)    // 1.2 actual - underperforming vs xG of 1.8
                .seasonConcededAway(24)
                .seasonBttsPercentageAway(50.0)
                .xgForAvgAway(1.8)      // xG higher than actual
                .xgAgainstAvgAway(1.2)
                .build();

        FixturePotentials potentials = FixturePotentials.builder()
                .fixtureId(110L)
                .o05HtPotential(75.0)
                .o15HtPotential(48.0)
                .o25Potential(65.0)
                .build();

        return FixtureContext.builder()
                .fixture(createFixture(110L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats)
                .awayTeamStats(awayStats)
                .potentials(potentials)
                .build();
    }

    private Fixture createFixture(Long id) {
        return Fixture.builder()
                .id(id)
                .homeTeamId(1L)
                .awayTeamId(2L)
                .seasonId(1L)
                .status("upcoming")
                .build();
    }

    private Team createTeam(Long id, String name) {
        return Team.builder()
                .id(id)
                .name(name)
                .build();
    }
}
