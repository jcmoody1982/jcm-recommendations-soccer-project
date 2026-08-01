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
public class BttsRecommendationEngine implements RecommendationEngine {

    // Base weights when form data IS available (total = 1.0)
    private static final double WEIGHT_HOME_BTTS_SEASON = 0.15;
    private static final double WEIGHT_AWAY_BTTS_SEASON = 0.15;
    private static final double WEIGHT_HOME_BTTS_FORM = 0.20;
    private static final double WEIGHT_AWAY_BTTS_FORM = 0.20;
    private static final double WEIGHT_HOME_FTS_INVERSE = 0.10;
    private static final double WEIGHT_AWAY_FTS_INVERSE = 0.10;
    private static final double WEIGHT_API_POTENTIAL = 0.10;

    // Redistributed weights when form data is NOT available (total = 1.0)
    private static final double WEIGHT_BTTS_SEASON_NO_FORM = 0.25;  // 0.15 → 0.25 each
    private static final double WEIGHT_FTS_INVERSE_NO_FORM = 0.175; // 0.10 → 0.175 each
    private static final double WEIGHT_API_POTENTIAL_NO_FORM = 0.15; // 0.10 → 0.15

    // Goals context boost for prolific scorers
    private static final double GOALS_BOOST_HOME_THRESHOLD = 1.5;  // Goals scored per home game
    private static final double GOALS_BOOST_AWAY_THRESHOLD = 1.0;  // Goals scored per away game
    private static final double GOALS_BOOST_AMOUNT = 5.0;          // Bonus percentage points

    // Defensive leakiness boost for porous defenses
    private static final double LEAKY_DEFENSE_HOME_THRESHOLD = 1.2;  // Goals conceded per home game
    private static final double LEAKY_DEFENSE_AWAY_THRESHOLD = 1.0;  // Goals conceded per away game
    private static final double LEAKY_DEFENSE_BOOST_AMOUNT = 4.0;    // Bonus percentage points

    private static final double THRESHOLD_STRONG = 80.0;
    private static final double THRESHOLD_MODERATE = 65.0;
    
    private static final double FILTER_MIN_SCORED_PERCENTAGE = 50.0;
    private static final double FILTER_MAX_FTS_PERCENTAGE = 40.0;

    @Override
    public RecommendationType getType() {
        return RecommendationType.BTTS;
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        log.debug("Analyzing BTTS for fixture: fixtureId={}, {} vs {}", 
                context.getFixture().getId(),
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());

        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        if (!passesFilters(homeStats, awayStats)) {
            log.debug("Fixture failed BTTS filters: fixtureId={}", context.getFixture().getId());
            return Optional.empty();
        }

        double score = calculateScore(context);
        ConfidenceLevel confidence = determineConfidence(score);

        if (confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        Map<String, Object> factors = buildFactors(context, score);
        Double odds = context.hasOdds() ? context.getOdds().getOddsBttsYes() : null;

        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(RecommendationType.BTTS)
                .confidence(confidence)
                .score(score)
                .market("BTTS Yes")
                .odds(odds)
                .description(buildDescription(context, confidence, score))
                .factors(factors)
                .build();

        log.info("BTTS recommendation generated: fixtureId={}, score={}, confidence={}", 
                context.getFixture().getId(), String.format("%.1f", score), confidence);

        return Optional.of(recommendation);
    }

    @Override
    public boolean isApplicable(FixtureContext context) {
        return context.hasCompleteData() 
            && context.getHomeTeamStats() != null 
            && context.getAwayTeamStats() != null;
    }

    private boolean passesFilters(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        double homeScoredPct = calculateScoredPercentage(homeStats);
        double awayScoredPct = calculateScoredPercentage(awayStats);
        
        if (homeScoredPct < FILTER_MIN_SCORED_PERCENTAGE || awayScoredPct < FILTER_MIN_SCORED_PERCENTAGE) {
            return false;
        }

        double homeFtsPct = calculateFailedToScorePercentageOverall(homeStats);
        double awayFtsPct = calculateFailedToScorePercentageOverall(awayStats);

        return homeFtsPct <= FILTER_MAX_FTS_PERCENTAGE && awayFtsPct <= FILTER_MAX_FTS_PERCENTAGE;
    }

    private double calculateScore(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double homeBttsSeason = safePercentage(homeStats.getSeasonBttsPercentageHome());
        double awayBttsSeason = safePercentage(awayStats.getSeasonBttsPercentageAway());

        double homeFtsInverse = 100.0 - calculateFailedToScorePercentageOverall(homeStats);
        double awayFtsInverse = 100.0 - calculateFailedToScorePercentageOverall(awayStats);

        double apiPotential = 50.0;
        if (context.hasPotentials() && context.getPotentials().getBttsPotential() != null) {
            apiPotential = context.getPotentials().getBttsPotential();
        }

        double score;
        
        if (context.hasRecentForm()) {
            // Full calculation with form data - use standard weights
            double homeBttsForm = safePercentage(context.getHomeTeamForm().getBttsPercentageHome());
            double awayBttsForm = safePercentage(context.getAwayTeamForm().getBttsPercentageAway());

            score = (homeBttsSeason * WEIGHT_HOME_BTTS_SEASON)
                    + (awayBttsSeason * WEIGHT_AWAY_BTTS_SEASON)
                    + (homeBttsForm * WEIGHT_HOME_BTTS_FORM)
                    + (awayBttsForm * WEIGHT_AWAY_BTTS_FORM)
                    + (homeFtsInverse * WEIGHT_HOME_FTS_INVERSE)
                    + (awayFtsInverse * WEIGHT_AWAY_FTS_INVERSE)
                    + (apiPotential * WEIGHT_API_POTENTIAL);
        } else {
            // No form data - redistribute weights to available data
            log.debug("No form data available, using redistributed weights for fixture: {}", 
                    context.getFixture().getId());
            
            score = (homeBttsSeason * WEIGHT_BTTS_SEASON_NO_FORM)
                    + (awayBttsSeason * WEIGHT_BTTS_SEASON_NO_FORM)
                    + (homeFtsInverse * WEIGHT_FTS_INVERSE_NO_FORM)
                    + (awayFtsInverse * WEIGHT_FTS_INVERSE_NO_FORM)
                    + (apiPotential * WEIGHT_API_POTENTIAL_NO_FORM);
        }

        // Apply goals context boost for prolific scorers
        double goalsBoost = calculateGoalsBoost(homeStats, awayStats);
        if (goalsBoost > 0) {
            log.debug("Applying goals boost of {} for fixture: {}", goalsBoost, context.getFixture().getId());
        }
        score += goalsBoost;

        // Apply defensive leakiness boost for porous defenses
        double leakyDefenseBoost = calculateLeakyDefenseBoost(homeStats, awayStats);
        if (leakyDefenseBoost > 0) {
            log.debug("Applying leaky defense boost of {} for fixture: {}", leakyDefenseBoost, context.getFixture().getId());
        }
        score += leakyDefenseBoost;

        return clampScore(score);
    }
    
    private double calculateGoalsBoost(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        double homeGoalsAvg = calculateGoalsAvgHome(homeStats);
        double awayGoalsAvg = calculateGoalsAvgAway(awayStats);
        
        if (homeGoalsAvg >= GOALS_BOOST_HOME_THRESHOLD && awayGoalsAvg >= GOALS_BOOST_AWAY_THRESHOLD) {
            return GOALS_BOOST_AMOUNT;
        }
        return 0.0;
    }
    
    private double calculateGoalsAvgHome(TeamSeasonStats stats) {
        if (stats == null || stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 0.0;
        }
        int homeMatches = stats.getMatchesPlayed() / 2;
        if (homeMatches == 0) return 0.0;
        return safeInt(stats.getSeasonGoalsHome()) / (double) homeMatches;
    }
    
    private double calculateGoalsAvgAway(TeamSeasonStats stats) {
        if (stats == null || stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 0.0;
        }
        int awayMatches = stats.getMatchesPlayed() / 2;
        if (awayMatches == 0) return 0.0;
        return safeInt(stats.getSeasonGoalsAway()) / (double) awayMatches;
    }
    
    private double calculateLeakyDefenseBoost(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        double homeConcededAvg = calculateConcededAvgHome(homeStats);
        double awayConcededAvg = calculateConcededAvgAway(awayStats);
        
        if (homeConcededAvg >= LEAKY_DEFENSE_HOME_THRESHOLD && awayConcededAvg >= LEAKY_DEFENSE_AWAY_THRESHOLD) {
            return LEAKY_DEFENSE_BOOST_AMOUNT;
        }
        return 0.0;
    }
    
    private double calculateConcededAvgHome(TeamSeasonStats stats) {
        if (stats == null || stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 0.0;
        }
        int homeMatches = stats.getMatchesPlayed() / 2;
        if (homeMatches == 0) return 0.0;
        return safeInt(stats.getSeasonConcededHome()) / (double) homeMatches;
    }
    
    private double calculateConcededAvgAway(TeamSeasonStats stats) {
        if (stats == null || stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 0.0;
        }
        int awayMatches = stats.getMatchesPlayed() / 2;
        if (awayMatches == 0) return 0.0;
        return safeInt(stats.getSeasonConcededAway()) / (double) awayMatches;
    }

    private ConfidenceLevel determineConfidence(double score) {
        if (score >= THRESHOLD_STRONG) {
            return ConfidenceLevel.STRONG;
        } else if (score >= THRESHOLD_MODERATE) {
            return ConfidenceLevel.MODERATE;
        }
        return ConfidenceLevel.WEAK;
    }

    private Map<String, Object> buildFactors(FixtureContext context, double score) {
        Map<String, Object> factors = new HashMap<>();
        
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        // Season BTTS percentages
        factors.put("homeBttsSeasonPct", safePercentage(homeStats.getSeasonBttsPercentageHome()));
        factors.put("awayBttsSeasonPct", safePercentage(awayStats.getSeasonBttsPercentageAway()));
        
        // Failed to score percentages
        factors.put("homeFailedToScorePct", calculateFailedToScorePercentageOverall(homeStats));
        factors.put("awayFailedToScorePct", calculateFailedToScorePercentageOverall(awayStats));

        // Form data (if available)
        factors.put("formDataAvailable", context.hasRecentForm());
        if (context.hasRecentForm()) {
            factors.put("homeBttsFormPct", safePercentage(context.getHomeTeamForm().getBttsPercentageHome()));
            factors.put("awayBttsFormPct", safePercentage(context.getAwayTeamForm().getBttsPercentageAway()));
        }

        // API potential
        if (context.hasPotentials() && context.getPotentials().getBttsPotential() != null) {
            factors.put("apiPotential", context.getPotentials().getBttsPotential());
        }

        // Goals context (for boost calculation)
        double homeGoalsAvg = calculateGoalsAvgHome(homeStats);
        double awayGoalsAvg = calculateGoalsAvgAway(awayStats);
        factors.put("homeGoalsAvgHome", homeGoalsAvg);
        factors.put("awayGoalsAvgAway", awayGoalsAvg);
        
        // Track if goals boost was applied
        double goalsBoost = calculateGoalsBoost(homeStats, awayStats);
        factors.put("goalsBoostApplied", goalsBoost > 0);
        if (goalsBoost > 0) {
            factors.put("goalsBoostAmount", goalsBoost);
        }

        // Track defensive leakiness
        double homeConcededAvg = calculateConcededAvgHome(homeStats);
        double awayConcededAvg = calculateConcededAvgAway(awayStats);
        factors.put("homeConcededAvgHome", homeConcededAvg);
        factors.put("awayConcededAvgAway", awayConcededAvg);
        
        double leakyDefenseBoost = calculateLeakyDefenseBoost(homeStats, awayStats);
        factors.put("leakyDefenseBoostApplied", leakyDefenseBoost > 0);
        if (leakyDefenseBoost > 0) {
            factors.put("leakyDefenseBoostAmount", leakyDefenseBoost);
        }

        factors.put("calculatedScore", score);
        
        return factors;
    }

    private String buildDescription(FixtureContext context, ConfidenceLevel confidence, double score) {
        return RecommendationFactory.buildStandardDescription(
                confidence, "BTTS", score, "score", context);
    }
}
