package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class OverGoalsRecommendationEngine implements RecommendationEngine {

    private static final double WEIGHT_HOME_SCORED_SEASON = 0.10;
    private static final double WEIGHT_AWAY_SCORED_SEASON = 0.10;
    private static final double WEIGHT_HOME_CONCEDED_SEASON = 0.10;
    private static final double WEIGHT_AWAY_CONCEDED_SEASON = 0.10;
    private static final double WEIGHT_HOME_SCORED_FORM = 0.15;
    private static final double WEIGHT_AWAY_SCORED_FORM = 0.15;
    private static final double WEIGHT_HOME_OVER25_SEASON = 0.10;
    private static final double WEIGHT_AWAY_OVER25_SEASON = 0.10;
    private static final double WEIGHT_API_POTENTIAL = 0.10;

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

        String market = determineMarket(expectedGoals, score);
        Map<String, Object> factors = buildFactors(context, score, expectedGoals);

        Recommendation recommendation = Recommendation.builder()
                .fixtureId(context.getFixture().getId())
                .homeTeamId(context.getHomeTeam().getId())
                .awayTeamId(context.getAwayTeam().getId())
                .homeTeamName(context.getHomeTeam().getName())
                .awayTeamName(context.getAwayTeam().getName())
                .matchDateUnix(context.getFixture().getDateUnix())
                .type(RecommendationType.OVER_GOALS)
                .confidence(confidence)
                .score(score)
                .market(market)
                .description(buildDescription(context, confidence, expectedGoals, market))
                .factors(factors)
                .generatedAt(Instant.now())
                .build();

        log.info("Over Goals recommendation generated: fixtureId={}, expectedGoals={}, score={}, confidence={}, market={}", 
                context.getFixture().getId(), String.format("%.2f", expectedGoals), 
                String.format("%.1f", score), confidence, market);

        return Optional.of(recommendation);
    }

    private double calculateExpectedGoals(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        double homeScoredAvg = calculateGoalsAverage(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed());
        double awayScoredAvg = calculateGoalsAverage(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed());
        double homeConcededAvg = calculateGoalsAverage(homeStats.getSeasonConcededHome(), homeStats.getMatchesPlayed());
        double awayConcededAvg = calculateGoalsAverage(awayStats.getSeasonConcededAway(), awayStats.getMatchesPlayed());

        return (homeScoredAvg + awayScoredAvg + homeConcededAvg + awayConcededAvg) / 2.0;
    }

    private double calculateScore(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double homeScoredSeason = normalizeGoals(calculateGoalsAverage(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed()));
        double awayScoredSeason = normalizeGoals(calculateGoalsAverage(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed()));
        double homeConcededSeason = normalizeGoals(calculateGoalsAverage(homeStats.getSeasonConcededHome(), homeStats.getMatchesPlayed()));
        double awayConcededSeason = normalizeGoals(calculateGoalsAverage(awayStats.getSeasonConcededAway(), awayStats.getMatchesPlayed()));

        double homeScoredForm = 50.0;
        double awayScoredForm = 50.0;
        if (context.hasRecentForm()) {
            homeScoredForm = normalizeGoals(safeDouble(context.getHomeTeamForm().getScoredAvgHome()));
            awayScoredForm = normalizeGoals(safeDouble(context.getAwayTeamForm().getScoredAvgAway()));
        }

        double homeOver25 = safePercentage(homeStats.getSeasonOver25PercentageOverall());
        double awayOver25 = safePercentage(awayStats.getSeasonOver25PercentageOverall());

        double apiPotential = 50.0;
        if (context.hasPotentials() && context.getPotentials().getO25Potential() != null) {
            apiPotential = context.getPotentials().getO25Potential();
        }

        double score = (homeScoredSeason * WEIGHT_HOME_SCORED_SEASON)
                + (awayScoredSeason * WEIGHT_AWAY_SCORED_SEASON)
                + (homeConcededSeason * WEIGHT_HOME_CONCEDED_SEASON)
                + (awayConcededSeason * WEIGHT_AWAY_CONCEDED_SEASON)
                + (homeScoredForm * WEIGHT_HOME_SCORED_FORM)
                + (awayScoredForm * WEIGHT_AWAY_SCORED_FORM)
                + (homeOver25 * WEIGHT_HOME_OVER25_SEASON)
                + (awayOver25 * WEIGHT_AWAY_OVER25_SEASON)
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

    private String determineMarket(double expectedGoals, double score) {
        if (expectedGoals >= 3.5 && score >= THRESHOLD_STRONG) {
            return "Over 3.5 Goals";
        }
        return "Over 2.5 Goals";
    }

    private Map<String, Object> buildFactors(FixtureContext context, double score, double expectedGoals) {
        Map<String, Object> factors = new HashMap<>();
        
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        factors.put("expectedGoals", expectedGoals);
        factors.put("homeGoalsScoredAvg", calculateGoalsAverage(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed()));
        factors.put("awayGoalsScoredAvg", calculateGoalsAverage(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed()));
        factors.put("homeOver25Pct", safePercentage(homeStats.getSeasonOver25PercentageOverall()));
        factors.put("awayOver25Pct", safePercentage(awayStats.getSeasonOver25PercentageOverall()));

        if (context.hasPotentials() && context.getPotentials().getO25Potential() != null) {
            factors.put("apiO25Potential", context.getPotentials().getO25Potential());
        }

        factors.put("calculatedScore", score);
        
        return factors;
    }

    private String buildDescription(FixtureContext context, ConfidenceLevel confidence, double expectedGoals, String market) {
        return String.format("%s confidence %s recommendation (%.2f expected goals) - %s vs %s",
                confidence.getDisplayName(),
                market,
                expectedGoals,
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());
    }

    private double calculateGoalsAverage(Integer goals, Integer matches) {
        if (matches == null || matches == 0) {
            return 1.0;
        }
        return (goals != null ? goals : 0) / (double) matches;
    }

    private double normalizeGoals(double goalsAvg) {
        return Math.min(100.0, goalsAvg * 33.33);
    }

    private double safePercentage(Double value) {
        return value != null ? value : 50.0;
    }

    private double safeDouble(Double value) {
        return value != null ? value : 1.0;
    }
}
