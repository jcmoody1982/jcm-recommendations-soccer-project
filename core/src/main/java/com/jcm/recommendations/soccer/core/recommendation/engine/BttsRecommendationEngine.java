package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class BttsRecommendationEngine implements RecommendationEngine {

    private static final double WEIGHT_HOME_BTTS_SEASON = 0.15;
    private static final double WEIGHT_AWAY_BTTS_SEASON = 0.15;
    private static final double WEIGHT_HOME_BTTS_FORM = 0.20;
    private static final double WEIGHT_AWAY_BTTS_FORM = 0.20;
    private static final double WEIGHT_HOME_FTS_INVERSE = 0.10;
    private static final double WEIGHT_AWAY_FTS_INVERSE = 0.10;
    private static final double WEIGHT_API_POTENTIAL = 0.10;

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

        Recommendation recommendation = Recommendation.builder()
                .fixtureId(context.getFixture().getId())
                .homeTeamId(context.getHomeTeam().getId())
                .awayTeamId(context.getAwayTeam().getId())
                .homeTeamName(context.getHomeTeam().getName())
                .awayTeamName(context.getAwayTeam().getName())
                .matchDateUnix(context.getFixture().getDateUnix())
                .type(RecommendationType.BTTS)
                .confidence(confidence)
                .score(score)
                .market("BTTS Yes")
                .description(buildDescription(context, confidence, score))
                .factors(factors)
                .generatedAt(Instant.now())
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

        double homeFtsPct = calculateFailedToScorePercentage(homeStats);
        double awayFtsPct = calculateFailedToScorePercentage(awayStats);

        return homeFtsPct <= FILTER_MAX_FTS_PERCENTAGE && awayFtsPct <= FILTER_MAX_FTS_PERCENTAGE;
    }

    private double calculateScore(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double homeBttsSeason = safePercentage(homeStats.getSeasonBttsPercentageHome());
        double awayBttsSeason = safePercentage(awayStats.getSeasonBttsPercentageAway());

        double homeBttsForm = 50.0;
        double awayBttsForm = 50.0;
        if (context.hasRecentForm()) {
            homeBttsForm = safePercentage(context.getHomeTeamForm().getBttsPercentageHome());
            awayBttsForm = safePercentage(context.getAwayTeamForm().getBttsPercentageAway());
        }

        double homeFtsInverse = 100.0 - calculateFailedToScorePercentage(homeStats);
        double awayFtsInverse = 100.0 - calculateFailedToScorePercentage(awayStats);

        double apiPotential = 50.0;
        if (context.hasPotentials() && context.getPotentials().getBttsPotential() != null) {
            apiPotential = context.getPotentials().getBttsPotential();
        }

        double score = (homeBttsSeason * WEIGHT_HOME_BTTS_SEASON)
                + (awayBttsSeason * WEIGHT_AWAY_BTTS_SEASON)
                + (homeBttsForm * WEIGHT_HOME_BTTS_FORM)
                + (awayBttsForm * WEIGHT_AWAY_BTTS_FORM)
                + (homeFtsInverse * WEIGHT_HOME_FTS_INVERSE)
                + (awayFtsInverse * WEIGHT_AWAY_FTS_INVERSE)
                + (apiPotential * WEIGHT_API_POTENTIAL);

        return Math.min(100.0, Math.max(0.0, score));
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

        factors.put("homeBttsSeasonPct", safePercentage(homeStats.getSeasonBttsPercentageHome()));
        factors.put("awayBttsSeasonPct", safePercentage(awayStats.getSeasonBttsPercentageAway()));
        factors.put("homeFailedToScorePct", calculateFailedToScorePercentage(homeStats));
        factors.put("awayFailedToScorePct", calculateFailedToScorePercentage(awayStats));

        if (context.hasRecentForm()) {
            factors.put("homeBttsFormPct", safePercentage(context.getHomeTeamForm().getBttsPercentageHome()));
            factors.put("awayBttsFormPct", safePercentage(context.getAwayTeamForm().getBttsPercentageAway()));
        }

        if (context.hasPotentials() && context.getPotentials().getBttsPotential() != null) {
            factors.put("apiPotential", context.getPotentials().getBttsPotential());
        }

        factors.put("calculatedScore", score);
        
        return factors;
    }

    private String buildDescription(FixtureContext context, ConfidenceLevel confidence, double score) {
        return String.format("%s confidence BTTS recommendation (%.1f%%) - %s vs %s",
                confidence.getDisplayName(),
                score,
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());
    }

    private double calculateScoredPercentage(TeamSeasonStats stats) {
        if (stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 50.0;
        }
        int scored = stats.getMatchesPlayed() - safeInt(stats.getSeasonFailedToScoreOverall());
        return (scored * 100.0) / stats.getMatchesPlayed();
    }

    private double calculateFailedToScorePercentage(TeamSeasonStats stats) {
        if (stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 0.0;
        }
        return (safeInt(stats.getSeasonFailedToScoreOverall()) * 100.0) / stats.getMatchesPlayed();
    }

    private double safePercentage(Double value) {
        return value != null ? value : 50.0;
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }
}
