package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.core.recommendation.util.RecommendationFactory;
import com.jcm.recommendations.soccer.core.recommendation.util.VegasTipsterCopy;
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
 * UC-016: Second Half Goals Recommendations
 * 
 * Predicts likelihood of goals in the second half (Over 0.5 2H, Over 1.5 2H).
 * Uses xG data (×0.55 proxy for 2H share), late game intensity indicators,
 * fitness/stamina analysis, and match situation factors.
 */
@Component
@Slf4j
public class SecondHalfGoalsRecommendationEngine implements RecommendationEngine {

    // Second half share of total goals (slightly higher than 1H)
    private static final double SECOND_HALF_RATIO = 0.55;

    // Base weights when all data IS available (total = 1.0)
    private static final double WEIGHT_HOME_2H_SCORED = 0.12;
    private static final double WEIGHT_AWAY_2H_SCORED = 0.12;
    private static final double WEIGHT_HOME_2H_CONCEDED = 0.08;
    private static final double WEIGHT_AWAY_2H_CONCEDED = 0.08;
    private static final double WEIGHT_HOME_XG_2H = 0.10;
    private static final double WEIGHT_AWAY_XG_2H = 0.10;
    private static final double WEIGHT_XGA_COMBINED = 0.05;
    private static final double WEIGHT_LATE_INTENSITY = 0.10;    // Cards as proxy for intensity
    private static final double WEIGHT_FITNESS = 0.10;           // Based on late goals pattern
    private static final double WEIGHT_MATCH_SITUATION = 0.15;   // Based on win/draw likelihood

    // Weights when xG data is NOT available (redistribute 0.25 to other factors)
    private static final double WEIGHT_HOME_2H_SCORED_NO_XG = 0.18;
    private static final double WEIGHT_AWAY_2H_SCORED_NO_XG = 0.18;
    private static final double WEIGHT_HOME_2H_CONCEDED_NO_XG = 0.12;
    private static final double WEIGHT_AWAY_2H_CONCEDED_NO_XG = 0.12;
    private static final double WEIGHT_LATE_INTENSITY_NO_XG = 0.15;
    private static final double WEIGHT_FITNESS_NO_XG = 0.10;
    private static final double WEIGHT_MATCH_SITUATION_NO_XG = 0.15;

    // xG-based combined rating thresholds and multipliers
    private static final double XG_HIGH_THRESHOLD = 3.0;
    private static final double XG_HIGH_MULTIPLIER = 1.20;
    private static final double XG_ABOVE_AVG_THRESHOLD = 2.5;
    private static final double XG_ABOVE_AVG_MULTIPLIER = 1.10;
    private static final double XG_LOW_THRESHOLD = 2.0;
    private static final double XG_LOW_MULTIPLIER = 0.85;

    // Late scorer/finisher thresholds
    private static final double STRONG_FINISHER_THRESHOLD = 0.60;
    private static final double STRONG_FINISHER_MULTIPLIER = 1.25;
    private static final double BALANCED_FINISHER_THRESHOLD = 0.50;
    private static final double BALANCED_FINISHER_MULTIPLIER = 1.05;
    private static final double FRONT_LOADED_MULTIPLIER = 0.90;

    // Late conceder thresholds
    private static final double LATE_CONCEDER_THRESHOLD = 0.60;
    private static final double LATE_CONCEDER_MULTIPLIER = 1.20;
    private static final double STRONG_LATE_DEFENSE_THRESHOLD = 0.45;
    private static final double STRONG_LATE_DEFENSE_MULTIPLIER = 0.90;

    // Late game intensity thresholds (based on cards per game)
    private static final double HIGH_INTENSITY_CARDS_THRESHOLD = 4.0;
    private static final double HIGH_INTENSITY_MULTIPLIER = 1.10;
    private static final double LOW_INTENSITY_CARDS_THRESHOLD = 2.5;
    private static final double LOW_INTENSITY_MULTIPLIER = 0.95;

    // Thresholds for recommendations
    private static final double THRESHOLD_STRONG = 75.0;
    private static final double THRESHOLD_MODERATE = 60.0;
    
    // Filter: minimum expected 2H goals to generate a recommendation
    private static final double FILTER_MIN_2H_GOALS = 0.9;

    @Override
    public RecommendationType getType() {
        return RecommendationType.SECOND_HALF_GOALS;
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        log.debug("Analyzing Second Half Goals for fixture: fixtureId={}, {} vs {}", 
                context.getFixture().getId(),
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());

        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        // Calculate expected 2H goals (use ratio of full-time expected)
        double expectedGoals2H = calculateExpected2HGoals(homeStats, awayStats);
        
        if (expectedGoals2H < FILTER_MIN_2H_GOALS) {
            log.debug("Fixture failed Second Half Goals filter: fixtureId={}, expected2HGoals={}", 
                    context.getFixture().getId(), expectedGoals2H);
            return Optional.empty();
        }

        double score = calculateScore(context);
        
        // Apply multipliers
        score = applyXgRatingMultiplier(score, homeStats, awayStats);
        score = applyLateGoalsTendencyMultiplier(score, homeStats, awayStats);
        score = applyLateConcedeMultiplier(score, homeStats, awayStats);
        score = applyLateIntensityMultiplier(score, homeStats, awayStats);
        
        score = clampScore(score);
        
        ConfidenceLevel confidence = determineConfidence(score);

        if (confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        String market = determineMarket(expectedGoals2H, score);
        Map<String, Object> factors = buildFactors(context, score, expectedGoals2H, homeStats, awayStats);

        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(RecommendationType.SECOND_HALF_GOALS)
                .confidence(confidence)
                .score(score)
                .market(market)
                .odds(null)  // No 2H-specific odds in our data model
                .description(buildDescription(context, confidence, expectedGoals2H, market))
                .factors(factors)
                .build();

        log.info("Second Half Goals recommendation generated: fixtureId={}, expected2HGoals={}, score={}, confidence={}, market={}", 
                context.getFixture().getId(), String.format("%.2f", expectedGoals2H), 
                String.format("%.1f", score), confidence, market);

        return Optional.of(recommendation);
    }

    private double calculateExpected2HGoals(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
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

        // Apply second half ratio (typically slightly more goals in 2H)
        return fullTimeExpected * SECOND_HALF_RATIO;
    }

    private double calculateScore(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        boolean hasXgData = hasXgData(homeStats, awayStats);

        // Calculate 2H goals scored proxy (total × 0.55, normalized to percentage)
        double home2HScoredProxy = normalize2HGoals(
                calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 1.0) * SECOND_HALF_RATIO);
        double away2HScoredProxy = normalize2HGoals(
                calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 1.0) * SECOND_HALF_RATIO);
        
        // Calculate 2H goals conceded proxy
        double home2HConcededProxy = normalize2HGoals(
                calculateGoalsAvg(homeStats.getSeasonConcededHome(), homeStats.getMatchesPlayed(), 1.0) * SECOND_HALF_RATIO);
        double away2HConcededProxy = normalize2HGoals(
                calculateGoalsAvg(awayStats.getSeasonConcededAway(), awayStats.getMatchesPlayed(), 1.0) * SECOND_HALF_RATIO);

        // Late game intensity - use cards as proxy
        double lateIntensity = calculateLateGameIntensity(homeStats, awayStats);
        
        // Fitness indicator - teams that score late typically have good fitness
        double fitnessIndicator = calculateFitnessIndicator(homeStats, awayStats);
        
        // Match situation factor - close games tend to have more 2H goals
        double matchSituation = calculateMatchSituationFactor(homeStats, awayStats);

        double score;

        if (hasXgData) {
            // xG 2H proxy
            double homeXg2H = normalize2HGoals(safeDouble(homeStats.getXgForAvgHome()) * SECOND_HALF_RATIO);
            double awayXg2H = normalize2HGoals(safeDouble(awayStats.getXgForAvgAway()) * SECOND_HALF_RATIO);
            double combinedXga2H = normalize2HGoals(
                    (safeDouble(homeStats.getXgAgainstAvgHome()) + safeDouble(awayStats.getXgAgainstAvgAway())) 
                    * SECOND_HALF_RATIO / 2.0);

            score = (home2HScoredProxy * WEIGHT_HOME_2H_SCORED)
                    + (away2HScoredProxy * WEIGHT_AWAY_2H_SCORED)
                    + (home2HConcededProxy * WEIGHT_HOME_2H_CONCEDED)
                    + (away2HConcededProxy * WEIGHT_AWAY_2H_CONCEDED)
                    + (homeXg2H * WEIGHT_HOME_XG_2H)
                    + (awayXg2H * WEIGHT_AWAY_XG_2H)
                    + (combinedXga2H * WEIGHT_XGA_COMBINED)
                    + (lateIntensity * WEIGHT_LATE_INTENSITY)
                    + (fitnessIndicator * WEIGHT_FITNESS)
                    + (matchSituation * WEIGHT_MATCH_SITUATION);
        } else {
            // No xG - redistribute weights
            log.debug("No xG data available, using redistributed weights for fixture: {}", 
                    context.getFixture().getId());

            score = (home2HScoredProxy * WEIGHT_HOME_2H_SCORED_NO_XG)
                    + (away2HScoredProxy * WEIGHT_AWAY_2H_SCORED_NO_XG)
                    + (home2HConcededProxy * WEIGHT_HOME_2H_CONCEDED_NO_XG)
                    + (away2HConcededProxy * WEIGHT_AWAY_2H_CONCEDED_NO_XG)
                    + (lateIntensity * WEIGHT_LATE_INTENSITY_NO_XG)
                    + (fitnessIndicator * WEIGHT_FITNESS_NO_XG)
                    + (matchSituation * WEIGHT_MATCH_SITUATION_NO_XG);
        }

        return score;
    }

    private boolean hasXgData(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        return homeStats.getXgForAvgHome() != null 
                && awayStats.getXgForAvgAway() != null
                && homeStats.getXgAgainstAvgHome() != null 
                && awayStats.getXgAgainstAvgAway() != null;
    }

    private double calculateLateGameIntensity(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        // Use cards as proxy for late game intensity
        // More cards typically correlates with more open, attacking play in 2H
        double homeCards = safeDouble(homeStats.getCardsAvgHome(), 2.0);
        double awayCards = safeDouble(awayStats.getCardsAvgAway(), 2.0);
        double combinedCards = homeCards + awayCards;
        
        // Normalize to percentage scale (typical range 3-6 cards combined)
        return Math.min(100.0, (combinedCards / 6.0) * 100.0);
    }

    private double calculateFitnessIndicator(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        // Teams that score more than they concede tend to have better fitness (can push late)
        // Use goal difference as a proxy for fitness/stamina
        double homeGoalDiff = calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 0.0) 
                - calculateGoalsAvg(homeStats.getSeasonConcededHome(), homeStats.getMatchesPlayed(), 0.0);
        double awayGoalDiff = calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 0.0)
                - calculateGoalsAvg(awayStats.getSeasonConcededAway(), awayStats.getMatchesPlayed(), 0.0);
        
        // Normalize to percentage (range -2 to +2 typical)
        double avgGoalDiff = (homeGoalDiff + awayGoalDiff) / 2.0;
        return Math.min(100.0, Math.max(0.0, 50.0 + (avgGoalDiff * 25.0)));
    }

    private double calculateMatchSituationFactor(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        // Close matches (high draw %, competitive teams) tend to have more 2H goals
        // As teams push for a result in the second half
        double homeDrawPct = calculateDrawPercentage(homeStats, true);
        double awayDrawPct = calculateDrawPercentage(awayStats, false);
        double avgDrawPct = (homeDrawPct + awayDrawPct) / 2.0;
        
        // Also consider if teams are attacking (high goals avg = more open play)
        double homeGoalsAvg = calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 1.0);
        double awayGoalsAvg = calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 1.0);
        double combinedGoalsIndicator = Math.min(100.0, (homeGoalsAvg + awayGoalsAvg) * 30.0);
        
        // Blend draw likelihood with attacking intent
        return (avgDrawPct * 0.4) + (combinedGoalsIndicator * 0.6);
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

    private double applyLateGoalsTendencyMultiplier(double score, TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        // Without explicit 2H data, we infer late scoring tendency from attacking metrics
        // Teams with high goals and low clean sheets tend to score/concede late
        
        double homeGoalsAvg = calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 1.0);
        double awayGoalsAvg = calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 1.0);
        double combinedGoals = homeGoalsAvg + awayGoalsAvg;
        
        // Calculate "late scorer profile" based on attacking + low clean sheets
        double homeCsPct = calculateCleanSheetPercentage(homeStats, true);
        double awayCsPct = calculateCleanSheetPercentage(awayStats, false);
        double avgCsPct = (homeCsPct + awayCsPct) / 2.0;
        
        // High-scoring teams with low clean sheet % = strong finishers
        if (combinedGoals >= 3.0 && avgCsPct < 30.0) {
            log.debug("Strong finisher profile detected: combinedGoals={}, avgCS%={}", combinedGoals, avgCsPct);
            return score * STRONG_FINISHER_MULTIPLIER;
        } else if (combinedGoals >= 2.5) {
            log.debug("Balanced finisher profile: combinedGoals={}", combinedGoals);
            return score * BALANCED_FINISHER_MULTIPLIER;
        } else if (combinedGoals < 2.0) {
            log.debug("Front-loaded profile (low combined goals): combinedGoals={}", combinedGoals);
            return score * FRONT_LOADED_MULTIPLIER;
        }
        
        return score;
    }

    private double applyLateConcedeMultiplier(double score, TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        // Teams with high conceded average and low clean sheets = vulnerable late
        double homeConcededAvg = calculateGoalsAvg(homeStats.getSeasonConcededHome(), homeStats.getMatchesPlayed(), 1.0);
        double awayConcededAvg = calculateGoalsAvg(awayStats.getSeasonConcededAway(), awayStats.getMatchesPlayed(), 1.0);
        double combinedConceded = homeConcededAvg + awayConcededAvg;
        
        double homeCsPct = calculateCleanSheetPercentage(homeStats, true);
        double awayCsPct = calculateCleanSheetPercentage(awayStats, false);
        double avgCsPct = (homeCsPct + awayCsPct) / 2.0;
        
        // High conceded + low clean sheets = late concede tendency
        if (combinedConceded >= 2.5 && avgCsPct < 25.0) {
            log.debug("Late conceder profile: combinedConceded={}, avgCS%={}", combinedConceded, avgCsPct);
            return score * LATE_CONCEDER_MULTIPLIER;
        } else if (combinedConceded < 1.5 && avgCsPct > 40.0) {
            log.debug("Strong late defense profile: combinedConceded={}, avgCS%={}", combinedConceded, avgCsPct);
            return score * STRONG_LATE_DEFENSE_MULTIPLIER;
        }
        
        return score;
    }

    private double applyLateIntensityMultiplier(double score, TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        double homeCards = safeDouble(homeStats.getCardsAvgHome(), 2.0);
        double awayCards = safeDouble(awayStats.getCardsAvgAway(), 2.0);
        double combinedCards = homeCards + awayCards;
        
        if (combinedCards >= HIGH_INTENSITY_CARDS_THRESHOLD) {
            log.debug("High intensity matchup (cards): combinedCards={}", combinedCards);
            return score * HIGH_INTENSITY_MULTIPLIER;
        } else if (combinedCards < LOW_INTENSITY_CARDS_THRESHOLD) {
            log.debug("Low intensity matchup (cards): combinedCards={}", combinedCards);
            return score * LOW_INTENSITY_MULTIPLIER;
        }
        
        return score;
    }

    private double normalize2HGoals(double goals2HAvg) {
        // Normalize 2H goals (typically 0.6-1.8 range) to percentage scale
        // Max expected 2H goals per team is around 1.7, so multiply by 45
        return Math.min(100.0, goals2HAvg * 45.0);
    }

    private ConfidenceLevel determineConfidence(double score) {
        if (score >= THRESHOLD_STRONG) {
            return ConfidenceLevel.STRONG;
        } else if (score >= THRESHOLD_MODERATE) {
            return ConfidenceLevel.MODERATE;
        }
        return ConfidenceLevel.WEAK;
    }

    private String determineMarket(double expected2HGoals, double score) {
        // Over 1.5 2H if expected 2H goals >= 1.5 AND score is strong
        if (expected2HGoals >= 1.5 && score >= THRESHOLD_STRONG) {
            return "Over 1.5 2H Goals";
        }
        
        // Over 0.5 2H is the default market
        return "Over 0.5 2H Goals";
    }

    private Map<String, Object> buildFactors(FixtureContext context, double score, 
            double expected2HGoals, TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        Map<String, Object> factors = new HashMap<>();
        
        // Expected goals
        factors.put("expected2HGoals", expected2HGoals);
        factors.put("secondHalfRatioUsed", SECOND_HALF_RATIO);
        
        // Full-time expected for reference
        double ftExpected = calculateExpected2HGoals(homeStats, awayStats) / SECOND_HALF_RATIO;
        factors.put("expectedFullTimeGoals", ftExpected);
        
        // Season goal averages (2H proxy)
        double home2HScoredProxy = calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 1.0) * SECOND_HALF_RATIO;
        double away2HScoredProxy = calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 1.0) * SECOND_HALF_RATIO;
        double home2HConcededProxy = calculateGoalsAvg(homeStats.getSeasonConcededHome(), homeStats.getMatchesPlayed(), 1.0) * SECOND_HALF_RATIO;
        double away2HConcededProxy = calculateGoalsAvg(awayStats.getSeasonConcededAway(), awayStats.getMatchesPlayed(), 1.0) * SECOND_HALF_RATIO;
        
        factors.put("home2HScoredProxyAvg", home2HScoredProxy);
        factors.put("away2HScoredProxyAvg", away2HScoredProxy);
        factors.put("home2HConcededProxyAvg", home2HConcededProxy);
        factors.put("away2HConcededProxyAvg", away2HConcededProxy);

        // Late game intensity
        double homeCards = safeDouble(homeStats.getCardsAvgHome(), 2.0);
        double awayCards = safeDouble(awayStats.getCardsAvgAway(), 2.0);
        factors.put("homeCardsAvg", homeCards);
        factors.put("awayCardsAvg", awayCards);
        factors.put("combinedCardsAvg", homeCards + awayCards);
        factors.put("lateGameIntensityScore", calculateLateGameIntensity(homeStats, awayStats));
        
        // Fitness indicator
        double homeGoalDiff = calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 0.0) 
                - calculateGoalsAvg(homeStats.getSeasonConcededHome(), homeStats.getMatchesPlayed(), 0.0);
        double awayGoalDiff = calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 0.0)
                - calculateGoalsAvg(awayStats.getSeasonConcededAway(), awayStats.getMatchesPlayed(), 0.0);
        factors.put("homeGoalDifferencePerGame", homeGoalDiff);
        factors.put("awayGoalDifferencePerGame", awayGoalDiff);
        factors.put("fitnessIndicatorScore", calculateFitnessIndicator(homeStats, awayStats));
        
        // Match situation
        factors.put("homeDrawPct", calculateDrawPercentage(homeStats, true));
        factors.put("awayDrawPct", calculateDrawPercentage(awayStats, false));
        factors.put("matchSituationScore", calculateMatchSituationFactor(homeStats, awayStats));

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
            factors.put("home2HXgProxy", homeXg * SECOND_HALF_RATIO);
            factors.put("away2HXgProxy", awayXg * SECOND_HALF_RATIO);
            
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
        }

        // Late scorer/finisher profile analysis
        List<String> positiveIndicators = new ArrayList<>();
        List<String> riskFlags = new ArrayList<>();
        
        double homeGoalsAvg = calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 1.0);
        double awayGoalsAvg = calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 1.0);
        double combinedGoals = homeGoalsAvg + awayGoalsAvg;
        
        double homeCsPct = calculateCleanSheetPercentage(homeStats, true);
        double awayCsPct = calculateCleanSheetPercentage(awayStats, false);
        double avgCsPct = (homeCsPct + awayCsPct) / 2.0;
        
        factors.put("combinedGoalsAvg", combinedGoals);
        factors.put("avgCleanSheetPct", avgCsPct);
        
        if (combinedGoals >= 3.0 && avgCsPct < 30.0) {
            factors.put("finisherProfile", "Strong finisher");
            positiveIndicators.add("Strong late scoring profile");
        } else if (combinedGoals >= 2.5) {
            factors.put("finisherProfile", "Balanced");
        } else if (combinedGoals < 2.0) {
            factors.put("finisherProfile", "Front-loaded");
            riskFlags.add("Teams tend to score early");
        }
        
        // Late conceder analysis
        double homeConcededAvg = calculateGoalsAvg(homeStats.getSeasonConcededHome(), homeStats.getMatchesPlayed(), 1.0);
        double awayConcededAvg = calculateGoalsAvg(awayStats.getSeasonConcededAway(), awayStats.getMatchesPlayed(), 1.0);
        double combinedConceded = homeConcededAvg + awayConcededAvg;
        factors.put("combinedConcededAvg", combinedConceded);
        
        if (combinedConceded >= 2.5 && avgCsPct < 25.0) {
            factors.put("lateConcedeProfile", "Vulnerable late");
            positiveIndicators.add("Both teams vulnerable late");
        } else if (combinedConceded < 1.5 && avgCsPct > 40.0) {
            factors.put("lateConcedeProfile", "Strong late defense");
            riskFlags.add("Both teams solid defensively late");
        }
        
        // Intensity analysis
        double combinedCards = homeCards + awayCards;
        if (combinedCards >= HIGH_INTENSITY_CARDS_THRESHOLD) {
            factors.put("intensityProfile", "High intensity");
            positiveIndicators.add("High-intensity matchup (more late goals likely)");
        } else if (combinedCards < LOW_INTENSITY_CARDS_THRESHOLD) {
            factors.put("intensityProfile", "Low intensity");
            riskFlags.add("Low-intensity matchup");
        } else {
            factors.put("intensityProfile", "Average");
        }
        
        factors.put("positiveIndicators", positiveIndicators);
        factors.put("riskFlags", riskFlags);
        factors.put("calculatedScore", score);
        
        return factors;
    }

    private String buildDescription(FixtureContext context, ConfidenceLevel confidence, 
            double expected2HGoals, String market) {
        StringBuilder colour = new StringBuilder();

        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        if (hasXgData(homeStats, awayStats)) {
            double combinedXg = safeDouble(homeStats.getXgForAvgHome()) + safeDouble(awayStats.getXgForAvgAway());
            if (combinedXg > XG_HIGH_THRESHOLD) {
                colour.append(String.format("High combined xG (%.2f)", combinedXg));
            }
        }

        double homeCards = safeDouble(homeStats.getCardsAvgHome(), 2.0);
        double awayCards = safeDouble(awayStats.getCardsAvgAway(), 2.0);
        if (homeCards + awayCards >= HIGH_INTENSITY_CARDS_THRESHOLD) {
            if (!colour.isEmpty()) {
                colour.append(". ");
            }
            colour.append("High-intensity matchup expected after the break");
        }

        return VegasTipsterCopy.narrate(VegasTipsterCopy.Brief.builder()
                .confidence(confidence)
                .selection(market)
                .context(context)
                .expected(expected2HGoals, "expected 2H goals")
                .colourNote(colour.isEmpty() ? "Second half goals potential after the interval" : colour.toString())
                .build());
    }
}
