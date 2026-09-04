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
 *
 * <p>The score is the modelled probability that the fixture clears the line, blended from two
 * estimates of the same event: a Poisson tail on expected goals, and the empirical rate at which
 * these two teams and the data provider say the line goes over.
 *
 * <p>This replaces an additive index that summed rescaled goal averages, over-line percentages and
 * an API potential, then added a high-scoring boost, an xG boost and an expected-goals lift on
 * top. All three boosts measured the same thing the index already contained — goal volume — so a
 * busy fixture was counted three times and could add 27 points to an already-high base. The result
 * saturated at the 100 clamp, and because the Elite board breaks ties on shortest price, a pile of
 * fixtures tied at 100 handed the board to whichever had the least generous odds.
 */
@Slf4j
public abstract class TotalGoalsOverRecommendationEngine implements RecommendationEngine {

    /** Split between the Poisson estimate and the empirical over-rates. */
    private static final double WEIGHT_POISSON = 0.50;

    // Contributions to expected goals, renormalised over whichever inputs are present.
    private static final double WEIGHT_LAMBDA_SEASON = 0.50;
    private static final double WEIGHT_LAMBDA_XG = 0.30;
    private static final double WEIGHT_LAMBDA_FORM = 0.20;

    // Contributions to the empirical over-rate, renormalised over whichever inputs are present.
    private static final double WEIGHT_EMPIRICAL_TEAM_RATE = 0.30;
    private static final double WEIGHT_EMPIRICAL_API = 0.40;

    protected record LineSpec(
            RecommendationType type,
            String market,
            String overPctFactorKeyPrefix,
            String apiPotentialFactorKey,
            double filterMinExpectedGoals,
            double thresholdStrong,
            double thresholdModerate,
            double line
    ) {
        /** Goals needed to clear the line: 2 for Over 1.5, 3 for Over 2.5. */
        int goalsNeeded() {
            return (int) Math.ceil(line);
        }
    }

    protected abstract LineSpec spec();

    protected abstract Double seasonOverPercentage(TeamSeasonStats stats);

    protected abstract Double formOverPercentage(TeamRecentForm form);

    protected abstract Double apiPotential(FixtureContext context);

    protected abstract Double oddsForMarket(FixtureContext context);

    /** Override to require a backable price. Default publishes priced and unpriced alike. */
    protected boolean passesOddsGate(Double odds) {
        return true;
    }

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

        double expectedGoals = calculateExpectedGoals(context);
        double homeOverPct = blendedOverPercentage(homeStats, context.hasRecentForm() ? context.getHomeTeamForm() : null);
        double awayOverPct = blendedOverPercentage(awayStats, context.hasRecentForm() ? context.getAwayTeamForm() : null);

        if (expectedGoals < spec.filterMinExpectedGoals()) {
            log.debug("Fixture failed {} filter: fixtureId={}, expectedGoals={}",
                    spec.market(), context.getFixture().getId(), expectedGoals);
            return Optional.empty();
        }

        double score = calculateScore(context, spec, homeOverPct, awayOverPct, expectedGoals);
        ConfidenceLevel confidence = determineConfidence(score, spec);
        if (confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        Double odds = oddsForMarket(context);
        if (!passesOddsGate(odds)) {
            return Optional.empty();
        }

        Map<String, Object> factors = buildFactors(context, spec, score, expectedGoals, homeOverPct, awayOverPct);

        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(spec.type())
                .confidence(confidence)
                .score(score)
                .market(spec.market())
                .odds(odds)
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

    /**
     * Expected total goals for the fixture, combining season record, xG and recent form. Every
     * goal-volume signal now feeds this one number rather than being added separately to the
     * score, which is what stopped the same evidence being counted three times over.
     */
    private double calculateExpectedGoals(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double homeScoredAvg = calculateVenueGoalsAvg(homeStats, true, 1.0);
        double awayScoredAvg = calculateVenueGoalsAvg(awayStats, false, 1.0);
        double homeConcededAvg = calculateVenueConcededAvg(homeStats, true, 1.0);
        double awayConcededAvg = calculateVenueConcededAvg(awayStats, false, 1.0);
        double seasonLambda = (homeScoredAvg + awayScoredAvg + homeConcededAvg + awayConcededAvg) / 2.0;

        double weightedSum = seasonLambda * WEIGHT_LAMBDA_SEASON;
        double weightUsed = WEIGHT_LAMBDA_SEASON;

        Double homeXgFor = homeStats.getXgForAvgHome();
        Double awayXgFor = awayStats.getXgForAvgAway();
        Double homeXgAgainst = homeStats.getXgAgainstAvgHome();
        Double awayXgAgainst = awayStats.getXgAgainstAvgAway();
        if (homeXgFor != null && awayXgFor != null && homeXgAgainst != null && awayXgAgainst != null) {
            double xgLambda = (homeXgFor + awayXgFor + homeXgAgainst + awayXgAgainst) / 2.0;
            weightedSum += xgLambda * WEIGHT_LAMBDA_XG;
            weightUsed += WEIGHT_LAMBDA_XG;
        }

        if (context.hasRecentForm()) {
            TeamRecentForm homeForm = context.getHomeTeamForm();
            TeamRecentForm awayForm = context.getAwayTeamForm();
            double formLambda = (safeDouble(homeForm.getScoredAvgHome(), homeScoredAvg)
                    + safeDouble(awayForm.getScoredAvgAway(), awayScoredAvg)
                    + safeDouble(homeForm.getConcededAvgHome(), homeConcededAvg)
                    + safeDouble(awayForm.getConcededAvgAway(), awayConcededAvg)) / 2.0;
            weightedSum += formLambda * WEIGHT_LAMBDA_FORM;
            weightUsed += WEIGHT_LAMBDA_FORM;
        }

        return weightedSum / weightUsed;
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
        return (seasonPct * 0.5) + (normalizePercentage(formPct) * 0.5);
    }

    /**
     * Blend two independent estimates of the same probability: a Poisson tail on expected goals,
     * and the rate at which these teams (and the data provider) actually clear the line.
     */
    private double calculateScore(
            FixtureContext context,
            LineSpec spec,
            double homeOverPct,
            double awayOverPct,
            double expectedGoals) {
        double poisson = poissonAtLeast(expectedGoals, spec.goalsNeeded());
        double empirical = empiricalOverPercentage(context, homeOverPct, awayOverPct);
        return clampScore((poisson * WEIGHT_POISSON) + (empirical * (1.0 - WEIGHT_POISSON)));
    }

    /**
     * The observed side of the estimate: how often these two teams clear the line, plus the data
     * provider's own potential for this fixture. Both are already probabilities of the event, so
     * they combine directly rather than being rescaled onto an arbitrary axis.
     */
    private double empiricalOverPercentage(FixtureContext context, double homeOverPct, double awayOverPct) {
        double teamRate = (homeOverPct + awayOverPct) / 2.0;
        double weightedSum = teamRate * WEIGHT_EMPIRICAL_TEAM_RATE;
        double weightUsed = WEIGHT_EMPIRICAL_TEAM_RATE;

        Double potential = apiPotential(context);
        if (potential != null) {
            weightedSum += normalizePercentage(potential) * WEIGHT_EMPIRICAL_API;
            weightUsed += WEIGHT_EMPIRICAL_API;
        }

        return weightedSum / weightUsed;
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

        double homeScoredAvg = calculateVenueGoalsAvg(homeStats, true, 1.0);
        double awayScoredAvg = calculateVenueGoalsAvg(awayStats, false, 1.0);
        double homeConcededAvg = calculateVenueConcededAvg(homeStats, true, 1.0);
        double awayConcededAvg = calculateVenueConcededAvg(awayStats, false, 1.0);

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
            factors.put(spec.apiPotentialFactorKey(), normalizePercentage(potential));
        }

        factors.put("poissonProbability", poissonAtLeast(expectedGoals, spec.goalsNeeded()));
        factors.put("empiricalOverPct", empiricalOverPercentage(context, homeOverPct, awayOverPct));
        factors.put("goalsNeeded", spec.goalsNeeded());

        double combinedGoalsAvg = (homeScoredAvg + awayScoredAvg + homeConcededAvg + awayConcededAvg) / 2.0;
        factors.put("combinedGoalsAvg", combinedGoalsAvg);

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
        if (xgAvailable) {
            factors.put("combinedXg", homeXgFor + awayXgFor);
        }

        factors.put("calculatedScore", score);
        return factors;
    }
}
