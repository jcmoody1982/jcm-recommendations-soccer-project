package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.core.recommendation.util.RecommendationFactory;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.*;

/**
 * Shared Over X.5 total-goals engine. Line-specific filters, rates, and odds
 * come from {@link LineSpec} (UC-038 Over 1.5, UC-039 Over 2.5).
 */
@Slf4j
public abstract class TotalGoalsOverRecommendationEngine implements RecommendationEngine {

    private static final double WEIGHT_HOME_SCORED_SEASON = 0.07;
    private static final double WEIGHT_AWAY_SCORED_SEASON = 0.07;
    private static final double WEIGHT_HOME_CONCEDED_SEASON = 0.07;
    private static final double WEIGHT_AWAY_CONCEDED_SEASON = 0.07;
    private static final double WEIGHT_HOME_SCORED_FORM = 0.10;
    private static final double WEIGHT_AWAY_SCORED_FORM = 0.10;
    private static final double WEIGHT_HOME_CONCEDED_FORM = 0.07;
    private static final double WEIGHT_AWAY_CONCEDED_FORM = 0.07;
    private static final double WEIGHT_HOME_OVER_LINE = 0.12;
    private static final double WEIGHT_AWAY_OVER_LINE = 0.12;
    private static final double WEIGHT_API_POTENTIAL = 0.14;

    private static final double WEIGHT_SCORED_SEASON_NO_FORM = 0.13;
    private static final double WEIGHT_CONCEDED_SEASON_NO_FORM = 0.10;
    private static final double WEIGHT_OVER_LINE_NO_FORM = 0.16;
    private static final double WEIGHT_API_POTENTIAL_NO_FORM = 0.22;

    protected record LineSpec(
            RecommendationType type,
            String market,
            String overPctFactorKeyPrefix,
            String apiPotentialFactorKey,
            double filterMinExpectedGoals,
            double filterMinAvgOverPct,
            double thresholdStrong,
            double thresholdModerate,
            double highScoringCombinedThreshold,
            double highScoringBoost,
            double xgCombinedThreshold,
            double xgBoost
    ) {}

    protected abstract LineSpec spec();

    protected abstract Double seasonOverPercentage(TeamSeasonStats stats);

    protected abstract Double formOverPercentage(TeamRecentForm form);

    protected abstract Double apiPotential(FixtureContext context);

    protected abstract Double oddsForMarket(FixtureContext context);

    @Override
    public RecommendationType getType() {
        return spec().type();
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        LineSpec spec = spec();
        log.debug("Analyzing {} for fixture: fixtureId={}, {} vs {}",
                spec.market(),
                context.getFixture().getId(),
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());

        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double expectedGoals = calculateExpectedGoals(homeStats, awayStats);
        double homeOverPct = blendedOverPercentage(homeStats, context.hasRecentForm() ? context.getHomeTeamForm() : null);
        double awayOverPct = blendedOverPercentage(awayStats, context.hasRecentForm() ? context.getAwayTeamForm() : null);
        double avgOverPct = (homeOverPct + awayOverPct) / 2.0;

        if (expectedGoals < spec.filterMinExpectedGoals() || avgOverPct < spec.filterMinAvgOverPct()) {
            log.debug("Fixture failed {} filter: fixtureId={}, expectedGoals={}, avgOverPct={}",
                    spec.market(), context.getFixture().getId(), expectedGoals, avgOverPct);
            return Optional.empty();
        }

        double score = calculateScore(context, spec, homeOverPct, awayOverPct);
        ConfidenceLevel confidence = determineConfidence(score, spec);
        if (confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        Map<String, Object> factors = buildFactors(context, spec, score, expectedGoals, homeOverPct, awayOverPct);

        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(spec.type())
                .confidence(confidence)
                .score(score)
                .market(spec.market())
                .odds(oddsForMarket(context))
                .description(RecommendationFactory.buildExpectedValueDescription(
                        confidence, spec.market(), expectedGoals, "expected goals", context))
                .factors(factors)
                .build();

        log.info("{} recommendation generated: fixtureId={}, expectedGoals={}, score={}, confidence={}",
                spec.market(),
                context.getFixture().getId(),
                String.format("%.2f", expectedGoals),
                String.format("%.1f", score),
                confidence);

        return Optional.of(recommendation);
    }

    private double calculateExpectedGoals(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        double homeScoredAvg = calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 1.0);
        double awayScoredAvg = calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 1.0);
        double homeConcededAvg = calculateGoalsAvg(homeStats.getSeasonConcededHome(), homeStats.getMatchesPlayed(), 1.0);
        double awayConcededAvg = calculateGoalsAvg(awayStats.getSeasonConcededAway(), awayStats.getMatchesPlayed(), 1.0);

        double actualGoalsExpected = (homeScoredAvg + awayScoredAvg + homeConcededAvg + awayConcededAvg) / 2.0;

        Double homeXgFor = homeStats.getXgForAvgHome();
        Double awayXgFor = awayStats.getXgForAvgAway();
        Double homeXgAgainst = homeStats.getXgAgainstAvgHome();
        Double awayXgAgainst = awayStats.getXgAgainstAvgAway();

        if (homeXgFor != null && awayXgFor != null && homeXgAgainst != null && awayXgAgainst != null) {
            double xgExpected = (homeXgFor + awayXgFor + homeXgAgainst + awayXgAgainst) / 2.0;
            return (actualGoalsExpected * 0.6) + (xgExpected * 0.4);
        }

        return actualGoalsExpected;
    }

    private double blendedOverPercentage(TeamSeasonStats stats, TeamRecentForm form) {
        double seasonPct = safePercentage(seasonOverPercentage(stats));
        if (form == null) {
            return seasonPct;
        }
        Double formPct = formOverPercentage(form);
        if (formPct == null) {
            return seasonPct;
        }
        return (seasonPct * 0.5) + (formPct * 0.5);
    }

    private double calculateScore(FixtureContext context, LineSpec spec, double homeOverPct, double awayOverPct) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double homeScoredSeason = normalizeGoals(calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 1.0));
        double awayScoredSeason = normalizeGoals(calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 1.0));
        double homeConcededSeason = normalizeGoals(calculateGoalsAvg(homeStats.getSeasonConcededHome(), homeStats.getMatchesPlayed(), 1.0));
        double awayConcededSeason = normalizeGoals(calculateGoalsAvg(awayStats.getSeasonConcededAway(), awayStats.getMatchesPlayed(), 1.0));

        double api = 50.0;
        Double potential = apiPotential(context);
        if (potential != null) {
            api = potential;
        }

        double score;
        if (context.hasRecentForm()) {
            double homeScoredForm = normalizeGoals(safeDouble(context.getHomeTeamForm().getScoredAvgHome(), 1.0));
            double awayScoredForm = normalizeGoals(safeDouble(context.getAwayTeamForm().getScoredAvgAway(), 1.0));
            double homeConcededForm = normalizeGoals(safeDouble(context.getHomeTeamForm().getConcededAvgHome(), 1.0));
            double awayConcededForm = normalizeGoals(safeDouble(context.getAwayTeamForm().getConcededAvgAway(), 1.0));

            score = (homeScoredSeason * WEIGHT_HOME_SCORED_SEASON)
                    + (awayScoredSeason * WEIGHT_AWAY_SCORED_SEASON)
                    + (homeConcededSeason * WEIGHT_HOME_CONCEDED_SEASON)
                    + (awayConcededSeason * WEIGHT_AWAY_CONCEDED_SEASON)
                    + (homeScoredForm * WEIGHT_HOME_SCORED_FORM)
                    + (awayScoredForm * WEIGHT_AWAY_SCORED_FORM)
                    + (homeConcededForm * WEIGHT_HOME_CONCEDED_FORM)
                    + (awayConcededForm * WEIGHT_AWAY_CONCEDED_FORM)
                    + (homeOverPct * WEIGHT_HOME_OVER_LINE)
                    + (awayOverPct * WEIGHT_AWAY_OVER_LINE)
                    + (api * WEIGHT_API_POTENTIAL);
        } else {
            score = (homeScoredSeason * WEIGHT_SCORED_SEASON_NO_FORM)
                    + (awayScoredSeason * WEIGHT_SCORED_SEASON_NO_FORM)
                    + (homeConcededSeason * WEIGHT_CONCEDED_SEASON_NO_FORM)
                    + (awayConcededSeason * WEIGHT_CONCEDED_SEASON_NO_FORM)
                    + (homeOverPct * WEIGHT_OVER_LINE_NO_FORM)
                    + (awayOverPct * WEIGHT_OVER_LINE_NO_FORM)
                    + (api * WEIGHT_API_POTENTIAL_NO_FORM);
        }

        score += highScoringBoost(homeStats, awayStats, spec);
        score += xgBoost(homeStats, awayStats, spec);
        return clampScore(score);
    }

    private double highScoringBoost(TeamSeasonStats homeStats, TeamSeasonStats awayStats, LineSpec spec) {
        double homeScoredAvg = calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 1.0);
        double awayScoredAvg = calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 1.0);
        double homeConcededAvg = calculateGoalsAvg(homeStats.getSeasonConcededHome(), homeStats.getMatchesPlayed(), 1.0);
        double awayConcededAvg = calculateGoalsAvg(awayStats.getSeasonConcededAway(), awayStats.getMatchesPlayed(), 1.0);
        double combined = (homeScoredAvg + awayScoredAvg + homeConcededAvg + awayConcededAvg) / 2.0;
        return combined >= spec.highScoringCombinedThreshold() ? spec.highScoringBoost() : 0.0;
    }

    private double xgBoost(TeamSeasonStats homeStats, TeamSeasonStats awayStats, LineSpec spec) {
        Double homeXgFor = homeStats != null ? homeStats.getXgForAvgHome() : null;
        Double awayXgFor = awayStats != null ? awayStats.getXgForAvgAway() : null;
        if (homeXgFor == null || awayXgFor == null) {
            return 0.0;
        }
        return (homeXgFor + awayXgFor) >= spec.xgCombinedThreshold() ? spec.xgBoost() : 0.0;
    }

    private ConfidenceLevel determineConfidence(double score, LineSpec spec) {
        if (score >= spec.thresholdStrong()) {
            return ConfidenceLevel.STRONG;
        }
        if (score >= spec.thresholdModerate()) {
            return ConfidenceLevel.MODERATE;
        }
        return ConfidenceLevel.WEAK;
    }

    private Map<String, Object> buildFactors(
            FixtureContext context,
            LineSpec spec,
            double score,
            double expectedGoals,
            double homeOverPct,
            double awayOverPct) {
        Map<String, Object> factors = new HashMap<>();
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        factors.put("expectedGoals", expectedGoals);
        factors.put("line", spec.market());

        double homeScoredAvg = calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 1.0);
        double awayScoredAvg = calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 1.0);
        double homeConcededAvg = calculateGoalsAvg(homeStats.getSeasonConcededHome(), homeStats.getMatchesPlayed(), 1.0);
        double awayConcededAvg = calculateGoalsAvg(awayStats.getSeasonConcededAway(), awayStats.getMatchesPlayed(), 1.0);

        factors.put("homeGoalsScoredAvg", homeScoredAvg);
        factors.put("awayGoalsScoredAvg", awayScoredAvg);
        factors.put("homeGoalsConcededAvg", homeConcededAvg);
        factors.put("awayGoalsConcededAvg", awayConcededAvg);

        factors.put(spec.overPctFactorKeyPrefix() + "Home", homeOverPct);
        factors.put(spec.overPctFactorKeyPrefix() + "Away", awayOverPct);
        factors.put("homeOverSeasonPct", safePercentage(seasonOverPercentage(homeStats)));
        factors.put("awayOverSeasonPct", safePercentage(seasonOverPercentage(awayStats)));

        factors.put("formDataAvailable", context.hasRecentForm());
        if (context.hasRecentForm()) {
            factors.put("homeScoredFormAvg", safeDouble(context.getHomeTeamForm().getScoredAvgHome(), 0.0));
            factors.put("awayScoredFormAvg", safeDouble(context.getAwayTeamForm().getScoredAvgAway(), 0.0));
            factors.put("homeConcededFormAvg", safeDouble(context.getHomeTeamForm().getConcededAvgHome(), 0.0));
            factors.put("awayConcededFormAvg", safeDouble(context.getAwayTeamForm().getConcededAvgAway(), 0.0));
            Double homeFormOver = formOverPercentage(context.getHomeTeamForm());
            Double awayFormOver = formOverPercentage(context.getAwayTeamForm());
            if (homeFormOver != null) {
                factors.put("homeOverFormPct", homeFormOver);
            }
            if (awayFormOver != null) {
                factors.put("awayOverFormPct", awayFormOver);
            }
        }

        Double potential = apiPotential(context);
        if (potential != null) {
            factors.put(spec.apiPotentialFactorKey(), potential);
        }

        double combinedGoalsAvg = (homeScoredAvg + awayScoredAvg + homeConcededAvg + awayConcededAvg) / 2.0;
        factors.put("combinedGoalsAvg", combinedGoalsAvg);
        double highScoringBoost = highScoringBoost(homeStats, awayStats, spec);
        factors.put("highScoringBoostApplied", highScoringBoost > 0);
        if (highScoringBoost > 0) {
            factors.put("highScoringBoostAmount", highScoringBoost);
        }

        Double homeXgFor = homeStats.getXgForAvgHome();
        Double awayXgFor = awayStats.getXgForAvgAway();
        Double homeXgAgainst = homeStats.getXgAgainstAvgHome();
        Double awayXgAgainst = awayStats.getXgAgainstAvgAway();
        boolean xgAvailable = homeXgFor != null && awayXgFor != null;
        factors.put("xgDataAvailable", xgAvailable);
        if (homeXgFor != null) {
            factors.put("homeXgForAvgHome", homeXgFor);
        }
        if (awayXgFor != null) {
            factors.put("awayXgForAvgAway", awayXgFor);
        }
        if (homeXgAgainst != null) {
            factors.put("homeXgAgainstAvgHome", homeXgAgainst);
        }
        if (awayXgAgainst != null) {
            factors.put("awayXgAgainstAvgAway", awayXgAgainst);
        }

        double xgBoost = xgBoost(homeStats, awayStats, spec);
        factors.put("xgBoostApplied", xgBoost > 0);
        if (xgBoost > 0) {
            factors.put("xgBoostAmount", xgBoost);
            factors.put("combinedXg", homeXgFor + awayXgFor);
        }

        factors.put("calculatedScore", score);
        return factors;
    }
}
