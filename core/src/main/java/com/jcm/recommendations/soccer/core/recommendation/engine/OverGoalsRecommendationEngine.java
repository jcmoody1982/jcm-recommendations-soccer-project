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
public class OverGoalsRecommendationEngine implements RecommendationEngine {

    // Base weights when form data IS available (total = 1.0)
    private static final double WEIGHT_HOME_SCORED_SEASON = 0.08;
    private static final double WEIGHT_AWAY_SCORED_SEASON = 0.08;
    private static final double WEIGHT_HOME_CONCEDED_SEASON = 0.08;
    private static final double WEIGHT_AWAY_CONCEDED_SEASON = 0.08;
    private static final double WEIGHT_HOME_SCORED_FORM = 0.12;
    private static final double WEIGHT_AWAY_SCORED_FORM = 0.12;
    private static final double WEIGHT_HOME_CONCEDED_FORM = 0.08;
    private static final double WEIGHT_AWAY_CONCEDED_FORM = 0.08;
    private static final double WEIGHT_HOME_OVER25_SEASON = 0.08;
    private static final double WEIGHT_AWAY_OVER25_SEASON = 0.08;
    private static final double WEIGHT_API_POTENTIAL = 0.12;

    // Redistributed weights when form data is NOT available (total = 1.0)
    private static final double WEIGHT_SCORED_SEASON_NO_FORM = 0.15;    // 0.08 → 0.15 each
    private static final double WEIGHT_CONCEDED_SEASON_NO_FORM = 0.12;  // 0.08 → 0.12 each
    private static final double WEIGHT_OVER25_SEASON_NO_FORM = 0.12;    // 0.08 → 0.12 each
    private static final double WEIGHT_API_POTENTIAL_NO_FORM = 0.22;    // 0.12 → 0.22

    // High-scoring context boost
    private static final double HIGH_SCORING_COMBINED_THRESHOLD = 3.0;  // Combined goals avg
    private static final double HIGH_SCORING_BOOST_AMOUNT = 5.0;        // Bonus percentage points

    // xG boost for high expected goals
    private static final double XG_COMBINED_THRESHOLD = 2.8;            // Combined xG for
    private static final double XG_BOOST_AMOUNT = 4.0;                  // Bonus percentage points

    private static final double THRESHOLD_STRONG = 80.0;
    private static final double THRESHOLD_MODERATE = 65.0;
    
    private static final double FILTER_MIN_COMBINED_GOALS = 2.5;

    @Override
    public RecommendationType getType() {
        return RecommendationType.OVER_GOALS;
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        log.debug("Analyzing Over Goals for fixture: fixtureId={}, {} vs {}", 
                context.getFixture().getId(),
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());

        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double expectedGoals = calculateExpectedGoals(homeStats, awayStats);
        
        if (expectedGoals < FILTER_MIN_COMBINED_GOALS) {
            log.debug("Fixture failed Over Goals filter: fixtureId={}, expectedGoals={}", 
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
                .type(RecommendationType.OVER_GOALS)
                .confidence(confidence)
                .score(score)
                .market(market)
                .odds(odds)
                .description(buildDescription(context, confidence, expectedGoals, market))
                .factors(factors)
                .build();

        log.info("Over Goals recommendation generated: fixtureId={}, expectedGoals={}, score={}, confidence={}, market={}", 
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

        // Season stats (normalized to percentage scale)
        double homeScoredSeason = normalizeGoals(calculateVenueGoalsAvg(homeStats, true, 1.0));
        double awayScoredSeason = normalizeGoals(calculateVenueGoalsAvg(awayStats, false, 1.0));
        double homeConcededSeason = normalizeGoals(calculateVenueConcededAvg(homeStats, true, 1.0));
        double awayConcededSeason = normalizeGoals(calculateVenueConcededAvg(awayStats, false, 1.0));

        // Over 2.5 percentages
        double homeOver25 = safePercentage(homeStats.getSeasonOver25PercentageOverall());
        double awayOver25 = safePercentage(awayStats.getSeasonOver25PercentageOverall());

        // API potential
        double apiPotential = 50.0;
        if (context.hasPotentials() && context.getPotentials().getO25Potential() != null) {
            apiPotential = context.getPotentials().getO25Potential();
        }

        double score;
        
        if (context.hasRecentForm()) {
            // Full calculation with form data
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
                    + (homeOver25 * WEIGHT_HOME_OVER25_SEASON)
                    + (awayOver25 * WEIGHT_AWAY_OVER25_SEASON)
                    + (apiPotential * WEIGHT_API_POTENTIAL);
        } else {
            // No form data - redistribute weights to available data
            log.debug("No form data available, using redistributed weights for fixture: {}", 
                    context.getFixture().getId());
            
            score = (homeScoredSeason * WEIGHT_SCORED_SEASON_NO_FORM)
                    + (awayScoredSeason * WEIGHT_SCORED_SEASON_NO_FORM)
                    + (homeConcededSeason * WEIGHT_CONCEDED_SEASON_NO_FORM)
                    + (awayConcededSeason * WEIGHT_CONCEDED_SEASON_NO_FORM)
                    + (homeOver25 * WEIGHT_OVER25_SEASON_NO_FORM)
                    + (awayOver25 * WEIGHT_OVER25_SEASON_NO_FORM)
                    + (apiPotential * WEIGHT_API_POTENTIAL_NO_FORM);
        }

        // Apply high-scoring context boost
        double highScoringBoost = calculateHighScoringBoost(homeStats, awayStats);
        if (highScoringBoost > 0) {
            log.debug("Applying high-scoring boost of {} for fixture: {}", highScoringBoost, context.getFixture().getId());
        }
        score += highScoringBoost;

        // Apply xG boost for high expected goals
        double xgBoost = calculateXgBoost(homeStats, awayStats);
        if (xgBoost > 0) {
            log.debug("Applying xG boost of {} for fixture: {}", xgBoost, context.getFixture().getId());
        }
        score += xgBoost;

        return clampScore(score);
    }

    private double calculateHighScoringBoost(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        double homeScoredAvg = calculateVenueGoalsAvg(homeStats, true, 1.0);
        double awayScoredAvg = calculateVenueGoalsAvg(awayStats, false, 1.0);
        double homeConcededAvg = calculateVenueConcededAvg(homeStats, true, 1.0);
        double awayConcededAvg = calculateVenueConcededAvg(awayStats, false, 1.0);
        
        double combinedGoalsAvg = (homeScoredAvg + awayScoredAvg + homeConcededAvg + awayConcededAvg) / 2.0;
        
        if (combinedGoalsAvg >= HIGH_SCORING_COMBINED_THRESHOLD) {
            return HIGH_SCORING_BOOST_AMOUNT;
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
        if (combinedXg >= XG_COMBINED_THRESHOLD) {
            return XG_BOOST_AMOUNT;
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
        
        // Check Over 3.5 eligibility
        if (expectedGoals >= 3.5 && score >= THRESHOLD_STRONG) {
            // Also check if teams have reasonable Over 3.5 percentages
            double homeOver35 = safePercentage(homeStats.getSeasonOver35PercentageOverall());
            double awayOver35 = safePercentage(awayStats.getSeasonOver35PercentageOverall());
            double avgOver35 = (homeOver35 + awayOver35) / 2.0;
            
            // Require at least 40% average Over 3.5 rate for O3.5 recommendation
            if (avgOver35 >= 40.0) {
                return "Over 3.5 Goals";
            }
        }
        return "Over 2.5 Goals";
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

        // Over percentages
        factors.put("homeOver25Pct", safePercentage(homeStats.getSeasonOver25PercentageOverall()));
        factors.put("awayOver25Pct", safePercentage(awayStats.getSeasonOver25PercentageOverall()));
        factors.put("homeOver35Pct", safePercentage(homeStats.getSeasonOver35PercentageOverall()));
        factors.put("awayOver35Pct", safePercentage(awayStats.getSeasonOver35PercentageOverall()));

        // Form data availability
        factors.put("formDataAvailable", context.hasRecentForm());
        if (context.hasRecentForm()) {
            factors.put("homeScoredFormAvg", safeDouble(context.getHomeTeamForm().getScoredAvgHome(), 0.0));
            factors.put("awayScoredFormAvg", safeDouble(context.getAwayTeamForm().getScoredAvgAway(), 0.0));
            factors.put("homeConcededFormAvg", safeDouble(context.getHomeTeamForm().getConcededAvgHome(), 0.0));
            factors.put("awayConcededFormAvg", safeDouble(context.getAwayTeamForm().getConcededAvgAway(), 0.0));
        }

        // API potential
        if (context.hasPotentials() && context.getPotentials().getO25Potential() != null) {
            factors.put("apiO25Potential", context.getPotentials().getO25Potential());
        }
        if (context.hasPotentials() && context.getPotentials().getO35Potential() != null) {
            factors.put("apiO35Potential", context.getPotentials().getO35Potential());
        }

        // High-scoring boost
        double combinedGoalsAvg = (homeScoredAvg + awayScoredAvg + homeConcededAvg + awayConcededAvg) / 2.0;
        factors.put("combinedGoalsAvg", combinedGoalsAvg);
        double highScoringBoost = calculateHighScoringBoost(homeStats, awayStats);
        factors.put("highScoringBoostApplied", highScoringBoost > 0);
        if (highScoringBoost > 0) {
            factors.put("highScoringBoostAmount", highScoringBoost);
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
            case "Over 1.5 Goals" -> context.getOdds().getOddsFtOver15();
            case "Over 2.5 Goals" -> context.getOdds().getOddsFtOver25();
            case "Over 3.5 Goals" -> context.getOdds().getOddsFtOver35();
            default -> context.getOdds().getOddsFtOver25();
        };
    }
}
