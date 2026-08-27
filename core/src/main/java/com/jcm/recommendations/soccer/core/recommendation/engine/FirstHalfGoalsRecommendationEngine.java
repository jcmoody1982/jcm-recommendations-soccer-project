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
 * Uses API potentials, xG data (×0.45 proxy for 1H share), fast/slow starter 
 * detection, and dynamic weight redistribution when data is unavailable.
 */
@Component
@Slf4j
public class FirstHalfGoalsRecommendationEngine implements RecommendationEngine {

    // First half share of total goals (industry standard)
    private static final double FIRST_HALF_RATIO = 0.45;

    // Base weights when all data IS available (total = 1.0)
    private static final double WEIGHT_API_O05HT = 0.15;
    private static final double WEIGHT_API_O15HT = 0.10;
    private static final double WEIGHT_HOME_1H_SCORED = 0.12;
    private static final double WEIGHT_AWAY_1H_SCORED = 0.12;
    private static final double WEIGHT_HOME_1H_CONCEDED = 0.08;
    private static final double WEIGHT_AWAY_1H_CONCEDED = 0.08;
    private static final double WEIGHT_HOME_BTTS_HT = 0.05;
    private static final double WEIGHT_AWAY_BTTS_HT = 0.05;
    private static final double WEIGHT_HOME_XG_1H = 0.10;
    private static final double WEIGHT_AWAY_XG_1H = 0.10;
    private static final double WEIGHT_XGA_COMBINED = 0.05;

    // Weights when xG data is NOT available (redistribute 0.25 to other factors)
    private static final double WEIGHT_API_O05HT_NO_XG = 0.20;
    private static final double WEIGHT_API_O15HT_NO_XG = 0.15;
    private static final double WEIGHT_HOME_1H_SCORED_NO_XG = 0.15;
    private static final double WEIGHT_AWAY_1H_SCORED_NO_XG = 0.15;
    private static final double WEIGHT_HOME_1H_CONCEDED_NO_XG = 0.10;
    private static final double WEIGHT_AWAY_1H_CONCEDED_NO_XG = 0.10;
    private static final double WEIGHT_HOME_BTTS_HT_NO_XG = 0.075;
    private static final double WEIGHT_AWAY_BTTS_HT_NO_XG = 0.075;

    // xG-based combined rating thresholds and multipliers
    private static final double XG_HIGH_THRESHOLD = 3.0;
    private static final double XG_HIGH_MULTIPLIER = 1.20;
    private static final double XG_ABOVE_AVG_THRESHOLD = 2.5;
    private static final double XG_ABOVE_AVG_MULTIPLIER = 1.10;
    private static final double XG_LOW_THRESHOLD = 2.0;
    private static final double XG_LOW_MULTIPLIER = 0.85;

    // xG regression adjustment
    private static final double XG_UNDERPERFORM_MULTIPLIER = 1.10;  // Both teams underperforming xG
    private static final double XG_OVERPERFORM_MULTIPLIER = 0.90;   // Both teams overperforming xG

    // Fast starter/slow starter thresholds (based on 1H goals ratio)
    private static final double FAST_STARTER_THRESHOLD = 0.55;
    private static final double FAST_STARTER_MULTIPLIER = 1.20;
    private static final double SLOW_STARTER_THRESHOLD = 0.45;
    private static final double SLOW_STARTER_MULTIPLIER = 0.85;

    // Early conceder thresholds (based on 1H conceded ratio)
    private static final double EARLY_CONCEDER_THRESHOLD = 0.55;
    private static final double EARLY_CONCEDER_MULTIPLIER = 1.15;
    private static final double STRONG_EARLY_DEFENSE_THRESHOLD = 0.45;
    private static final double STRONG_EARLY_DEFENSE_MULTIPLIER = 0.90;

    // Recent form adjustment (1H goals in last 5 matches)
    private static final double FORM_HOT_1H_MULTIPLIER = 1.10;      // 4+ of last 5 had 1H goals
    private static final double FORM_COLD_1H_MULTIPLIER = 0.90;     // 2 or fewer of last 5 had 1H goals

    // Thresholds for recommendations
    private static final double THRESHOLD_STRONG = 75.0;
    private static final double THRESHOLD_MODERATE = 60.0;
    
    // Filter: minimum expected 1H goals to generate a recommendation
    private static final double FILTER_MIN_1H_GOALS = 0.8;

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

        // Calculate expected 1H goals (use ratio of full-time expected)
        double expectedGoals1H = calculateExpected1HGoals(homeStats, awayStats);
        
        if (expectedGoals1H < FILTER_MIN_1H_GOALS) {
            log.debug("Fixture failed First Half Goals filter: fixtureId={}, expected1HGoals={}", 
                    context.getFixture().getId(), expectedGoals1H);
            return Optional.empty();
        }

        double score = calculateScore(context);
        
        // Apply multipliers
        score = applyXgRatingMultiplier(score, homeStats, awayStats);
        score = applyXgRegressionMultiplier(score, homeStats, awayStats);
        score = applyFastStarterMultiplier(score, homeStats, awayStats, context);
        score = applyEarlyConcedeMultiplier(score, homeStats, awayStats, context);
        score = applyRecentFormMultiplier(score, context);
        
        score = clampScore(score);
        
        ConfidenceLevel confidence = determineConfidence(score);

        if (confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        String market = determineMarket(expectedGoals1H, score, context);
        Map<String, Object> factors = buildFactors(context, score, expectedGoals1H, homeStats, awayStats);

        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(RecommendationType.FIRST_HALF_GOALS)
                .confidence(confidence)
                .score(score)
                .market(market)
                .odds(null)  // No HT-specific odds in our data model
                .description(buildDescription(context, confidence, expectedGoals1H, market))
                .factors(factors)
                .build();

        log.info("First Half Goals recommendation generated: fixtureId={}, expected1HGoals={}, score={}, confidence={}, market={}", 
                context.getFixture().getId(), String.format("%.2f", expectedGoals1H), 
                String.format("%.1f", score), confidence, market);

        return Optional.of(recommendation);
    }

    private double calculateExpected1HGoals(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        // Calculate full-time expected goals
        double homeScoredAvg = calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 1.0);
        double awayScoredAvg = calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 1.0);
        double homeConcededAvg = calculateGoalsAvg(homeStats.getSeasonConcededHome(), homeStats.getMatchesPlayed(), 1.0);
        double awayConcededAvg = calculateGoalsAvg(awayStats.getSeasonConcededAway(), awayStats.getMatchesPlayed(), 1.0);

        double actualExpected = (homeScoredAvg + awayScoredAvg + homeConcededAvg + awayConcededAvg) / 2.0;

        // If xG data is available, blend for more accuracy
        Double homeXgFor = homeStats.getXgForAvgHome();
        Double awayXgFor = awayStats.getXgForAvgAway();
        Double homeXgAgainst = homeStats.getXgAgainstAvgHome();
        Double awayXgAgainst = awayStats.getXgAgainstAvgAway();

        double fullTimeExpected;
        if (homeXgFor != null && awayXgFor != null && homeXgAgainst != null && awayXgAgainst != null) {
            double xgExpected = (homeXgFor + awayXgFor + homeXgAgainst + awayXgAgainst) / 2.0;
            fullTimeExpected = (actualExpected * 0.6) + (xgExpected * 0.4);
        } else {
            fullTimeExpected = actualExpected;
        }

        // Apply first half ratio
        return fullTimeExpected * FIRST_HALF_RATIO;
    }

    private double calculateScore(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        boolean hasXgData = hasXgData(homeStats, awayStats);

        // Calculate 1H goals scored proxy (total × 0.45, normalized to percentage)
        double home1HScoredProxy = normalize1HGoals(
                calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 1.0) * FIRST_HALF_RATIO);
        double away1HScoredProxy = normalize1HGoals(
                calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 1.0) * FIRST_HALF_RATIO);
        
        // Calculate 1H goals conceded proxy
        double home1HConcededProxy = normalize1HGoals(
                calculateGoalsAvg(homeStats.getSeasonConcededHome(), homeStats.getMatchesPlayed(), 1.0) * FIRST_HALF_RATIO);
        double away1HConcededProxy = normalize1HGoals(
                calculateGoalsAvg(awayStats.getSeasonConcededAway(), awayStats.getMatchesPlayed(), 1.0) * FIRST_HALF_RATIO);

        // BTTS HT proxy (use season BTTS % as indicator - if teams often have BTTS, likely 1H goals too)
        double homeBttsHtProxy = safePercentage(homeStats.getSeasonBttsPercentageHome());
        double awayBttsHtProxy = safePercentage(awayStats.getSeasonBttsPercentageAway());

        // API potentials for HT goals
        double apiO05Ht = 50.0;
        double apiO15Ht = 50.0;
        if (context.hasPotentials()) {
            if (context.getPotentials().getO05HtPotential() != null) {
                apiO05Ht = context.getPotentials().getO05HtPotential();
            }
            if (context.getPotentials().getO15HtPotential() != null) {
                apiO15Ht = context.getPotentials().getO15HtPotential();
            }
        }

        double score;

        if (hasXgData) {
            // xG 1H proxy
            double homeXg1H = normalize1HGoals(safeDouble(homeStats.getXgForAvgHome()) * FIRST_HALF_RATIO);
            double awayXg1H = normalize1HGoals(safeDouble(awayStats.getXgForAvgAway()) * FIRST_HALF_RATIO);
            double combinedXga1H = normalize1HGoals(
                    (safeDouble(homeStats.getXgAgainstAvgHome()) + safeDouble(awayStats.getXgAgainstAvgAway())) 
                    * FIRST_HALF_RATIO / 2.0);

            score = (apiO05Ht * WEIGHT_API_O05HT)
                    + (apiO15Ht * WEIGHT_API_O15HT)
                    + (home1HScoredProxy * WEIGHT_HOME_1H_SCORED)
                    + (away1HScoredProxy * WEIGHT_AWAY_1H_SCORED)
                    + (home1HConcededProxy * WEIGHT_HOME_1H_CONCEDED)
                    + (away1HConcededProxy * WEIGHT_AWAY_1H_CONCEDED)
                    + (homeBttsHtProxy * WEIGHT_HOME_BTTS_HT)
                    + (awayBttsHtProxy * WEIGHT_AWAY_BTTS_HT)
                    + (homeXg1H * WEIGHT_HOME_XG_1H)
                    + (awayXg1H * WEIGHT_AWAY_XG_1H)
                    + (combinedXga1H * WEIGHT_XGA_COMBINED);
        } else {
            // No xG - redistribute weights
            log.debug("No xG data available, using redistributed weights for fixture: {}", 
                    context.getFixture().getId());

            score = (apiO05Ht * WEIGHT_API_O05HT_NO_XG)
                    + (apiO15Ht * WEIGHT_API_O15HT_NO_XG)
                    + (home1HScoredProxy * WEIGHT_HOME_1H_SCORED_NO_XG)
                    + (away1HScoredProxy * WEIGHT_AWAY_1H_SCORED_NO_XG)
                    + (home1HConcededProxy * WEIGHT_HOME_1H_CONCEDED_NO_XG)
                    + (away1HConcededProxy * WEIGHT_AWAY_1H_CONCEDED_NO_XG)
                    + (homeBttsHtProxy * WEIGHT_HOME_BTTS_HT_NO_XG)
                    + (awayBttsHtProxy * WEIGHT_AWAY_BTTS_HT_NO_XG);
        }

        return score;
    }

    private boolean hasXgData(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        return homeStats.getXgForAvgHome() != null 
                && awayStats.getXgForAvgAway() != null
                && homeStats.getXgAgainstAvgHome() != null 
                && awayStats.getXgAgainstAvgAway() != null;
    }

    private double applyXgRatingMultiplier(double score, TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        if (!hasXgData(homeStats, awayStats)) {
            return score;
        }

        double combinedXg = safeDouble(homeStats.getXgForAvgHome()) + safeDouble(awayStats.getXgForAvgAway());

        if (combinedXg > XG_HIGH_THRESHOLD) {
            log.debug("Applying high xG rating multiplier: combinedXg={}", combinedXg);
            return score * XG_HIGH_MULTIPLIER;
        } else if (combinedXg >= XG_ABOVE_AVG_THRESHOLD) {
            log.debug("Applying above-average xG rating multiplier: combinedXg={}", combinedXg);
            return score * XG_ABOVE_AVG_MULTIPLIER;
        } else if (combinedXg < XG_LOW_THRESHOLD) {
            log.debug("Applying low xG rating multiplier: combinedXg={}", combinedXg);
            return score * XG_LOW_MULTIPLIER;
        }

        return score;
    }

    private double applyXgRegressionMultiplier(double score, TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        if (!hasXgData(homeStats, awayStats)) {
            return score;
        }

        double homeXg = safeDouble(homeStats.getXgForAvgHome());
        double awayXg = safeDouble(awayStats.getXgForAvgAway());
        double homeActual = calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 1.0);
        double awayActual = calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 1.0);

        boolean homeUnderperforming = homeXg > 0 && homeActual < homeXg * 0.85;
        boolean awayUnderperforming = awayXg > 0 && awayActual < awayXg * 0.85;
        boolean homeOverperforming = homeXg > 0 && homeActual > homeXg * 1.15;
        boolean awayOverperforming = awayXg > 0 && awayActual > awayXg * 1.15;

        if (homeUnderperforming && awayUnderperforming) {
            log.debug("Both teams underperforming xG - regression suggests more goals likely");
            return score * XG_UNDERPERFORM_MULTIPLIER;
        } else if (homeOverperforming && awayOverperforming) {
            log.debug("Both teams overperforming xG - regression suggests fewer goals");
            return score * XG_OVERPERFORM_MULTIPLIER;
        }

        return score;
    }

    private double applyFastStarterMultiplier(double score, TeamSeasonStats homeStats, 
            TeamSeasonStats awayStats, FixtureContext context) {
        // Without explicit 1H data, we use API potentials to infer fast starter tendency
        // If O05HT potential is very high relative to total goals, team tends to score early
        
        if (!context.hasPotentials()) {
            return score;
        }
        
        Double o05HtPotential = context.getPotentials().getO05HtPotential();
        Double o25Potential = context.getPotentials().getO25Potential();
        
        if (o05HtPotential == null || o25Potential == null || o25Potential == 0) {
            return score;
        }
        
        // Calculate implied 1H ratio from API potentials
        // If O05HT is very high and close to O25, teams tend to score early
        double impliedRatio = o05HtPotential / Math.max(50.0, o25Potential);
        
        if (impliedRatio > FAST_STARTER_THRESHOLD * 1.5) { // Adjusted for potential scale
            log.debug("Fast starter detected via API potentials: ratio={}", impliedRatio);
            return score * FAST_STARTER_MULTIPLIER;
        } else if (impliedRatio < SLOW_STARTER_THRESHOLD) {
            log.debug("Slow starter detected via API potentials: ratio={}", impliedRatio);
            return score * SLOW_STARTER_MULTIPLIER;
        }
        
        return score;
    }

    private double applyEarlyConcedeMultiplier(double score, TeamSeasonStats homeStats, 
            TeamSeasonStats awayStats, FixtureContext context) {
        // Use conceded goals ratio compared to league average as proxy for early vulnerability
        // Teams with high conceded average tend to be vulnerable throughout including 1H
        
        double homeConcededAvg = calculateGoalsAvg(homeStats.getSeasonConcededHome(), homeStats.getMatchesPlayed(), 1.0);
        double awayConcededAvg = calculateGoalsAvg(awayStats.getSeasonConcededAway(), awayStats.getMatchesPlayed(), 1.0);
        double combinedConceded = homeConcededAvg + awayConcededAvg;
        
        // High combined conceded (> 2.5) suggests both teams vulnerable = more 1H goals
        if (combinedConceded > 2.5) {
            log.debug("Early conceder detected: combinedConceded={}", combinedConceded);
            return score * EARLY_CONCEDER_MULTIPLIER;
        } else if (combinedConceded < 1.5) {
            log.debug("Strong early defense detected: combinedConceded={}", combinedConceded);
            return score * STRONG_EARLY_DEFENSE_MULTIPLIER;
        }
        
        return score;
    }

    private double applyRecentFormMultiplier(double score, FixtureContext context) {
        if (!context.hasRecentForm()) {
            return score;
        }
        
        TeamRecentForm homeForm = context.getHomeTeamForm();
        TeamRecentForm awayForm = context.getAwayTeamForm();
        
        if (homeForm == null || awayForm == null) {
            return score;
        }
        
        // Use Over 1.5 in recent form as proxy for 1H goals
        // (if a team regularly has O1.5 in recent matches, likely to have 1H goals)
        int homeO15Form = safeInt(homeForm.getOver15Overall());
        int awayO15Form = safeInt(awayForm.getOver15Overall());
        int totalO15InForm = homeO15Form + awayO15Form;
        
        // Out of 10 possible matches (5 each), if 8+ had O1.5, very hot form
        if (totalO15InForm >= 8) {
            log.debug("Hot 1H form detected: totalO15InForm={}", totalO15InForm);
            return score * FORM_HOT_1H_MULTIPLIER;
        } else if (totalO15InForm <= 4) {
            log.debug("Cold 1H form detected: totalO15InForm={}", totalO15InForm);
            return score * FORM_COLD_1H_MULTIPLIER;
        }
        
        return score;
    }

    private double normalize1HGoals(double goals1HAvg) {
        // Normalize 1H goals (typically 0.5-1.5 range) to percentage scale
        // Max expected 1H goals per team is around 1.5, so multiply by 50
        return Math.min(100.0, goals1HAvg * 50.0);
    }

    private ConfidenceLevel determineConfidence(double score) {
        if (score >= THRESHOLD_STRONG) {
            return ConfidenceLevel.STRONG;
        } else if (score >= THRESHOLD_MODERATE) {
            return ConfidenceLevel.MODERATE;
        }
        return ConfidenceLevel.WEAK;
    }

    private String determineMarket(double expected1HGoals, double score, FixtureContext context) {
        // Check API potential for O15HT confidence
        double o15HtPotential = 50.0;
        if (context.hasPotentials() && context.getPotentials().getO15HtPotential() != null) {
            o15HtPotential = context.getPotentials().getO15HtPotential();
        }
        
        // Over 1.5 HT if expected 1H goals >= 1.3 AND score is strong AND API potential > 55%
        if (expected1HGoals >= 1.3 && score >= THRESHOLD_STRONG && o15HtPotential >= 55.0) {
            return "Over 1.5 HT Goals";
        }
        
        // Over 0.5 HT is the default market
        return "Over 0.5 HT Goals";
    }

    private Map<String, Object> buildFactors(FixtureContext context, double score, 
            double expected1HGoals, TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        Map<String, Object> factors = new HashMap<>();
        
        // Expected goals
        factors.put("expected1HGoals", expected1HGoals);
        factors.put("firstHalfRatioUsed", FIRST_HALF_RATIO);
        
        // Full-time expected for reference
        double ftExpected = calculateExpected1HGoals(homeStats, awayStats) / FIRST_HALF_RATIO;
        factors.put("expectedFullTimeGoals", ftExpected);
        
        // Season goal averages (1H proxy)
        double home1HScoredProxy = calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 1.0) * FIRST_HALF_RATIO;
        double away1HScoredProxy = calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 1.0) * FIRST_HALF_RATIO;
        double home1HConcededProxy = calculateGoalsAvg(homeStats.getSeasonConcededHome(), homeStats.getMatchesPlayed(), 1.0) * FIRST_HALF_RATIO;
        double away1HConcededProxy = calculateGoalsAvg(awayStats.getSeasonConcededAway(), awayStats.getMatchesPlayed(), 1.0) * FIRST_HALF_RATIO;
        
        factors.put("home1HScoredProxyAvg", home1HScoredProxy);
        factors.put("away1HScoredProxyAvg", away1HScoredProxy);
        factors.put("home1HConcededProxyAvg", home1HConcededProxy);
        factors.put("away1HConcededProxyAvg", away1HConcededProxy);
        
        // BTTS HT proxy
        factors.put("homeBttsSeasonPct", safePercentage(homeStats.getSeasonBttsPercentageHome()));
        factors.put("awayBttsSeasonPct", safePercentage(awayStats.getSeasonBttsPercentageAway()));

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
            double homeActual = calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 1.0);
            double awayActual = calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 1.0);
            
            boolean homeUnderperforming = homeXg > 0 && homeActual < homeXg * 0.85;
            boolean awayUnderperforming = awayXg > 0 && awayActual < awayXg * 0.85;
            boolean homeOverperforming = homeXg > 0 && homeActual > homeXg * 1.15;
            boolean awayOverperforming = awayXg > 0 && awayActual > awayXg * 1.15;
            
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
        
        if (context.hasPotentials() && context.getPotentials().getO05HtPotential() != null 
                && context.getPotentials().getO25Potential() != null) {
            double o05Ht = context.getPotentials().getO05HtPotential();
            double o25 = context.getPotentials().getO25Potential();
            double impliedRatio = o05Ht / Math.max(50.0, o25);
            
            factors.put("fastStarterImpliedRatio", impliedRatio);
            
            if (impliedRatio > FAST_STARTER_THRESHOLD * 1.5) {
                factors.put("fastStarterStatus", "Fast starters detected");
                positiveIndicators.add("Fast starter teams");
            } else if (impliedRatio < SLOW_STARTER_THRESHOLD) {
                factors.put("fastStarterStatus", "Slow starters detected");
                riskFlags.add("Slow starter teams");
            }
        }
        
        // Early conceder analysis
        double combinedConceded = calculateGoalsAvg(homeStats.getSeasonConcededHome(), homeStats.getMatchesPlayed(), 1.0)
                + calculateGoalsAvg(awayStats.getSeasonConcededAway(), awayStats.getMatchesPlayed(), 1.0);
        factors.put("combinedConcededAvg", combinedConceded);
        
        if (combinedConceded > 2.5) {
            factors.put("earlyConcedeStatus", "Both teams vulnerable defensively");
            positiveIndicators.add("High-conceding matchup");
        } else if (combinedConceded < 1.5) {
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
        factors.put("calculatedScore", score);
        
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
}
