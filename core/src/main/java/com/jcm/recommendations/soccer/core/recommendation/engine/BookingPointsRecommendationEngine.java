package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.domain.RefereeStats;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class BookingPointsRecommendationEngine implements RecommendationEngine {

    private static final int YELLOW_CARD_POINTS = 10;
    private static final int RED_CARD_POINTS = 25;

    private static final double WEIGHT_HOME_CARDS = 0.20;
    private static final double WEIGHT_AWAY_CARDS = 0.20;
    private static final double WEIGHT_REFEREE_CARDS = 0.25;
    private static final double WEIGHT_RED_CARD_RISK = 0.10;
    private static final double WEIGHT_REFEREE_RELIABILITY = 0.10;
    private static final double WEIGHT_MATCH_INTENSITY = 0.15;

    private static final double THRESHOLD_STRONG_OVER = 50.0;
    private static final double THRESHOLD_MODERATE_OVER = 40.0;
    private static final double THRESHOLD_MODERATE_UNDER = 39.0;
    private static final double THRESHOLD_STRONG_UNDER = 30.0;

    @Override
    public RecommendationType getType() {
        return RecommendationType.BOOKING_POINTS;
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        log.debug("Analyzing Booking Points for fixture: fixtureId={}, {} vs {}", 
                context.getFixture().getId(),
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());

        double expectedBookingPoints = calculateExpectedBookingPoints(context);
        ConfidenceLevel confidence = determineConfidence(expectedBookingPoints);
        String market = determineMarket(expectedBookingPoints);

        if (confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        Map<String, Object> factors = buildFactors(context, expectedBookingPoints);

        Recommendation recommendation = Recommendation.builder()
                .fixtureId(context.getFixture().getId())
                .homeTeamId(context.getHomeTeam().getId())
                .awayTeamId(context.getAwayTeam().getId())
                .homeTeamName(context.getHomeTeam().getName())
                .awayTeamName(context.getAwayTeam().getName())
                .matchDateUnix(context.getFixture().getDateUnix())
                .leagueId(context.getLeague() != null ? context.getLeague().getCurrentSeasonId() : null)
                .leagueName(context.getLeague() != null ? context.getLeague().getName() : null)
                .leagueImage(context.getLeague() != null ? context.getLeague().getImage() : null)
                .type(RecommendationType.BOOKING_POINTS)
                .confidence(confidence)
                .score(expectedBookingPoints)
                .market(market)
                .description(buildDescription(context, confidence, expectedBookingPoints, market))
                .factors(factors)
                .generatedAt(Instant.now())
                .build();

        log.info("Booking Points recommendation generated: fixtureId={}, expectedPoints={}, confidence={}, market={}", 
                context.getFixture().getId(), String.format("%.1f", expectedBookingPoints), confidence, market);

        return Optional.of(recommendation);
    }

    @Override
    public boolean isApplicable(FixtureContext context) {
        return context.hasCompleteData() 
            && context.getHomeTeamStats() != null 
            && context.getAwayTeamStats() != null;
    }

    private double calculateExpectedBookingPoints(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double homeCardsAvg = safeDouble(homeStats.getCardsAvgHome()) * YELLOW_CARD_POINTS;
        double awayCardsAvg = safeDouble(awayStats.getCardsAvgAway()) * YELLOW_CARD_POINTS;

        double refereeCardsAvg = 0.0;
        double refereeReliability = 0.5;
        if (context.hasRefereeStats()) {
            RefereeStats refStats = context.getRefereeStats();
            refereeCardsAvg = safeDouble(refStats.getCardsPerMatchOverall()) * YELLOW_CARD_POINTS;
            refereeReliability = calculateRefereeReliability(refStats);
        }

        double redCardRisk = calculateRedCardRisk(context);
        double matchIntensity = calculateMatchIntensity(context);

        double basePoints = (homeCardsAvg * WEIGHT_HOME_CARDS)
                + (awayCardsAvg * WEIGHT_AWAY_CARDS)
                + (refereeCardsAvg * WEIGHT_REFEREE_CARDS)
                + (redCardRisk * WEIGHT_RED_CARD_RISK)
                + (refereeReliability * refereeCardsAvg * WEIGHT_REFEREE_RELIABILITY);

        return basePoints * matchIntensity;
    }

    private double calculateRefereeReliability(RefereeStats refStats) {
        if (refStats.getAppearancesOverall() == null) {
            return 0.5;
        }
        int appearances = refStats.getAppearancesOverall();
        if (appearances >= 10) {
            return 1.0;
        } else if (appearances >= 5) {
            return 0.8;
        }
        return 0.5;
    }

    private double calculateRedCardRisk(FixtureContext context) {
        double risk = 0.0;
        
        if (context.hasRefereeStats()) {
            RefereeStats refStats = context.getRefereeStats();
            if (refStats.getRedCardsOverall() != null && refStats.getAppearancesOverall() != null 
                    && refStats.getAppearancesOverall() > 0) {
                double redCardRate = refStats.getRedCardsOverall() / (double) refStats.getAppearancesOverall();
                risk = redCardRate * RED_CARD_POINTS;
            }
        }
        
        return risk;
    }

    private double calculateMatchIntensity(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        if (homeStats.getPosition() == null || awayStats.getPosition() == null) {
            return 1.0;
        }

        int positionDiff = Math.abs(homeStats.getPosition() - awayStats.getPosition());
        
        if (positionDiff <= 3) {
            return 1.2;
        } else if (positionDiff <= 6) {
            return 1.1;
        }
        
        return 1.0;
    }

    private ConfidenceLevel determineConfidence(double expectedPoints) {
        if (expectedPoints >= THRESHOLD_STRONG_OVER || expectedPoints < THRESHOLD_STRONG_UNDER) {
            return ConfidenceLevel.STRONG;
        } else if (expectedPoints >= THRESHOLD_MODERATE_OVER || expectedPoints <= THRESHOLD_MODERATE_UNDER) {
            return ConfidenceLevel.MODERATE;
        }
        return ConfidenceLevel.WEAK;
    }

    private String determineMarket(double expectedPoints) {
        if (expectedPoints >= THRESHOLD_STRONG_OVER) {
            return "Over 50 Booking Points";
        } else if (expectedPoints >= THRESHOLD_MODERATE_OVER) {
            return "Over 40 Booking Points";
        } else if (expectedPoints < THRESHOLD_STRONG_UNDER) {
            return "Under 30 Booking Points";
        } else {
            return "Under 40 Booking Points";
        }
    }

    private Map<String, Object> buildFactors(FixtureContext context, double expectedPoints) {
        Map<String, Object> factors = new HashMap<>();
        
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        factors.put("expectedBookingPoints", expectedPoints);
        factors.put("homeCardsAvg", safeDouble(homeStats.getCardsAvgHome()));
        factors.put("awayCardsAvg", safeDouble(awayStats.getCardsAvgAway()));
        factors.put("matchIntensityFactor", calculateMatchIntensity(context));

        if (context.hasRefereeStats()) {
            RefereeStats refStats = context.getRefereeStats();
            factors.put("refereeCardsAvg", safeDouble(refStats.getCardsPerMatchOverall()));
            factors.put("refereeAppearances", refStats.getAppearancesOverall());
            factors.put("refereeReliability", calculateRefereeReliability(refStats));
        }

        return factors;
    }

    private String buildDescription(FixtureContext context, ConfidenceLevel confidence, double expectedPoints, String market) {
        return String.format("%s confidence %s recommendation (%.1f expected points) - %s vs %s",
                confidence.getDisplayName(),
                market,
                expectedPoints,
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());
    }

    private double safeDouble(Double value) {
        return value != null ? value : 0.0;
    }
}
