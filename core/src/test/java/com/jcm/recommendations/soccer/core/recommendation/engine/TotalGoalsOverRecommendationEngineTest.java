package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.domain.Fixture;
import com.jcm.recommendations.soccer.domain.FixtureOdds;
import com.jcm.recommendations.soccer.domain.FixturePotentials;
import com.jcm.recommendations.soccer.domain.Team;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Shared Over X.5 scoring, exercised through a minimal line so the base-class branches (xG, form,
 * API potential, odds gate, confidence bands) are pinned independently of Over 1.5 / Over 2.5 tuning.
 */
class TotalGoalsOverRecommendationEngineTest {

    private static final class StubLineEngine extends TotalGoalsOverRecommendationEngine {
        private final LineSpec spec;
        private final boolean requireOdds;

        StubLineEngine(double strong, double moderate, boolean requireOdds) {
            this(1.0, strong, moderate, requireOdds);
        }

        StubLineEngine(double minExpectedGoals, double strong, double moderate, boolean requireOdds) {
            this.spec = new LineSpec(RecommendationType.OVER_25_GOALS, "Over 2.5 Goals", "overPct", "apiPotential",
                    minExpectedGoals, strong, moderate, 2.5);
            this.requireOdds = requireOdds;
        }

        @Override
        protected LineSpec spec() {
            return spec;
        }

        @Override
        protected Double seasonOverPercentage(TeamSeasonStats stats) {
            return stats == null ? null : stats.getSeasonOver25PercentageOverall();
        }

        @Override
        protected Double formOverPercentage(TeamRecentForm form) {
            return form == null ? null : form.getOver25PercentageOverall();
        }

        @Override
        protected Double apiPotential(FixtureContext context) {
            return context.hasPotentials() ? context.getPotentials().getO25Potential() : null;
        }

        @Override
        protected Double oddsForMarket(FixtureContext context) {
            return context.hasOdds() ? context.getOdds().getOddsFtOver25() : null;
        }

        @Override
        protected boolean passesOddsGate(Double odds) {
            return !requireOdds || (odds != null && odds > 1.0);
        }
    }

    private static final StubLineEngine LENIENT = new StubLineEngine(60.0, 40.0, false);

    @Test
    @DisplayName("goalsNeeded rounds the line up to whole goals")
    void lineSpecGoalsNeeded() {
        assertThat(LENIENT.spec().goalsNeeded()).isEqualTo(3);
        assertThat(new StubLineEngine(60, 40, false).getType()).isEqualTo(RecommendationType.OVER_25_GOALS);
    }

    @Test
    @DisplayName("season-only data: expected goals is the venue average and score is an even Poisson/empirical blend")
    void seasonOnlyBlend() {
        Recommendation rec = LENIENT.analyze(context(stats(1L, 70.0, null), stats(2L, 60.0, null), null, null, null))
                .orElseThrow();
        Map<String, Object> f = rec.getFactors();

        assertThat((double) f.get("expectedGoals")).isCloseTo((double) f.get("combinedGoalsAvg"), within(1e-9));
        assertThat((double) f.get("empiricalOverPct")).isCloseTo(65.0, within(1e-9));
        double expectedScore = 0.5 * (double) f.get("poissonProbability") + 0.5 * (double) f.get("empiricalOverPct");
        assertThat(rec.getScore()).isCloseTo(expectedScore, within(1e-9));
        assertThat(f).doesNotContainKey("apiPotential");
        assertThat(f.get("xgDataAvailable")).isEqualTo(false);
        assertThat(f.get("formDataAvailable")).isEqualTo(false);
        assertThat(f.get("ceilingApplied")).isEqualTo(false);
        assertThat(rec.getOdds()).isNull();
    }

    @Test
    @DisplayName("API potential pulls the empirical rate towards the provider's number")
    void apiPotentialWeighsIntoEmpiricalRate() {
        FixturePotentials potentials = FixturePotentials.builder().fixtureId(1000L).o25Potential(95.0).build();

        Recommendation without = LENIENT.analyze(context(stats(1L, 60.0, null), stats(2L, 60.0, null), null, null, null))
                .orElseThrow();
        Recommendation with = LENIENT.analyze(context(stats(1L, 60.0, null), stats(2L, 60.0, null), null, potentials, null))
                .orElseThrow();

        double empirical = (double) with.getFactors().get("empiricalOverPct");
        assertThat(empirical).isCloseTo((60.0 * 0.3 + 95.0 * 0.4) / 0.7, within(1e-9));
        assertThat(with.getFactors()).containsEntry("apiPotential", 95.0);
        assertThat(with.getScore()).isGreaterThan(without.getScore());
    }

    @Test
    @DisplayName("xG is only blended in when all four venue xG figures are present")
    void xgRequiresAllFourFigures() {
        TeamSeasonStats homeWithXg = stats(1L, 60.0, 3.0);
        TeamSeasonStats awayWithXg = stats(2L, 60.0, 3.0);
        Recommendation withXg = LENIENT.analyze(context(homeWithXg, awayWithXg, null, null, null)).orElseThrow();

        assertThat(withXg.getFactors().get("xgDataAvailable")).isEqualTo(true);
        assertThat((double) withXg.getFactors().get("expectedGoals"))
                .isGreaterThan((double) withXg.getFactors().get("combinedGoalsAvg"));
        assertThat(withXg.getFactors()).containsKey("combinedXg");

        TeamSeasonStats partial = stats(2L, 60.0, 3.0);
        partial.setXgAgainstAvgAway(null);
        Recommendation partialXg = LENIENT.analyze(context(homeWithXg, partial, null, null, null)).orElseThrow();

        assertThat((double) partialXg.getFactors().get("expectedGoals"))
                .isCloseTo((double) partialXg.getFactors().get("combinedGoalsAvg"), within(1e-9));
        assertThat(partialXg.getFactors()).doesNotContainKey("awayXgAgainstAvgAway");
    }

    @Test
    @DisplayName("recent form blends into both expected goals and the over-rate; missing form rate falls back to season")
    void recentFormBlend() {
        TeamRecentForm hotHome = form(3.0, 1.5, 100.0);
        TeamRecentForm awayNoRate = form(2.0, 1.5, null);

        Recommendation rec = LENIENT.analyze(
                context(stats(1L, 60.0, null), stats(2L, 50.0, null), new TeamRecentForm[]{hotHome, awayNoRate}, null, null))
                .orElseThrow();
        Map<String, Object> f = rec.getFactors();

        assertThat(f.get("formDataAvailable")).isEqualTo(true);
        assertThat((double) f.get("overPctHome")).isCloseTo(80.0, within(1e-9));
        assertThat((double) f.get("overPctAway")).isCloseTo(50.0, within(1e-9));
        assertThat(f).containsEntry("homeOverFormPct", 100.0).doesNotContainKey("awayOverFormPct");
        assertThat((double) f.get("expectedGoals")).isNotCloseTo((double) f.get("combinedGoalsAvg"), within(1e-6));
    }

    @Test
    @DisplayName("confidence bands follow the line thresholds and WEAK is never published")
    void confidenceBands() {
        FixtureContext ctx = context(stats(1L, 70.0, null), stats(2L, 70.0, null), null, null, null);
        double score = LENIENT.analyze(ctx).orElseThrow().getScore();

        assertThat(new StubLineEngine(score - 1, score - 10, false).analyze(ctx))
                .get().extracting(Recommendation::getConfidence).isEqualTo(ConfidenceLevel.STRONG);
        assertThat(new StubLineEngine(score + 1, score - 1, false).analyze(ctx))
                .get().extracting(Recommendation::getConfidence).isEqualTo(ConfidenceLevel.MODERATE);
        assertThat(new StubLineEngine(score + 10, score + 1, false).analyze(ctx)).isEmpty();
    }

    @Test
    @DisplayName("odds gate drops otherwise-valid picks without a backable price")
    void oddsGate() {
        StubLineEngine priced = new StubLineEngine(60.0, 40.0, true);
        FixtureOdds odds = FixtureOdds.builder().fixtureId(1000L).oddsFtOver25(1.75).build();

        assertThat(priced.analyze(context(stats(1L, 70.0, null), stats(2L, 70.0, null), null, null, null))).isEmpty();
        Optional<Recommendation> rec = priced.analyze(context(stats(1L, 70.0, null), stats(2L, 70.0, null), null, null, odds));
        assertThat(rec).get().extracting(Recommendation::getOdds).isEqualTo(1.75);
    }

    @Test
    @DisplayName("fixtures below the expected-goals floor are filtered before scoring")
    void expectedGoalsFloor() {
        StubLineEngine strictFloor = new StubLineEngine(50.0, 60.0, 40.0, false);

        assertThat(strictFloor.analyze(context(stats(1L, 99.0, null), stats(2L, 99.0, null), null, null, null))).isEmpty();
    }

    private static TeamSeasonStats stats(long teamId, Double over25Pct, Double xg) {
        return TeamSeasonStats.builder()
                .teamId(teamId)
                .seasonId(100L)
                .matchesPlayed(10)
                .matchesPlayedHome(10)
                .matchesPlayedAway(10)
                .seasonGoalsHome(18)
                .seasonGoalsAway(14)
                .seasonConcededHome(12)
                .seasonConcededAway(15)
                .seasonOver25PercentageOverall(over25Pct)
                .xgForAvgHome(xg)
                .xgForAvgAway(xg)
                .xgAgainstAvgHome(xg)
                .xgAgainstAvgAway(xg)
                .build();
    }

    private static TeamRecentForm form(double scored, double conceded, Double over25Pct) {
        return TeamRecentForm.builder()
                .scoredAvgHome(scored)
                .scoredAvgAway(scored)
                .concededAvgHome(conceded)
                .concededAvgAway(conceded)
                .over25PercentageOverall(over25Pct)
                .build();
    }

    private static FixtureContext context(TeamSeasonStats home, TeamSeasonStats away, TeamRecentForm[] forms,
                                          FixturePotentials potentials, FixtureOdds odds) {
        return FixtureContext.builder()
                .fixture(Fixture.builder()
                        .id(1000L)
                        .seasonId(100L)
                        .homeTeamId(1L)
                        .awayTeamId(2L)
                        .homeTeamName("Home")
                        .awayTeamName("Away")
                        .dateUnix(System.currentTimeMillis() / 1000 + 86400)
                        .status("incomplete")
                        .build())
                .homeTeam(Team.builder().id(1L).name("Home").build())
                .awayTeam(Team.builder().id(2L).name("Away").build())
                .homeTeamStats(home)
                .awayTeamStats(away)
                .homeTeamForm(forms == null ? null : forms[0])
                .awayTeamForm(forms == null ? null : forms[1])
                .potentials(potentials)
                .odds(odds)
                .build();
    }
}
