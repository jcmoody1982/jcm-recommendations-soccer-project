package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.core.recommendation.util.RecommendationFactory;
import com.jcm.recommendations.soccer.domain.RefereeStats;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.*;

@Component
@Slf4j
public class BookingPointsRecommendationEngine implements RecommendationEngine {

    private static final int YELLOW_CARD_POINTS = 10;
    private static final int RED_CARD_POINTS = 25;

    // Base weights when referee data IS available (total for weighted calculation)
    private static final double WEIGHT_HOME_CARDS_SEASON = 0.12;
    private static final double WEIGHT_AWAY_CARDS_SEASON = 0.12;
    private static final double WEIGHT_HOME_CARDS_FORM = 0.10;
    private static final double WEIGHT_AWAY_CARDS_FORM = 0.10;
    private static final double WEIGHT_REFEREE_CARDS = 0.20;
    private static final double WEIGHT_REFEREE_O35_CARDS = 0.08;
    private static final double WEIGHT_RED_CARD_RISK = 0.06;
    private static final double WEIGHT_API_POTENTIAL = 0.10;
    private static final double WEIGHT_MATCH_INTENSITY = 0.12;

    // Redistributed weights when referee data is NOT available
    private static final double WEIGHT_CARDS_SEASON_NO_REF = 0.20;     // 0.12 → 0.20 each
    private static final double WEIGHT_CARDS_FORM_NO_REF = 0.15;       // 0.10 → 0.15 each
    private static final double WEIGHT_API_POTENTIAL_NO_REF = 0.18;    // 0.10 → 0.18
    private static final double WEIGHT_MATCH_INTENSITY_NO_REF = 0.12;  // same

    // Redistributed weights when form data is NOT available (but referee is)
    private static final double WEIGHT_CARDS_SEASON_NO_FORM = 0.18;    // 0.12 → 0.18 each
    private static final double WEIGHT_API_POTENTIAL_NO_FORM = 0.14;   // 0.10 → 0.14

    // High-cards matchup boost
    private static final double HIGH_CARDS_TEAM_THRESHOLD = 2.0;       // Cards per game
    private static final double HIGH_CARDS_BOOST_POINTS = 5.0;         // Bonus points

    // Referee strictness boost
    private static final double REFEREE_STRICT_O35_THRESHOLD = 60.0;   // Over 3.5 cards %
    private static final double REFEREE_STRICT_BOOST_POINTS = 5.0;     // Bonus points

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

        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(RecommendationType.BOOKING_POINTS)
                .confidence(confidence)
                .score(expectedBookingPoints)
                .market(market)
                .odds(null)
                .description(buildDescription(context, confidence, expectedBookingPoints, market))
                .factors(factors)
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

        // Season cards (converted to points)
        double homeCardsSeason = safeDouble(homeStats.getCardsAvgHome()) * YELLOW_CARD_POINTS;
        double awayCardsSeason = safeDouble(awayStats.getCardsAvgAway()) * YELLOW_CARD_POINTS;

        // API potential
        double apiPotential = 40.0; // Default neutral
        if (context.hasPotentials() && context.getPotentials().getCardsPotential() != null) {
            apiPotential = context.getPotentials().getCardsPotential();
        }

        // Match intensity multiplier
        double matchIntensity = calculateMatchIntensity(context);

        double basePoints;
        boolean hasRefereeData = context.hasRefereeStats();
        boolean hasFormData = context.hasRecentForm();

        if (hasRefereeData && hasFormData) {
            // Full calculation with all data
            RefereeStats refStats = context.getRefereeStats();
            double refereeCardsAvg = safeDouble(refStats.getCardsPerMatchOverall()) * YELLOW_CARD_POINTS;
            double refereeO35Pct = safePercentage(refStats.getOver35CardsPercentageOverall());
            double redCardRisk = calculateRedCardRisk(context);

            double homeCardsForm = safeDouble(context.getHomeTeamForm().getCardsAvgHome()) * YELLOW_CARD_POINTS;
            double awayCardsForm = safeDouble(context.getAwayTeamForm().getCardsAvgAway()) * YELLOW_CARD_POINTS;

            basePoints = (homeCardsSeason * WEIGHT_HOME_CARDS_SEASON)
                    + (awayCardsSeason * WEIGHT_AWAY_CARDS_SEASON)
                    + (homeCardsForm * WEIGHT_HOME_CARDS_FORM)
                    + (awayCardsForm * WEIGHT_AWAY_CARDS_FORM)
                    + (refereeCardsAvg * WEIGHT_REFEREE_CARDS)
                    + (refereeO35Pct * 0.5 * WEIGHT_REFEREE_O35_CARDS) // Scale O35% to points
                    + (redCardRisk * WEIGHT_RED_CARD_RISK)
                    + (apiPotential * 0.5 * WEIGHT_API_POTENTIAL); // Scale API to points
        } else if (hasRefereeData) {
            // Referee data but no form
            log.debug("No form data available, using redistributed weights for fixture: {}", 
                    context.getFixture().getId());
            
            RefereeStats refStats = context.getRefereeStats();
            double refereeCardsAvg = safeDouble(refStats.getCardsPerMatchOverall()) * YELLOW_CARD_POINTS;
            double refereeO35Pct = safePercentage(refStats.getOver35CardsPercentageOverall());
            double redCardRisk = calculateRedCardRisk(context);

            basePoints = (homeCardsSeason * WEIGHT_CARDS_SEASON_NO_FORM)
                    + (awayCardsSeason * WEIGHT_CARDS_SEASON_NO_FORM)
                    + (refereeCardsAvg * WEIGHT_REFEREE_CARDS)
                    + (refereeO35Pct * 0.5 * WEIGHT_REFEREE_O35_CARDS)
                    + (redCardRisk * WEIGHT_RED_CARD_RISK)
                    + (apiPotential * 0.5 * WEIGHT_API_POTENTIAL_NO_FORM);
        } else if (hasFormData) {
            // Form data but no referee
            log.debug("No referee data available, using redistributed weights for fixture: {}", 
                    context.getFixture().getId());
            
            double homeCardsForm = safeDouble(context.getHomeTeamForm().getCardsAvgHome()) * YELLOW_CARD_POINTS;
            double awayCardsForm = safeDouble(context.getAwayTeamForm().getCardsAvgAway()) * YELLOW_CARD_POINTS;

            basePoints = (homeCardsSeason * WEIGHT_CARDS_SEASON_NO_REF)
                    + (awayCardsSeason * WEIGHT_CARDS_SEASON_NO_REF)
                    + (homeCardsForm * WEIGHT_CARDS_FORM_NO_REF)
                    + (awayCardsForm * WEIGHT_CARDS_FORM_NO_REF)
                    + (apiPotential * 0.5 * WEIGHT_API_POTENTIAL_NO_REF);
        } else {
            // Neither referee nor form data
            log.debug("No referee or form data available for fixture: {}", context.getFixture().getId());
            
            basePoints = (homeCardsSeason * 0.35)
                    + (awayCardsSeason * 0.35)
                    + (apiPotential * 0.5 * 0.30);
        }

        // Apply match intensity multiplier
        double points = basePoints * matchIntensity;

        // Apply high-cards matchup boost
        double highCardsBoost = calculateHighCardsBoost(homeStats, awayStats);
        if (highCardsBoost > 0) {
            log.debug("Applying high-cards boost of {} for fixture: {}", highCardsBoost, context.getFixture().getId());
        }
        points += highCardsBoost;

        // Apply referee strictness boost
        double strictnessBoost = calculateRefereeStrictnessBoost(context);
        if (strictnessBoost > 0) {
            log.debug("Applying referee strictness boost of {} for fixture: {}", strictnessBoost, context.getFixture().getId());
        }
        points += strictnessBoost;

        return points;
    }

    private double calculateHighCardsBoost(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        double homeCardsAvg = safeDouble(homeStats.getCardsAvgHome());
        double awayCardsAvg = safeDouble(awayStats.getCardsAvgAway());
        
        if (homeCardsAvg >= HIGH_CARDS_TEAM_THRESHOLD && awayCardsAvg >= HIGH_CARDS_TEAM_THRESHOLD) {
            return HIGH_CARDS_BOOST_POINTS;
        }
        return 0.0;
    }

    private double calculateRefereeStrictnessBoost(FixtureContext context) {
        if (!context.hasRefereeStats()) {
            return 0.0;
        }
        
        RefereeStats refStats = context.getRefereeStats();
        double o35Pct = safePercentage(refStats.getOver35CardsPercentageOverall());
        
        if (o35Pct >= REFEREE_STRICT_O35_THRESHOLD) {
            return REFEREE_STRICT_BOOST_POINTS;
        }
        return 0.0;
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

        // Expected booking points
        factors.put("expectedBookingPoints", expectedPoints);

        // Season cards averages
        double homeCardsAvg = safeDouble(homeStats.getCardsAvgHome());
        double awayCardsAvg = safeDouble(awayStats.getCardsAvgAway());
        factors.put("homeCardsSeasonAvg", homeCardsAvg);
        factors.put("awayCardsSeasonAvg", awayCardsAvg);

        // Form data availability
        factors.put("formDataAvailable", context.hasRecentForm());
        if (context.hasRecentForm()) {
            factors.put("homeCardsFormAvg", safeDouble(context.getHomeTeamForm().getCardsAvgHome()));
            factors.put("awayCardsFormAvg", safeDouble(context.getAwayTeamForm().getCardsAvgAway()));
        }

        // Referee data availability
        factors.put("refereeDataAvailable", context.hasRefereeStats());
        if (context.hasRefereeStats()) {
            RefereeStats refStats = context.getRefereeStats();
            factors.put("refereeCardsAvg", safeDouble(refStats.getCardsPerMatchOverall()));
            factors.put("refereeAppearances", refStats.getAppearancesOverall());
            factors.put("refereeReliability", calculateRefereeReliability(refStats));
            factors.put("refereeYellowCards", refStats.getYellowCardsOverall());
            factors.put("refereeRedCards", refStats.getRedCardsOverall());
            factors.put("refereeOver35CardsPct", safePercentage(refStats.getOver35CardsPercentageOverall()));
        }

        // API potential
        if (context.hasPotentials() && context.getPotentials().getCardsPotential() != null) {
            factors.put("apiCardsPotential", context.getPotentials().getCardsPotential());
        }

        // Match intensity
        double matchIntensity = calculateMatchIntensity(context);
        factors.put("matchIntensityFactor", matchIntensity);
        if (homeStats.getPosition() != null && awayStats.getPosition() != null) {
            factors.put("homePosition", homeStats.getPosition());
            factors.put("awayPosition", awayStats.getPosition());
            factors.put("positionDifference", Math.abs(homeStats.getPosition() - awayStats.getPosition()));
        }

        // High-cards boost
        double highCardsBoost = calculateHighCardsBoost(homeStats, awayStats);
        factors.put("highCardsBoostApplied", highCardsBoost > 0);
        if (highCardsBoost > 0) {
            factors.put("highCardsBoostAmount", highCardsBoost);
        }

        // Referee strictness boost
        double strictnessBoost = calculateRefereeStrictnessBoost(context);
        factors.put("refereeStrictnessBoostApplied", strictnessBoost > 0);
        if (strictnessBoost > 0) {
            factors.put("refereeStrictnessBoostAmount", strictnessBoost);
        }

        // Red card risk
        if (context.hasRefereeStats()) {
            factors.put("redCardRisk", calculateRedCardRisk(context));
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
}
