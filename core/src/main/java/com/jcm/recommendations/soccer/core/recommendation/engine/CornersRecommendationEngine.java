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
public class CornersRecommendationEngine implements RecommendationEngine {

    private static final double WEIGHT_HOME_CORNERS = 0.24;
    private static final double WEIGHT_AWAY_CORNERS = 0.24;
    private static final double WEIGHT_HOME_FORM_CORNERS = 0.24;
    private static final double WEIGHT_AWAY_FORM_CORNERS = 0.24;
    private static final double WEIGHT_API_POTENTIAL = 0.04;

    private static final double THRESHOLD_STRONG_OVER = 12.0;
    private static final double THRESHOLD_MODERATE_OVER = 10.0;
    private static final double THRESHOLD_MODERATE_UNDER = 9.5;
    private static final double THRESHOLD_STRONG_UNDER = 8.0;

    @Override
    public RecommendationType getType() {
        return RecommendationType.OVER_CORNERS;
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        log.debug("Analyzing Corners for fixture: fixtureId={}, {} vs {}", 
                context.getFixture().getId(),
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());

        double expectedCorners = calculateExpectedCorners(context);
        
        RecommendationType type;
        ConfidenceLevel confidence;
        String market;

        if (expectedCorners >= THRESHOLD_MODERATE_OVER) {
            type = RecommendationType.OVER_CORNERS;
            confidence = expectedCorners >= THRESHOLD_STRONG_OVER ? ConfidenceLevel.STRONG : ConfidenceLevel.MODERATE;
            market = expectedCorners >= THRESHOLD_STRONG_OVER ? "Over 10.5 Corners" : "Over 9.5 Corners";
        } else if (expectedCorners <= THRESHOLD_MODERATE_UNDER) {
            type = RecommendationType.UNDER_CORNERS;
            confidence = expectedCorners <= THRESHOLD_STRONG_UNDER ? ConfidenceLevel.STRONG : ConfidenceLevel.MODERATE;
            market = expectedCorners <= THRESHOLD_STRONG_UNDER ? "Under 8.5 Corners" : "Under 9.5 Corners";
        } else {
            return Optional.empty();
        }

        Map<String, Object> factors = buildFactors(context, expectedCorners);

        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(type)
                .confidence(confidence)
                .score(expectedCorners)
                .market(market)
                .odds(null)
                .description(buildDescription(context, confidence, expectedCorners, market))
                .factors(factors)
                .build();

        log.info("Corners recommendation generated: fixtureId={}, expectedCorners={}, type={}, confidence={}, market={}", 
                context.getFixture().getId(), String.format("%.1f", expectedCorners), type, confidence, market);

        return Optional.of(recommendation);
    }

    @Override
    public boolean isApplicable(FixtureContext context) {
        return context.hasCompleteData() 
            && context.getHomeTeamStats() != null 
            && context.getAwayTeamStats() != null;
    }

    private double calculateExpectedCorners(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double homeCornersAvg = safeDouble(homeStats.getCornersAvgHome(), 5.0);
        double awayCornersAvg = safeDouble(awayStats.getCornersAvgAway(), 5.0);

        double homeFormCorners = homeCornersAvg;
        double awayFormCorners = awayCornersAvg;
        if (context.hasRecentForm()) {
            homeFormCorners = safeDouble(context.getHomeTeamForm().getCornersAvgHome(), 5.0);
            awayFormCorners = safeDouble(context.getAwayTeamForm().getCornersAvgAway(), 5.0);
        }

        double playingStyleFactor = calculatePlayingStyleFactor(homeStats, awayStats);

        double weightedCorners = (homeCornersAvg + awayCornersAvg + homeFormCorners + awayFormCorners) / 2.0;
        
        return weightedCorners * playingStyleFactor;
    }

    private double calculatePlayingStyleFactor(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        double factor = 1.0;

        double homeGoalsAvg = calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 1.0);
        double awayGoalsAvg = calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 1.0);

        if (homeGoalsAvg > 1.8) factor += 0.05;
        if (awayGoalsAvg > 1.5) factor += 0.05;

        if (homeStats.getPosition() != null && awayStats.getPosition() != null) {
            int positionDiff = Math.abs(homeStats.getPosition() - awayStats.getPosition());
            if (positionDiff <= 3) {
                factor += 0.05;
            }
        }

        return factor;
    }

    private Map<String, Object> buildFactors(FixtureContext context, double expectedCorners) {
        Map<String, Object> factors = new HashMap<>();
        
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        factors.put("expectedCorners", expectedCorners);
        factors.put("homeCornersAvg", safeDouble(homeStats.getCornersAvgHome(), 5.0));
        factors.put("awayCornersAvg", safeDouble(awayStats.getCornersAvgAway(), 5.0));
        factors.put("playingStyleFactor", calculatePlayingStyleFactor(homeStats, awayStats));

        if (context.hasPotentials() && context.getPotentials().getCornersPotential() != null) {
            factors.put("apiCornersPotential", context.getPotentials().getCornersPotential());
        }

        if (context.hasRecentForm()) {
            factors.put("homeFormCornersAvg", safeDouble(context.getHomeTeamForm().getCornersAvgHome(), 5.0));
            factors.put("awayFormCornersAvg", safeDouble(context.getAwayTeamForm().getCornersAvgAway(), 5.0));
        }

        return factors;
    }

    private String buildDescription(FixtureContext context, ConfidenceLevel confidence, double expectedCorners, String market) {
        return String.format("%s confidence %s recommendation (%.1f expected corners) - %s vs %s",
                confidence.getDisplayName(),
                market,
                expectedCorners,
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());
    }
}
