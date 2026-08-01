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

    private static final double WEIGHT_HOME_SCORED_INVERSE = 0.10;
    private static final double WEIGHT_AWAY_SCORED_INVERSE = 0.10;
    private static final double WEIGHT_HOME_CONCEDED_INVERSE = 0.10;
    private static final double WEIGHT_AWAY_CONCEDED_INVERSE = 0.10;
    private static final double WEIGHT_HOME_SCORED_FORM_INVERSE = 0.15;
    private static final double WEIGHT_AWAY_SCORED_FORM_INVERSE = 0.15;
    private static final double WEIGHT_HOME_CLEANSHEET = 0.10;
    private static final double WEIGHT_AWAY_CLEANSHEET = 0.10;
    private static final double WEIGHT_API_POTENTIAL = 0.10;

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

        String market = determineMarket(expectedGoals, score);
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
        double homeScoredAvg = calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 1.0);
        double awayScoredAvg = calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 1.0);
        double homeConcededAvg = calculateGoalsAvg(homeStats.getSeasonConcededHome(), homeStats.getMatchesPlayed(), 1.0);
        double awayConcededAvg = calculateGoalsAvg(awayStats.getSeasonConcededAway(), awayStats.getMatchesPlayed(), 1.0);

        return (homeScoredAvg + awayScoredAvg + homeConcededAvg + awayConcededAvg) / 2.0;
    }

    private double calculateScore(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double homeScoredInverse = inverseNormalizeGoals(calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 1.0));
        double awayScoredInverse = inverseNormalizeGoals(calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 1.0));
        double homeConcededInverse = inverseNormalizeGoals(calculateGoalsAvg(homeStats.getSeasonConcededHome(), homeStats.getMatchesPlayed(), 1.0));
        double awayConcededInverse = inverseNormalizeGoals(calculateGoalsAvg(awayStats.getSeasonConcededAway(), awayStats.getMatchesPlayed(), 1.0));

        double homeScoredFormInverse = 50.0;
        double awayScoredFormInverse = 50.0;
        if (context.hasRecentForm()) {
            homeScoredFormInverse = inverseNormalizeGoals(safeDouble(context.getHomeTeamForm().getScoredAvgHome(), 1.0));
            awayScoredFormInverse = inverseNormalizeGoals(safeDouble(context.getAwayTeamForm().getScoredAvgAway(), 1.0));
        }

        double homeCleanSheet = calculateCleanSheetPercentageOverall(homeStats);
        double awayCleanSheet = calculateCleanSheetPercentageOverall(awayStats);

        double apiPotential = 50.0;
        if (context.hasPotentials() && context.getPotentials().getU15Potential() != null) {
            apiPotential = context.getPotentials().getU15Potential();
        }

        double score = (homeScoredInverse * WEIGHT_HOME_SCORED_INVERSE)
                + (awayScoredInverse * WEIGHT_AWAY_SCORED_INVERSE)
                + (homeConcededInverse * WEIGHT_HOME_CONCEDED_INVERSE)
                + (awayConcededInverse * WEIGHT_AWAY_CONCEDED_INVERSE)
                + (homeScoredFormInverse * WEIGHT_HOME_SCORED_FORM_INVERSE)
                + (awayScoredFormInverse * WEIGHT_AWAY_SCORED_FORM_INVERSE)
                + (homeCleanSheet * WEIGHT_HOME_CLEANSHEET)
                + (awayCleanSheet * WEIGHT_AWAY_CLEANSHEET)
                + (apiPotential * WEIGHT_API_POTENTIAL);

        return clampScore(score);
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
        if (expectedGoals <= 1.5 && score >= THRESHOLD_STRONG) {
            return "Under 1.5 Goals";
        }
        return "Under 2.5 Goals";
    }

    private Map<String, Object> buildFactors(FixtureContext context, double score, double expectedGoals) {
        Map<String, Object> factors = new HashMap<>();
        
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        factors.put("expectedGoals", expectedGoals);
        factors.put("homeGoalsScoredAvg", calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 1.0));
        factors.put("awayGoalsScoredAvg", calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 1.0));
        factors.put("homeCleanSheetPct", calculateCleanSheetPercentageOverall(homeStats));
        factors.put("awayCleanSheetPct", calculateCleanSheetPercentageOverall(awayStats));

        if (context.hasPotentials() && context.getPotentials().getU15Potential() != null) {
            factors.put("apiU15Potential", context.getPotentials().getU15Potential());
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
