package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.core.recommendation.util.RecommendationFactory;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.*;

@Component
@Slf4j
public class UnderGoalsRecommendationEngine implements RecommendationEngine {

    // Base weights when form data IS available (total = 1.0)
    private static final double WEIGHT_HOME_SCORED_INVERSE = 0.07;
    private static final double WEIGHT_AWAY_SCORED_INVERSE = 0.07;
    private static final double WEIGHT_HOME_CONCEDED_INVERSE = 0.07;
    private static final double WEIGHT_AWAY_CONCEDED_INVERSE = 0.07;
    private static final double WEIGHT_HOME_SCORED_FORM_INVERSE = 0.10;
    private static final double WEIGHT_AWAY_SCORED_FORM_INVERSE = 0.10;
    private static final double WEIGHT_HOME_CONCEDED_FORM_INVERSE = 0.06;
    private static final double WEIGHT_AWAY_CONCEDED_FORM_INVERSE = 0.06;
    private static final double WEIGHT_HOME_CLEANSHEET = 0.08;
    private static final double WEIGHT_AWAY_CLEANSHEET = 0.08;
    private static final double WEIGHT_HOME_FTS = 0.06;
    private static final double WEIGHT_AWAY_FTS = 0.06;
    private static final double WEIGHT_API_POTENTIAL = 0.12;

    // Redistributed weights when form data is NOT available (total = 1.0)
    private static final double WEIGHT_SCORED_INVERSE_NO_FORM = 0.12;    // 0.07 → 0.12 each
    private static final double WEIGHT_CONCEDED_INVERSE_NO_FORM = 0.10;  // 0.07 → 0.10 each
    private static final double WEIGHT_CLEANSHEET_NO_FORM = 0.12;        // 0.08 → 0.12 each
    private static final double WEIGHT_FTS_NO_FORM = 0.10;               // 0.06 → 0.10 each
    private static final double WEIGHT_API_POTENTIAL_NO_FORM = 0.22;     // 0.12 → 0.22

    // Low-scoring context boost
    private static final double LOW_SCORING_COMBINED_THRESHOLD = 2.0;    // Combined goals avg
    private static final double LOW_SCORING_BOOST_AMOUNT = 5.0;          // Bonus percentage points

    // Defensive strength boost (both teams keep clean sheets often)
    private static final double DEFENSIVE_STRENGTH_CS_THRESHOLD = 30.0;  // Clean sheet % threshold
    private static final double DEFENSIVE_STRENGTH_BOOST_AMOUNT = 4.0;   // Bonus percentage points

    // xG boost for low expected goals
    private static final double XG_LOW_COMBINED_THRESHOLD = 2.2;         // Combined xG threshold
    private static final double XG_LOW_BOOST_AMOUNT = 4.0;               // Bonus percentage points

    private static final double THRESHOLD_STRONG = 80.0;
    private static final double THRESHOLD_MODERATE = 65.0;
    
    private static final double FILTER_MAX_COMBINED_GOALS = 2.5;

    @Override
    public RecommendationType getType() {
        return RecommendationType.UNDER_GOALS;
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        log.debug("Analyzing Under Goals for fixture: fixtureId={}, {} vs {}", 
                context.getFixture().getId(),
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());

        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double expectedGoals = calculateExpectedGoals(homeStats, awayStats);
        
        if (expectedGoals > FILTER_MAX_COMBINED_GOALS) {
            log.debug("Fixture failed Under Goals filter: fixtureId={}, expectedGoals={}", 
                    context.getFixture().getId(), expectedGoals);
            return Optional.empty();
        }

        double score = calculateScore(context);
        ConfidenceLevel confidence = determineConfidence(score);

        if (confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        String market = determineMarket(context, expectedGoals, score);
        Double odds = getOddsForMarket(context, market);
        Map<String, Object> factors = buildFactors(context, score, expectedGoals);

        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(RecommendationType.UNDER_GOALS)
                .confidence(confidence)
                .score(score)
                .market(market)
                .odds(odds)
                .description(buildDescription(context, confidence, expectedGoals, market))
                .factors(factors)
                .build();

        log.info("Under Goals recommendation generated: fixtureId={}, expectedGoals={}, score={}, confidence={}, market={}", 
                context.getFixture().getId(), String.format("%.2f", expectedGoals), 
                String.format("%.1f", score), confidence, market);

        return Optional.of(recommendation);
    }

    private double calculateExpectedGoals(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        // Calculate from actual goals
        double homeScoredAvg = calculateVenueGoalsAvg(homeStats, true, 1.0);
        double awayScoredAvg = calculateVenueGoalsAvg(awayStats, false, 1.0);
        double homeConcededAvg = calculateVenueConcededAvg(homeStats, true, 1.0);
        double awayConcededAvg = calculateVenueConcededAvg(awayStats, false, 1.0);

        double actualGoalsExpected = (homeScoredAvg + awayScoredAvg + homeConcededAvg + awayConcededAvg) / 2.0;

        // If xG data is available, blend with xG for more accurate prediction
        Double homeXgFor = homeStats.getXgForAvgHome();
        Double awayXgFor = awayStats.getXgForAvgAway();
        Double homeXgAgainst = homeStats.getXgAgainstAvgHome();
        Double awayXgAgainst = awayStats.getXgAgainstAvgAway();

        if (homeXgFor != null && awayXgFor != null && homeXgAgainst != null && awayXgAgainst != null) {
            double xgExpected = (homeXgFor + awayXgFor + homeXgAgainst + awayXgAgainst) / 2.0;
            // Blend 60% actual goals, 40% xG for balanced prediction
            return (actualGoalsExpected * 0.6) + (xgExpected * 0.4);
        }

        return actualGoalsExpected;
    }

    private double calculateScore(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        // Season stats (inverse normalized - lower goals = higher score)
        double homeScoredInverse = inverseNormalizeGoals(calculateVenueGoalsAvg(homeStats, true, 1.0));
        double awayScoredInverse = inverseNormalizeGoals(calculateVenueGoalsAvg(awayStats, false, 1.0));
        double homeConcededInverse = inverseNormalizeGoals(calculateVenueConcededAvg(homeStats, true, 1.0));
        double awayConcededInverse = inverseNormalizeGoals(calculateVenueConcededAvg(awayStats, false, 1.0));

        // Clean sheet percentages
        double homeCleanSheet = calculateCleanSheetPercentageOverall(homeStats);
        double awayCleanSheet = calculateCleanSheetPercentageOverall(awayStats);

        // Failed to score percentages (higher = better for unders)
        double homeFts = calculateFailedToScorePercentageOverall(homeStats);
        double awayFts = calculateFailedToScorePercentageOverall(awayStats);

        // API potential
        double apiPotential = 50.0;
        if (context.hasPotentials() && context.getPotentials().getU15Potential() != null) {
            apiPotential = context.getPotentials().getU15Potential();
        }

        double score;
        
        if (context.hasRecentForm()) {
            // Full calculation with form data
            double homeScoredFormInverse = inverseNormalizeGoals(safeDouble(context.getHomeTeamForm().getScoredAvgHome(), 1.0));
            double awayScoredFormInverse = inverseNormalizeGoals(safeDouble(context.getAwayTeamForm().getScoredAvgAway(), 1.0));
            double homeConcededFormInverse = inverseNormalizeGoals(safeDouble(context.getHomeTeamForm().getConcededAvgHome(), 1.0));
            double awayConcededFormInverse = inverseNormalizeGoals(safeDouble(context.getAwayTeamForm().getConcededAvgAway(), 1.0));

            score = (homeScoredInverse * WEIGHT_HOME_SCORED_INVERSE)
                    + (awayScoredInverse * WEIGHT_AWAY_SCORED_INVERSE)
                    + (homeConcededInverse * WEIGHT_HOME_CONCEDED_INVERSE)
                    + (awayConcededInverse * WEIGHT_AWAY_CONCEDED_INVERSE)
                    + (homeScoredFormInverse * WEIGHT_HOME_SCORED_FORM_INVERSE)
                    + (awayScoredFormInverse * WEIGHT_AWAY_SCORED_FORM_INVERSE)
                    + (homeConcededFormInverse * WEIGHT_HOME_CONCEDED_FORM_INVERSE)
                    + (awayConcededFormInverse * WEIGHT_AWAY_CONCEDED_FORM_INVERSE)
                    + (homeCleanSheet * WEIGHT_HOME_CLEANSHEET)
                    + (awayCleanSheet * WEIGHT_AWAY_CLEANSHEET)
                    + (homeFts * WEIGHT_HOME_FTS)
                    + (awayFts * WEIGHT_AWAY_FTS)
                    + (apiPotential * WEIGHT_API_POTENTIAL);
        } else {
            // No form data - redistribute weights to available data
            log.debug("No form data available, using redistributed weights for fixture: {}", 
                    context.getFixture().getId());
            
            score = (homeScoredInverse * WEIGHT_SCORED_INVERSE_NO_FORM)
                    + (awayScoredInverse * WEIGHT_SCORED_INVERSE_NO_FORM)
                    + (homeConcededInverse * WEIGHT_CONCEDED_INVERSE_NO_FORM)
                    + (awayConcededInverse * WEIGHT_CONCEDED_INVERSE_NO_FORM)
                    + (homeCleanSheet * WEIGHT_CLEANSHEET_NO_FORM)
                    + (awayCleanSheet * WEIGHT_CLEANSHEET_NO_FORM)
                    + (homeFts * WEIGHT_FTS_NO_FORM)
                    + (awayFts * WEIGHT_FTS_NO_FORM)
                    + (apiPotential * WEIGHT_API_POTENTIAL_NO_FORM);
        }

        // Apply low-scoring context boost
        double lowScoringBoost = calculateLowScoringBoost(homeStats, awayStats);
        if (lowScoringBoost > 0) {
            log.debug("Applying low-scoring boost of {} for fixture: {}", lowScoringBoost, context.getFixture().getId());
        }
        score += lowScoringBoost;

        // Apply defensive strength boost
        double defensiveBoost = calculateDefensiveStrengthBoost(homeStats, awayStats);
        if (defensiveBoost > 0) {
            log.debug("Applying defensive strength boost of {} for fixture: {}", defensiveBoost, context.getFixture().getId());
        }
        score += defensiveBoost;

        // Apply xG boost for low expected goals
        double xgBoost = calculateXgBoost(homeStats, awayStats);
        if (xgBoost > 0) {
            log.debug("Applying xG boost of {} for fixture: {}", xgBoost, context.getFixture().getId());
        }
        score += xgBoost;

        return clampScore(score);
    }

    private double calculateLowScoringBoost(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        double homeScoredAvg = calculateVenueGoalsAvg(homeStats, true, 1.0);
        double awayScoredAvg = calculateVenueGoalsAvg(awayStats, false, 1.0);
        double homeConcededAvg = calculateVenueConcededAvg(homeStats, true, 1.0);
        double awayConcededAvg = calculateVenueConcededAvg(awayStats, false, 1.0);
        
        double combinedGoalsAvg = (homeScoredAvg + awayScoredAvg + homeConcededAvg + awayConcededAvg) / 2.0;
        
        if (combinedGoalsAvg <= LOW_SCORING_COMBINED_THRESHOLD) {
            return LOW_SCORING_BOOST_AMOUNT;
        }
        return 0.0;
    }

    private double calculateDefensiveStrengthBoost(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        double homeCleanSheet = calculateCleanSheetPercentageOverall(homeStats);
        double awayCleanSheet = calculateCleanSheetPercentageOverall(awayStats);
        
        if (homeCleanSheet >= DEFENSIVE_STRENGTH_CS_THRESHOLD && awayCleanSheet >= DEFENSIVE_STRENGTH_CS_THRESHOLD) {
            return DEFENSIVE_STRENGTH_BOOST_AMOUNT;
        }
        return 0.0;
    }

    private double calculateXgBoost(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        Double homeXgFor = homeStats != null ? homeStats.getXgForAvgHome() : null;
        Double awayXgFor = awayStats != null ? awayStats.getXgForAvgAway() : null;
        
        // Only apply boost if we have xG data for both teams
        if (homeXgFor == null || awayXgFor == null) {
            return 0.0;
        }
        
        double combinedXg = homeXgFor + awayXgFor;
        // For under goals, LOW xG is good
        if (combinedXg <= XG_LOW_COMBINED_THRESHOLD) {
            return XG_LOW_BOOST_AMOUNT;
        }
        return 0.0;
    }

    private ConfidenceLevel determineConfidence(double score) {
        if (score >= THRESHOLD_STRONG) {
            return ConfidenceLevel.STRONG;
        } else if (score >= THRESHOLD_MODERATE) {
            return ConfidenceLevel.MODERATE;
        }
        return ConfidenceLevel.WEAK;
    }

    private String determineMarket(FixtureContext context, double expectedGoals, double score) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();
        
        // Check Under 1.5 eligibility
        if (expectedGoals <= 1.5 && score >= THRESHOLD_STRONG) {
            // Also check if teams have reasonable Under 1.5 percentages
            // Use inverse of Over 1.5 (100 - Over 1.5% = Under 1.5%)
            double homeOver15 = safePercentage(homeStats.getSeasonOver15PercentageOverall());
            double awayOver15 = safePercentage(awayStats.getSeasonOver15PercentageOverall());
            double homeUnder15 = 100.0 - homeOver15;
            double awayUnder15 = 100.0 - awayOver15;
            double avgUnder15 = (homeUnder15 + awayUnder15) / 2.0;
            
            // Require at least 25% average Under 1.5 rate for U1.5 recommendation
            if (avgUnder15 >= 25.0) {
                return "Under 1.5 Goals";
            }
        }
        return "Under 2.5 Goals";
    }

    private Map<String, Object> buildFactors(FixtureContext context, double score, double expectedGoals) {
        Map<String, Object> factors = new HashMap<>();
        
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        // Expected goals
        factors.put("expectedGoals", expectedGoals);

        // Season goal averages
        double homeScoredAvg = calculateVenueGoalsAvg(homeStats, true, 1.0);
        double awayScoredAvg = calculateVenueGoalsAvg(awayStats, false, 1.0);
        double homeConcededAvg = calculateVenueConcededAvg(homeStats, true, 1.0);
        double awayConcededAvg = calculateVenueConcededAvg(awayStats, false, 1.0);
        
        factors.put("homeGoalsScoredAvg", homeScoredAvg);
        factors.put("awayGoalsScoredAvg", awayScoredAvg);
        factors.put("homeGoalsConcededAvg", homeConcededAvg);
        factors.put("awayGoalsConcededAvg", awayConcededAvg);

        // Combined goals average
        double combinedGoalsAvg = (homeScoredAvg + awayScoredAvg + homeConcededAvg + awayConcededAvg) / 2.0;
        factors.put("combinedGoalsAvg", combinedGoalsAvg);

        // Defensive stats
        double homeCleanSheet = calculateCleanSheetPercentageOverall(homeStats);
        double awayCleanSheet = calculateCleanSheetPercentageOverall(awayStats);
        factors.put("homeCleanSheetPct", homeCleanSheet);
        factors.put("awayCleanSheetPct", awayCleanSheet);

        // Failed to score percentages
        double homeFts = calculateFailedToScorePercentageOverall(homeStats);
        double awayFts = calculateFailedToScorePercentageOverall(awayStats);
        factors.put("homeFailedToScorePct", homeFts);
        factors.put("awayFailedToScorePct", awayFts);

        // Under percentages (derived from Over percentages)
        double homeOver15 = safePercentage(homeStats.getSeasonOver15PercentageOverall());
        double awayOver15 = safePercentage(awayStats.getSeasonOver15PercentageOverall());
        factors.put("homeUnder15Pct", 100.0 - homeOver15);
        factors.put("awayUnder15Pct", 100.0 - awayOver15);

        double homeOver25 = safePercentage(homeStats.getSeasonOver25PercentageOverall());
        double awayOver25 = safePercentage(awayStats.getSeasonOver25PercentageOverall());
        factors.put("homeUnder25Pct", 100.0 - homeOver25);
        factors.put("awayUnder25Pct", 100.0 - awayOver25);

        // Form data availability
        factors.put("formDataAvailable", context.hasRecentForm());
        if (context.hasRecentForm()) {
            factors.put("homeScoredFormAvg", safeDouble(context.getHomeTeamForm().getScoredAvgHome(), 0.0));
            factors.put("awayScoredFormAvg", safeDouble(context.getAwayTeamForm().getScoredAvgAway(), 0.0));
            factors.put("homeConcededFormAvg", safeDouble(context.getHomeTeamForm().getConcededAvgHome(), 0.0));
            factors.put("awayConcededFormAvg", safeDouble(context.getAwayTeamForm().getConcededAvgAway(), 0.0));
        }

        // API potentials
        if (context.hasPotentials() && context.getPotentials().getU15Potential() != null) {
            factors.put("apiU15Potential", context.getPotentials().getU15Potential());
        }

        // Low-scoring boost
        double lowScoringBoost = calculateLowScoringBoost(homeStats, awayStats);
        factors.put("lowScoringBoostApplied", lowScoringBoost > 0);
        if (lowScoringBoost > 0) {
            factors.put("lowScoringBoostAmount", lowScoringBoost);
        }

        // Defensive strength boost
        double defensiveBoost = calculateDefensiveStrengthBoost(homeStats, awayStats);
        factors.put("defensiveStrengthBoostApplied", defensiveBoost > 0);
        if (defensiveBoost > 0) {
            factors.put("defensiveStrengthBoostAmount", defensiveBoost);
        }

        // xG data
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

        double xgBoost = calculateXgBoost(homeStats, awayStats);
        factors.put("xgBoostApplied", xgBoost > 0);
        if (xgBoost > 0) {
            factors.put("xgBoostAmount", xgBoost);
            factors.put("combinedXg", homeXgFor + awayXgFor);
        }

        factors.put("calculatedScore", score);
        
        return factors;
    }

    private String buildDescription(FixtureContext context, ConfidenceLevel confidence, double expectedGoals, String market) {
        return RecommendationFactory.buildExpectedValueDescription(
                confidence, market, expectedGoals, "expected goals", context);
    }

    private Double getOddsForMarket(FixtureContext context, String market) {
        if (!context.hasOdds()) {
            return null;
        }
        return switch (market) {
            case "Under 1.5 Goals" -> context.getOdds().getOddsFtUnder15();
            case "Under 2.5 Goals" -> context.getOdds().getOddsFtUnder25();
            case "Under 3.5 Goals" -> context.getOdds().getOddsFtUnder35();
            default -> context.getOdds().getOddsFtUnder25();
        };
    }
}
