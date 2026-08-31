package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.core.recommendation.util.RecommendationFactory;
import com.jcm.recommendations.soccer.core.recommendation.util.MatchBriefCopy;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.*;

/**
 * UC-015: First Half Goals Recommendations
 *
 * Predicts likelihood of goals in the first half (Over 0.5 HT, Over 1.5 HT).
 *
 * Season goals, xG (×0.45 proxy for the 1H share) and provider potentials feed a single
 * expected-first-half-goals figure, which a Poisson tail turns into the probability of the line
 * being marketed. The score published is therefore the probability of that specific line, so
 * Over 1.5 HT is scored as P(2+ HT goals) rather than sharing a scale with Over 0.5 HT.
 */
@Component
@Slf4j
public class FirstHalfGoalsRecommendationEngine implements RecommendationEngine {

    // First half share of total goals (industry standard)
    private static final double FIRST_HALF_RATIO = 0.45;

    /**
     * The two lines this engine can market. The published score is the probability of the line
     * actually being recommended, so each line carries its own thresholds: a first half averages
     * about 1.2 goals, which puts Over 0.5 in the 70-86% band and Over 1.5 in the 30-60% band.
     * One shared threshold cannot describe both, and using the Over 0.5 band for Over 1.5 is what
     * previously let a coin-flip market publish as a near-certainty.
     */
    private enum Line {
        OVER_05(1, "Over 0.5 HT Goals", 78.0, 70.0),
        OVER_15(2, "Over 1.5 HT Goals", 52.0, 44.0);

        private final int goalsNeeded;
        private final String market;
        private final double thresholdStrong;
        private final double thresholdModerate;

        Line(int goalsNeeded, String market, double thresholdStrong, double thresholdModerate) {
            this.goalsNeeded = goalsNeeded;
            this.market = market;
            this.thresholdStrong = thresholdStrong;
            this.thresholdModerate = thresholdModerate;
        }
    }

    // Blend of the Poisson estimate with the provider's potential for the same line
    private static final double WEIGHT_POISSON = 0.65;
    private static final double WEIGHT_API_POTENTIAL = 0.35;

    /**
     * Bounds on the combined expected-goals adjustment. The individual signals below are all
     * variations on "this looks like a lively game", so left unbounded they compound rather than
     * corroborate.
     */
    private static final double LAMBDA_ADJUST_MIN = 0.85;
    private static final double LAMBDA_ADJUST_MAX = 1.20;

    // Over 1.5 HT needs a genuine volume outlier, not merely an above-average fixture
    private static final double OVER_15_MIN_EXPECTED_GOALS = 1.5;
    private static final double OVER_15_MIN_API_POTENTIAL = 50.0;

    // xG-based combined rating bands (reported as match colour)
    private static final double XG_HIGH_THRESHOLD = 3.0;
    private static final double XG_ABOVE_AVG_THRESHOLD = 2.5;
    private static final double XG_LOW_THRESHOLD = 2.0;

    // xG regression: a persistent gap between xG and goals scored points at the finishing rate
    private static final double XG_UNDERPERFORM_FACTOR = 1.10;
    private static final double XG_OVERPERFORM_FACTOR = 0.90;

    /**
     * Weight on the provider's own first-half read when blending expectations. Splitting season
     * totals by a flat {@link #FIRST_HALF_RATIO} cannot tell a fast-starting fixture from a
     * slow-starting one; inverting the provider's Over 0.5 HT potential can.
     */
    private static final double WEIGHT_PROVIDER_EXPECTED = 0.35;

    // Plausible bounds when inverting a potential back into an expectation
    private static final double PROVIDER_EXPECTED_MIN = 0.40;
    private static final double PROVIDER_EXPECTED_MAX = 2.60;

    // Front-loaded / back-loaded bands: provider's first-half read against the flat-split estimate
    private static final double FRONT_LOADED_RATIO = 1.15;
    private static final double BACK_LOADED_RATIO = 0.85;

    // Recent form adjustment (Over 1.5 in the last 5 matches, as a scoring-rate proxy)
    private static final double FORM_HOT_FACTOR = 1.10;
    private static final double FORM_COLD_FACTOR = 0.90;

    // Defensive bands reported as match colour
    private static final double CONCEDING_MATCHUP_GOALS = 2.5;
    private static final double TIGHT_DEFENCE_GOALS = 1.5;

    // Filter: minimum expected 1H goals to generate a recommendation
    private static final double FILTER_MIN_1H_GOALS = 0.8;

    /** The chain from raw stats through to the published probability, kept together for reporting. */
    private record Estimate(
            double statsExpected1HGoals,
            double baseExpected1HGoals,
            double adjustment,
            double expected1HGoals,
            Line line,
            double score) {}

    @Override
    public RecommendationType getType() {
        return RecommendationType.FIRST_HALF_GOALS;
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        log.debug("Analyzing First Half Goals for fixture: fixtureId={}, {} vs {}", 
                context.getFixture().getId(),
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());

        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        // Season and xG totals split by the flat first-half ratio
        double statsExpectedGoals1H = calculateExpected1HGoals(homeStats, awayStats);

        if (statsExpectedGoals1H < FILTER_MIN_1H_GOALS) {
            log.debug("Fixture failed First Half Goals filter: fixtureId={}, expected1HGoals={}", 
                    context.getFixture().getId(), statsExpectedGoals1H);
            return Optional.empty();
        }

        double baseExpectedGoals1H = blendWithProviderRead(statsExpectedGoals1H, context);

        // The signals below describe how many goals to expect, so they adjust the expectation
        // rather than the published probability. Poisson then bounds how far they can move it.
        double adjustment = calculateExpectedGoalsAdjustment(context);
        double expectedGoals1H = baseExpectedGoals1H * adjustment;

        Line line = selectLine(expectedGoals1H, context);
        double score = calculateScore(expectedGoals1H, line, context);

        ConfidenceLevel confidence = determineConfidence(score, line);

        if (confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        Estimate estimate = new Estimate(
                statsExpectedGoals1H, baseExpectedGoals1H, adjustment, expectedGoals1H, line, score);
        Map<String, Object> factors = buildFactors(context, estimate, homeStats, awayStats);

        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(RecommendationType.FIRST_HALF_GOALS)
                .confidence(confidence)
                .score(score)
                .market(line.market)
                .odds(null)  // No HT-specific odds in our data model
                .description(buildDescription(context, confidence, expectedGoals1H, line.market))
                .factors(factors)
                .build();

        log.info("First Half Goals recommendation generated: fixtureId={}, expected1HGoals={}, score={}, confidence={}, market={}", 
                context.getFixture().getId(), String.format("%.2f", expectedGoals1H), 
                String.format("%.1f", score), confidence, line.market);

        return Optional.of(recommendation);
    }

    private double calculateExpected1HGoals(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        double homeScoredAvg = halfScoredAvg(homeStats, true, FIRST_HALF_RATIO);
        double awayScoredAvg = halfScoredAvg(awayStats, false, FIRST_HALF_RATIO);
        double homeConcededAvg = halfConcededAvg(homeStats, true, FIRST_HALF_RATIO);
        double awayConcededAvg = halfConcededAvg(awayStats, false, FIRST_HALF_RATIO);

        double actualExpected = (homeScoredAvg + awayScoredAvg + homeConcededAvg + awayConcededAvg) / 2.0;

        // If xG data is available, blend for more accuracy (still apply 1H ratio to xG share)
        Double homeXgFor = homeStats.getXgForAvgHome();
        Double awayXgFor = awayStats.getXgForAvgAway();
        Double homeXgAgainst = homeStats.getXgAgainstAvgHome();
        Double awayXgAgainst = awayStats.getXgAgainstAvgAway();

        if (homeXgFor != null && awayXgFor != null && homeXgAgainst != null && awayXgAgainst != null) {
            double xgExpected = (homeXgFor + awayXgFor + homeXgAgainst + awayXgAgainst) / 2.0 * FIRST_HALF_RATIO;
            return (actualExpected * 0.6) + (xgExpected * 0.4);
        }

        return actualExpected;
    }

    private double blendWithProviderRead(double statsExpected1HGoals, FixtureContext context) {
        Double providerExpected = providerExpected1HGoals(context);
        if (providerExpected == null) {
            return statsExpected1HGoals;
        }
        return (statsExpected1HGoals * (1.0 - WEIGHT_PROVIDER_EXPECTED))
                + (providerExpected * WEIGHT_PROVIDER_EXPECTED);
    }

    /**
     * Expected first-half goals implied by the provider's Over 0.5 HT potential, recovered by
     * inverting P(1+) = 1 - e^-lambda. Bounded because a potential of 95%+ inverts to an
     * expectation no first half sustains.
     */
    private static Double providerExpected1HGoals(FixtureContext context) {
        Double o05HtPotential = apiPotentialFor(Line.OVER_05, context);
        if (o05HtPotential == null) {
            return null;
        }

        double probability = safePercentage(o05HtPotential) / 100.0;
        if (probability <= 0.0 || probability >= 1.0) {
            return null;
        }

        double implied = -Math.log(1.0 - probability);
        return Math.max(PROVIDER_EXPECTED_MIN, Math.min(PROVIDER_EXPECTED_MAX, implied));
    }

    /**
     * How front-loaded the fixture looks: the provider's first-half read against our flat-ratio
     * split of season totals. Above one means goals arrive earlier than a flat split assumes.
     */
    private static Double frontLoadedRatio(double statsExpected1HGoals, FixtureContext context) {
        Double providerExpected = providerExpected1HGoals(context);
        if (providerExpected == null || statsExpected1HGoals <= 0) {
            return null;
        }
        return providerExpected / statsExpected1HGoals;
    }

    /**
     * Probability that the selected line lands. The Poisson tail over expected first-half goals
     * carries the estimate; the provider's potential for the same line anchors it against an
     * independent read of the same event.
     */
    private double calculateScore(double expectedGoals1H, Line line, FixtureContext context) {
        double poisson = poissonAtLeast(expectedGoals1H, line.goalsNeeded);

        Double apiPotential = apiPotentialFor(line, context);
        if (apiPotential == null) {
            return clampScore(poisson);
        }

        return clampScore((poisson * WEIGHT_POISSON) + (safePercentage(apiPotential) * WEIGHT_API_POTENTIAL));
    }

    private Line selectLine(double expectedGoals1H, FixtureContext context) {
        if (expectedGoals1H < OVER_15_MIN_EXPECTED_GOALS) {
            return Line.OVER_05;
        }

        Double o15HtPotential = apiPotentialFor(Line.OVER_15, context);
        if (o15HtPotential != null && o15HtPotential < OVER_15_MIN_API_POTENTIAL) {
            return Line.OVER_05;
        }

        return Line.OVER_15;
    }

    private static Double apiPotentialFor(Line line, FixtureContext context) {
        if (!context.hasPotentials()) {
            return null;
        }
        return line == Line.OVER_15
                ? context.getPotentials().getO15HtPotential()
                : context.getPotentials().getO05HtPotential();
    }

    /**
     * Combined multiplier on expected first-half goals. Only signals carrying information the
     * expectation does not already hold are included: the xG level, the conceded averages and the
     * provider's first-half read are all inputs to the expectation itself, so re-applying them
     * here would count them twice.
     */
    private double calculateExpectedGoalsAdjustment(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double adjustment = xgRegressionFactor(homeStats, awayStats) * recentFormFactor(context);

        return Math.max(LAMBDA_ADJUST_MIN, Math.min(LAMBDA_ADJUST_MAX, adjustment));
    }

    private double xgRegressionFactor(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        if (!hasXgData(homeStats, awayStats)) {
            return 1.0;
        }

        if (bothUnderperformingXg(homeStats, awayStats)) {
            log.debug("Both teams underperforming xG - regression suggests more goals likely");
            return XG_UNDERPERFORM_FACTOR;
        }
        if (bothOverperformingXg(homeStats, awayStats)) {
            log.debug("Both teams overperforming xG - regression suggests fewer goals");
            return XG_OVERPERFORM_FACTOR;
        }

        return 1.0;
    }

    private double recentFormFactor(FixtureContext context) {
        Integer totalO15InForm = totalOver15InForm(context);
        if (totalO15InForm == null) {
            return 1.0;
        }

        if (totalO15InForm >= 8) {
            log.debug("Hot 1H form detected: totalO15InForm={}", totalO15InForm);
            return FORM_HOT_FACTOR;
        }
        if (totalO15InForm <= 4) {
            log.debug("Cold 1H form detected: totalO15InForm={}", totalO15InForm);
            return FORM_COLD_FACTOR;
        }

        return 1.0;
    }

    private static Integer totalOver15InForm(FixtureContext context) {
        if (!context.hasRecentForm()) {
            return null;
        }
        TeamRecentForm homeForm = context.getHomeTeamForm();
        TeamRecentForm awayForm = context.getAwayTeamForm();
        if (homeForm == null || awayForm == null) {
            return null;
        }
        return safeInt(homeForm.getOver15Overall()) + safeInt(awayForm.getOver15Overall());
    }

    private static boolean bothUnderperformingXg(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        return isUnderperformingXg(homeStats, true) && isUnderperformingXg(awayStats, false);
    }

    private static boolean bothOverperformingXg(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        return isOverperformingXg(homeStats, true) && isOverperformingXg(awayStats, false);
    }

    private static boolean isUnderperformingXg(TeamSeasonStats stats, boolean home) {
        double xg = safeDouble(home ? stats.getXgForAvgHome() : stats.getXgForAvgAway());
        return xg > 0 && venueGoalsAvg(stats, home) < xg * 0.85;
    }

    private static boolean isOverperformingXg(TeamSeasonStats stats, boolean home) {
        double xg = safeDouble(home ? stats.getXgForAvgHome() : stats.getXgForAvgAway());
        return xg > 0 && venueGoalsAvg(stats, home) > xg * 1.15;
    }

    private static double venueGoalsAvg(TeamSeasonStats stats, boolean home) {
        Integer goals = home ? stats.getSeasonGoalsHome() : stats.getSeasonGoalsAway();
        return calculateGoalsAvg(goals, stats.getMatchesPlayed(), 1.0);
    }

    private boolean hasXgData(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        return homeStats.getXgForAvgHome() != null 
                && awayStats.getXgForAvgAway() != null
                && homeStats.getXgAgainstAvgHome() != null 
                && awayStats.getXgAgainstAvgAway() != null;
    }

    private ConfidenceLevel determineConfidence(double score, Line line) {
        if (score >= line.thresholdStrong) {
            return ConfidenceLevel.STRONG;
        } else if (score >= line.thresholdModerate) {
            return ConfidenceLevel.MODERATE;
        }
        return ConfidenceLevel.WEAK;
    }

    private Map<String, Object> buildFactors(FixtureContext context, Estimate estimate,
            TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        Map<String, Object> factors = new HashMap<>();

        double statsExpected1HGoals = estimate.statsExpected1HGoals();
        double expected1HGoals = estimate.expected1HGoals();
        Line line = estimate.line();

        // Expected goals
        factors.put("expected1HGoals", expected1HGoals);
        factors.put("statsExpected1HGoals", statsExpected1HGoals);
        factors.put("baseExpected1HGoals", estimate.baseExpected1HGoals());
        factors.put("expectedGoalsAdjustment", estimate.adjustment());
        factors.put("firstHalfRatioUsed", FIRST_HALF_RATIO);
        factors.put("halfStatsFromApi", hasHalfGoalStats(homeStats, awayStats));

        // How the published probability was arrived at
        factors.put("line", line.market);
        factors.put("goalsNeeded", line.goalsNeeded);
        factors.put("poissonProbability", poissonAtLeast(expected1HGoals, line.goalsNeeded));
        Double linePotential = apiPotentialFor(line, context);
        if (linePotential != null) {
            factors.put("apiPotentialForLine", safePercentage(linePotential));
        }
        Double providerExpected = providerExpected1HGoals(context);
        if (providerExpected != null) {
            factors.put("providerExpected1HGoals", providerExpected);
        }

        // Full-time expected for reference
        factors.put("expectedFullTimeGoals", expected1HGoals / FIRST_HALF_RATIO);
        
        double home1HScoredProxy = halfScoredAvg(homeStats, true, FIRST_HALF_RATIO);
        double away1HScoredProxy = halfScoredAvg(awayStats, false, FIRST_HALF_RATIO);
        double home1HConcededProxy = halfConcededAvg(homeStats, true, FIRST_HALF_RATIO);
        double away1HConcededProxy = halfConcededAvg(awayStats, false, FIRST_HALF_RATIO);
        
        factors.put("home1HScoredProxyAvg", home1HScoredProxy);
        factors.put("away1HScoredProxyAvg", away1HScoredProxy);
        factors.put("home1HConcededProxyAvg", home1HConcededProxy);
        factors.put("away1HConcededProxyAvg", away1HConcededProxy);
        
        // BTTS HT
        factors.put("homeBttsSeasonPct", halfBttsPercentage(homeStats, true));
        factors.put("awayBttsSeasonPct", halfBttsPercentage(awayStats, false));

        // API potentials
        if (context.hasPotentials()) {
            if (context.getPotentials().getO05HtPotential() != null) {
                factors.put("apiO05HtPotential", context.getPotentials().getO05HtPotential());
            }
            if (context.getPotentials().getO15HtPotential() != null) {
                factors.put("apiO15HtPotential", context.getPotentials().getO15HtPotential());
            }
        }

        // xG data
        boolean xgAvailable = hasXgData(homeStats, awayStats);
        factors.put("xgDataAvailable", xgAvailable);
        
        if (xgAvailable) {
            double homeXg = safeDouble(homeStats.getXgForAvgHome());
            double awayXg = safeDouble(awayStats.getXgForAvgAway());
            double homeXga = safeDouble(homeStats.getXgAgainstAvgHome());
            double awayXga = safeDouble(awayStats.getXgAgainstAvgAway());
            
            factors.put("homeXgForAvgHome", homeXg);
            factors.put("awayXgForAvgAway", awayXg);
            factors.put("homeXgAgainstAvgHome", homeXga);
            factors.put("awayXgAgainstAvgAway", awayXga);
            factors.put("combinedXg", homeXg + awayXg);
            factors.put("home1HXgProxy", homeXg * FIRST_HALF_RATIO);
            factors.put("away1HXgProxy", awayXg * FIRST_HALF_RATIO);
            
            // xG rating
            double combinedXg = homeXg + awayXg;
            String xgRating;
            if (combinedXg > XG_HIGH_THRESHOLD) {
                xgRating = "High-scoring potential";
            } else if (combinedXg >= XG_ABOVE_AVG_THRESHOLD) {
                xgRating = "Above average";
            } else if (combinedXg < XG_LOW_THRESHOLD) {
                xgRating = "Low-scoring potential";
            } else {
                xgRating = "Average";
            }
            factors.put("xgRating", xgRating);
            
            // xG regression analysis
            boolean homeUnderperforming = isUnderperformingXg(homeStats, true);
            boolean awayUnderperforming = isUnderperformingXg(awayStats, false);
            boolean homeOverperforming = isOverperformingXg(homeStats, true);
            boolean awayOverperforming = isOverperformingXg(awayStats, false);
            
            factors.put("homeXgPerformance", homeUnderperforming ? "Underperforming" : 
                    (homeOverperforming ? "Overperforming" : "On track"));
            factors.put("awayXgPerformance", awayUnderperforming ? "Underperforming" : 
                    (awayOverperforming ? "Overperforming" : "On track"));
            
            if (homeUnderperforming && awayUnderperforming) {
                factors.put("xgRegressionOutlook", "Positive - both teams due for more goals");
            } else if (homeOverperforming && awayOverperforming) {
                factors.put("xgRegressionOutlook", "Negative - both teams may regress to fewer goals");
            }
        }

        // Fast/slow starter indicators
        List<String> positiveIndicators = new ArrayList<>();
        List<String> riskFlags = new ArrayList<>();
        
        Double frontLoadedRatio = frontLoadedRatio(statsExpected1HGoals, context);
        if (frontLoadedRatio != null) {
            factors.put("frontLoadedRatio", frontLoadedRatio);

            if (frontLoadedRatio > FRONT_LOADED_RATIO) {
                factors.put("fastStarterStatus", "Fast starters detected");
                positiveIndicators.add("Fast starter teams");
            } else if (frontLoadedRatio < BACK_LOADED_RATIO) {
                factors.put("fastStarterStatus", "Slow starters detected");
                riskFlags.add("Slow starter teams");
            }
        }
        
        // Early conceder analysis (full-time conceded as defensive vulnerability signal)
        double combinedConceded = calculateGoalsAvg(homeStats.getSeasonConcededHome(), homeStats.getMatchesPlayed(), 1.0)
                + calculateGoalsAvg(awayStats.getSeasonConcededAway(), awayStats.getMatchesPlayed(), 1.0);
        factors.put("combinedConcededAvg", combinedConceded);
        
        if (combinedConceded > CONCEDING_MATCHUP_GOALS) {
            factors.put("earlyConcedeStatus", "Both teams vulnerable defensively");
            positiveIndicators.add("High-conceding matchup");
        } else if (combinedConceded < TIGHT_DEFENCE_GOALS) {
            factors.put("earlyConcedeStatus", "Strong combined defense");
            riskFlags.add("Low-conceding matchup");
        }
        
        // Recent form
        factors.put("formDataAvailable", context.hasRecentForm());
        if (context.hasRecentForm()) {
            TeamRecentForm homeForm = context.getHomeTeamForm();
            TeamRecentForm awayForm = context.getAwayTeamForm();
            
            if (homeForm != null && awayForm != null) {
                int homeO15Form = safeInt(homeForm.getOver15Overall());
                int awayO15Form = safeInt(awayForm.getOver15Overall());
                
                factors.put("homeO15RecentForm", homeO15Form);
                factors.put("awayO15RecentForm", awayO15Form);
                factors.put("totalO15InForm", homeO15Form + awayO15Form);
                
                if (homeO15Form + awayO15Form >= 8) {
                    factors.put("recentFormStatus", "Hot - high-scoring recent form");
                    positiveIndicators.add("High-scoring recent form");
                } else if (homeO15Form + awayO15Form <= 4) {
                    factors.put("recentFormStatus", "Cold - low-scoring recent form");
                    riskFlags.add("Low-scoring recent form");
                }
            }
        }
        
        factors.put("positiveIndicators", positiveIndicators);
        factors.put("riskFlags", riskFlags);
        factors.put("calculatedScore", estimate.score());
        
        return factors;
    }

    private String buildDescription(FixtureContext context, ConfidenceLevel confidence, 
            double expected1HGoals, String market) {
        StringBuilder colour = new StringBuilder();

        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        if (hasXgData(homeStats, awayStats)) {
            double combinedXg = safeDouble(homeStats.getXgForAvgHome()) + safeDouble(awayStats.getXgForAvgAway());
            if (combinedXg > XG_HIGH_THRESHOLD) {
                colour.append(String.format("High combined xG (%.2f)", combinedXg));
            }
        }

        if (context.hasPotentials() && context.getPotentials().getO05HtPotential() != null) {
            double o05Ht = context.getPotentials().getO05HtPotential();
            if (o05Ht >= 70.0) {
                if (!colour.isEmpty()) {
                    colour.append(". ");
                }
                colour.append(String.format("Analysis rates first-half goal potential at %.0f%%", o05Ht));
            }
        }

        return MatchBriefCopy.narrate(MatchBriefCopy.Brief.builder()
                .confidence(confidence)
                .selection(market)
                .context(context)
                .expected(expected1HGoals, "expected 1H goals")
                .colourNote(colour.isEmpty() ? "First-half goals look lively on the team brief" : colour.toString())
                .build());
    }

    private static double halfScoredAvg(TeamSeasonStats stats, boolean home, double fallbackRatio) {
        Double halfAvg = home ? stats.getScoredAvgHtHome() : stats.getScoredAvgHtAway();
        if (halfAvg != null) {
            return halfAvg;
        }
        Integer goals = home ? stats.getSeasonGoalsHome() : stats.getSeasonGoalsAway();
        return calculateGoalsAvg(goals, stats.getMatchesPlayed(), 1.0) * fallbackRatio;
    }

    private static double halfConcededAvg(TeamSeasonStats stats, boolean home, double fallbackRatio) {
        Double halfAvg = home ? stats.getConcededAvgHtHome() : stats.getConcededAvgHtAway();
        if (halfAvg != null) {
            return halfAvg;
        }
        Integer conceded = home ? stats.getSeasonConcededHome() : stats.getSeasonConcededAway();
        return calculateGoalsAvg(conceded, stats.getMatchesPlayed(), 1.0) * fallbackRatio;
    }

    private static double halfBttsPercentage(TeamSeasonStats stats, boolean home) {
        Double halfPct = home ? stats.getBttsFhgPercentageHome() : stats.getBttsFhgPercentageAway();
        if (halfPct != null) {
            return safePercentage(halfPct);
        }
        Double seasonPct = home ? stats.getSeasonBttsPercentageHome() : stats.getSeasonBttsPercentageAway();
        return safePercentage(seasonPct);
    }

    private static boolean hasHalfGoalStats(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        return homeStats.getScoredAvgHtHome() != null
                && awayStats.getScoredAvgHtAway() != null
                && homeStats.getConcededAvgHtHome() != null
                && awayStats.getConcededAvgHtAway() != null;
    }
}
