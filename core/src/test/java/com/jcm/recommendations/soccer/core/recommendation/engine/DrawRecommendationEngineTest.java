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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DrawRecommendationEngineTest {

    private DrawRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DrawRecommendationEngine();
    }

    @Test
    @DisplayName("getType returns DRAW")
    void getType_returnsCorrectType() {
        assertThat(engine.getType()).isEqualTo(RecommendationType.DRAW);
    }

    @Test
    @DisplayName("analyze publishes a level, well-priced fixture")
    void analyze_publishesLevelWellPricedFixture() {
        Optional<Recommendation> result = engine.analyze(drawFriendlyContext());

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(RecommendationType.DRAW);
        assertThat(result.get().getMarket()).isEqualTo("Draw");
        assertThat(result.get().getConfidence()).isEqualTo(ConfidenceLevel.MODERATE);
    }

    // ===== The score is a probability now, not an index =====

    @Test
    @DisplayName("published probability stays inside a realistic draw band")
    void publishedProbability_staysInRealisticBand() {
        Optional<Recommendation> result = engine.analyze(drawFriendlyContext());

        assertThat(result).isPresent();
        // A draw is never a near-certainty. The old index could publish 60+ for a fixture that hit
        // 12% of the time; anything outside roughly 20-40 is not a draw probability.
        assertThat(result.get().getScore()).isBetween(20.0, 40.0);
    }

    @Test
    @DisplayName("published probability is the model/market blend")
    void publishedProbability_isModelMarketBlend() {
        Optional<Recommendation> result = engine.analyze(drawFriendlyContext());

        assertThat(result).isPresent();
        Map<String, Object> factors = result.get().getFactors();
        double model = (Double) factors.get("modelDrawProbability");
        double market = (Double) factors.get("marketDrawProbability");

        assertThat((Double) factors.get("publishedDrawProbability"))
                .isEqualTo(result.get().getScore());
        assertThat(result.get().getScore())
                .isCloseTo((model * 0.45) + (market * 0.55), within(0.001));
    }

    @Test
    @DisplayName("model probability comes from the two goal expectations")
    void modelProbability_derivesFromGoalExpectations() {
        Optional<Recommendation> result = engine.analyze(drawFriendlyContext());

        assertThat(result).isPresent();
        Map<String, Object> factors = result.get().getFactors();
        assertThat((Double) factors.get("expectedGoalsHome")).isBetween(0.9, 1.4);
        assertThat((Double) factors.get("expectedGoalsAway")).isBetween(0.9, 1.4);
        assertThat((Double) factors.get("combinedExpectedGoals")).isCloseTo(
                (Double) factors.get("expectedGoalsHome") + (Double) factors.get("expectedGoalsAway"),
                within(0.001));
        assertThat(factors.get("xgDataAvailable")).isEqualTo(true);
    }

    @Test
    @DisplayName("draw-heavy recent form no longer moves the score")
    void drawHeavyForm_doesNotInflateScore() {
        // The old engine multiplied by 1.25 for a three-draw streak. Draw frequency does not
        // persist, so that multiplier was selecting on noise — it must not change the number.
        Recommendation streak = engine.analyze(drawFriendlyContext(builder -> builder
                .homeTeamForm(TeamRecentForm.builder().teamId(1L).drawsHome(3).build())
                .awayTeamForm(TeamRecentForm.builder().teamId(2L).drawsAway(3).build())))
                .orElseThrow();

        Recommendation noDraws = engine.analyze(drawFriendlyContext(builder -> builder
                .homeTeamForm(TeamRecentForm.builder().teamId(1L).drawsHome(0).build())
                .awayTeamForm(TeamRecentForm.builder().teamId(2L).drawsAway(0).build())))
                .orElseThrow();

        assertThat(streak.getScore()).isEqualTo(noDraws.getScore());
        assertThat(streak.getFactors().get("homeDrawsLast5")).isEqualTo(3);
    }

    @Test
    @DisplayName("a draw-friendly referee no longer moves the score")
    void drawFriendlyReferee_doesNotInflateScore() {
        Recommendation withReferee = engine.analyze(drawFriendlyContext(builder -> builder
                .refereeStats(RefereeStats.builder()
                        .refereeId(1L)
                        .seasonId(1L)
                        .appearancesOverall(15)
                        .drawsPer(34.0)
                        .cardsPerMatchOverall(2.4)
                        .build())))
                .orElseThrow();

        Recommendation withoutReferee = engine.analyze(drawFriendlyContext()).orElseThrow();

        assertThat(withReferee.getScore()).isEqualTo(withoutReferee.getScore());
        assertThat((Double) withReferee.getFactors().get("refereeDrawPct")).isEqualTo(34.0);
        assertThat(asStrings(withReferee.getFactors().get("positiveIndicators")))
                .anyMatch(s -> s.contains("Draw-friendly referee"));
    }

    // ===== Publishing gates =====

    @Test
    @DisplayName("no price means no pick")
    void withoutOdds_isWithheld() {
        assertThat(engine.analyze(drawFriendlyContext(builder -> builder.odds(null)))).isEmpty();
    }

    @Test
    @DisplayName("a partial 1X2 cannot be de-vigged, so it is withheld")
    void withIncompleteOdds_isWithheld() {
        FixtureContext context = drawFriendlyContext(builder -> builder
                .odds(FixtureOdds.builder().fixtureId(101L).oddsFtX(3.60).build()));

        assertThat(engine.analyze(context)).isEmpty();
    }

    @Test
    @DisplayName("a short draw price is withheld even when the match is level")
    void shortPrice_isWithheld() {
        // Same level fixture, but priced so the bet no longer clears its own cost.
        FixtureContext context = drawFriendlyContext(builder -> builder
                .odds(odds(2.45, 3.10, 2.80)));

        assertThat(engine.analyze(context)).isEmpty();
    }

    @Test
    @DisplayName("an implausibly large edge is withheld rather than treated as the best pick")
    void oversizedEdge_isWithheld() {
        // Model says 36%+ against a market read of 27%. That gap is far more likely to mean our
        // expectations are wrong than that the price is a gift.
        FixtureContext context = drawFriendlyContext(builder -> builder
                .homeTeamStats(homeStats(stats -> stats
                        .seasonGoalsHome(15).seasonConcededHome(15)
                        .xgForAvgHome(0.75).xgAgainstAvgHome(0.75)))
                .awayTeamStats(awayStats(stats -> stats
                        .seasonGoalsAway(15).seasonConcededAway(15)
                        .xgForAvgAway(0.75).xgAgainstAvgAway(0.75))));

        assertThat(engine.analyze(context)).isEmpty();
    }

    @Test
    @DisplayName("mismatched teams are withheld")
    void mismatchedTeams_areWithheld() {
        FixtureContext context = drawFriendlyContext(builder -> builder
                .homeTeamStats(homeStats(stats -> stats
                        .ppgOverall(2.5).position(1)
                        .seasonWinsHome(14).seasonDrawsHome(3).seasonLossesHome(3)
                        .seasonGoalsHome(42).seasonConcededHome(12)
                        .xgForAvgHome(2.3).xgAgainstAvgHome(0.6)))
                .awayTeamStats(awayStats(stats -> stats
                        .ppgOverall(0.8).position(19)
                        .seasonWinsAway(2).seasonDrawsAway(3).seasonLossesAway(15)
                        .seasonGoalsAway(12).seasonConcededAway(40)
                        .xgForAvgAway(0.7).xgAgainstAvgAway(2.2))));

        assertThat(engine.analyze(context)).isEmpty();
    }

    @Test
    @DisplayName("a high-scoring fixture is withheld however level it looks")
    void highScoringFixture_isWithheld() {
        FixtureContext context = drawFriendlyContext(builder -> builder
                .homeTeamStats(homeStats(stats -> stats
                        .seasonGoalsHome(40).seasonConcededHome(36)
                        .xgForAvgHome(2.0).xgAgainstAvgHome(1.8)))
                .awayTeamStats(awayStats(stats -> stats
                        .seasonGoalsAway(36).seasonConcededAway(38)
                        .xgForAvgAway(1.8).xgAgainstAvgAway(2.0))));

        assertThat(engine.analyze(context)).isEmpty();
    }

    @Test
    @DisplayName("a thin venue record is withheld rather than modelled")
    void thinVenueRecord_isWithheld() {
        FixtureContext context = drawFriendlyContext(builder -> builder
                .homeTeamStats(homeStats(stats -> stats
                        .seasonWinsHome(1).seasonDrawsHome(1).seasonLossesHome(1)
                        .seasonGoalsHome(3).seasonConcededHome(3))));

        assertThat(engine.analyze(context)).isEmpty();
    }

    @Test
    @DisplayName("analyze returns empty when context is incomplete")
    void withIncompleteContext_returnsEmpty() {
        FixtureContext context = FixtureContext.builder()
                .fixture(createFixture(111L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .build();

        assertThat(engine.analyze(context)).isEmpty();
    }

    // ===== Reporting =====

    @Test
    @DisplayName("factors report the price and the edge taken at it")
    void factors_reportPriceAndEdge() {
        Optional<Recommendation> result = engine.analyze(drawFriendlyContext());

        assertThat(result).isPresent();
        Map<String, Object> factors = result.get().getFactors();
        assertThat((Double) factors.get("drawOdds")).isEqualTo(3.60);
        assertThat((Double) factors.get("edgeAtPrice")).isGreaterThan(0.03);
        assertThat(result.get().getOdds()).isEqualTo(3.60);
    }

    @Test
    @DisplayName("factors track positive indicators and risk flags")
    void factors_trackIndicatorsAndFlags() {
        Optional<Recommendation> result = engine.analyze(drawFriendlyContext());

        assertThat(result).isPresent();
        assertThat(asStrings(result.get().getFactors().get("positiveIndicators"))).isNotEmpty();
        assertThat(result.get().getFactors()).containsKey("riskFlags");
    }

    @Test
    @DisplayName("missing xG is flagged as a risk")
    void missingXg_isFlagged() {
        FixtureContext context = drawFriendlyContext(builder -> builder
                .homeTeamStats(homeStats(stats -> stats
                        .xgForAvgHome(null).xgAgainstAvgHome(null)))
                .awayTeamStats(awayStats(stats -> stats
                        .xgForAvgAway(null).xgAgainstAvgAway(null))));

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getFactors().get("xgDataAvailable")).isEqualTo(false);
        assertThat(asStrings(result.get().getFactors().get("riskFlags")))
                .anyMatch(s -> s.contains("No xG data"));
    }

    @Test
    @DisplayName("draw specialists still colour the write-up")
    void drawSpecialists_colourDescription() {
        FixtureContext context = drawFriendlyContext(builder -> builder
                .homeTeamStats(homeStats(stats -> stats
                        .ppgOverall(1.3)
                        .seasonWinsHome(4).seasonDrawsHome(10).seasonLossesHome(6)
                        .seasonGoalsHome(20).seasonConcededHome(22)
                        .xgForAvgHome(null).xgAgainstAvgHome(null)))
                .awayTeamStats(awayStats(stats -> stats
                        .ppgOverall(1.2)
                        .seasonWinsAway(3).seasonDrawsAway(9).seasonLossesAway(8)
                        .seasonGoalsAway(16).seasonConcededAway(24)
                        .xgForAvgAway(null).xgAgainstAvgAway(null))));

        Optional<Recommendation> result = engine.analyze(context);

        assertThat(result).isPresent();
        assertThat(result.get().getDescription()).contains("Draw specialists");
        assertThat(asStrings(result.get().getFactors().get("positiveIndicators")))
                .anyMatch(s -> s.contains("Draw specialist"));
    }

    @Test
    @DisplayName("Draw confidence is capped at MODERATE (no STRONG tier)")
    void determineConfidence_cappedAtModerate() {
        assertThat(engine.determineConfidence(55.0)).isEqualTo(ConfidenceLevel.MODERATE);
        assertThat(engine.determineConfidence(30.0)).isEqualTo(ConfidenceLevel.MODERATE);
        assertThat(engine.determineConfidence(26.0)).isEqualTo(ConfidenceLevel.MODERATE);
        assertThat(engine.determineConfidence(25.9)).isEqualTo(ConfidenceLevel.WEAK);
    }

    // ===== Helpers =====

    /**
     * A level, low-scoring, fairly priced fixture: both sides expected around 1.1 goals, PPG within
     * 0.1, and a draw offered at 3.60 against a de-vigged market read of roughly 26.6%.
     */
    private FixtureContext drawFriendlyContext() {
        return drawFriendlyContext(builder -> builder);
    }

    private FixtureContext drawFriendlyContext(ContextCustomiser customiser) {
        FixtureContext.FixtureContextBuilder builder = FixtureContext.builder()
                .fixture(createFixture(101L))
                .homeTeam(createTeam(1L, "Home Team"))
                .awayTeam(createTeam(2L, "Away Team"))
                .homeTeamStats(homeStats(stats -> stats))
                .awayTeamStats(awayStats(stats -> stats))
                .odds(odds(2.45, 3.60, 2.80));
        return customiser.apply(builder).build();
    }

    private TeamSeasonStats homeStats(StatsCustomiser customiser) {
        return customiser.apply(TeamSeasonStats.builder()
                .teamId(1L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgOverall(1.5)
                .position(8)
                .seasonWinsHome(5)
                .seasonDrawsHome(7)
                .seasonLossesHome(8)
                .seasonGoalsHome(22)
                .seasonConcededHome(24)
                .xgForAvgOverall(1.15)
                .xgForAvgHome(1.15)
                .xgAgainstAvgHome(1.20)).build();
    }

    private TeamSeasonStats awayStats(StatsCustomiser customiser) {
        return customiser.apply(TeamSeasonStats.builder()
                .teamId(2L)
                .seasonId(1L)
                .matchesPlayed(20)
                .ppgOverall(1.4)
                .position(9)
                .seasonWinsAway(5)
                .seasonDrawsAway(6)
                .seasonLossesAway(9)
                .seasonGoalsAway(18)
                .seasonConcededAway(22)
                .xgForAvgOverall(1.10)
                .xgForAvgAway(0.95)
                .xgAgainstAvgAway(1.10)).build();
    }

    private FixtureOdds odds(double home, double draw, double away) {
        return FixtureOdds.builder()
                .fixtureId(101L)
                .oddsFt1(home)
                .oddsFtX(draw)
                .oddsFt2(away)
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<String> asStrings(Object value) {
        return (List<String>) value;
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

    @FunctionalInterface
    private interface ContextCustomiser {
        FixtureContext.FixtureContextBuilder apply(FixtureContext.FixtureContextBuilder builder);
    }

    @FunctionalInterface
    private interface StatsCustomiser {
        TeamSeasonStats.TeamSeasonStatsBuilder apply(TeamSeasonStats.TeamSeasonStatsBuilder builder);
    }
}
