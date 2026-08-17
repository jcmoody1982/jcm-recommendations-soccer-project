package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.core.recommendation.util.RecommendationFactory;
import com.jcm.recommendations.soccer.core.recommendation.util.VegasTipsterCopy;
import com.jcm.recommendations.soccer.domain.RefereeStats;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.*;

/**
 * UC-008: Booking Points recommendations (Yellow=10, Red=25).
 *
 * P0–P2 (no form-sample dampening):
 * - {@code cards_potential} is expected <em>card count</em> → convert ×10; omit if missing
 * - Require edge vs line before tipping; mid-range fixtures return empty
 * - Model units are expected booking points (card-count ×10 + red risk)
 * - Prefer lines with buffer via min edge; settlement voids exact line (push)
 * - Soften boosts + additive intensity (not ×1.2 multiplier)
 * - Without referee: never STRONG; apply referee reliability to ref signals
 */
@Component
@Slf4j
public class BookingPointsRecommendationEngine implements RecommendationEngine {

    private static final int YELLOW_CARD_POINTS = 10;
    private static final int RED_CARD_POINTS = 25;

    // Preferred weights (renormalized when signals missing)
    private static final double WEIGHT_HOME_CARDS_SEASON = 0.14;
    private static final double WEIGHT_AWAY_CARDS_SEASON = 0.14;
    private static final double WEIGHT_HOME_CARDS_FORM = 0.10;
    private static final double WEIGHT_AWAY_CARDS_FORM = 0.10;
    private static final double WEIGHT_REFEREE_CARDS = 0.20;
    private static final double WEIGHT_REFEREE_O35_CARDS = 0.08;
    private static final double WEIGHT_RED_CARD_RISK = 0.06;
    private static final double WEIGHT_API_POTENTIAL = 0.18;

    // Graded boosts (capped in combination)
    private static final double HIGH_CARDS_TEAM_THRESHOLD = 2.0;
    private static final double HIGH_CARDS_BOOST_MAX = 3.0;
    private static final double REFEREE_STRICT_O35_THRESHOLD = 60.0;
    private static final double REFEREE_STRICT_BOOST_MAX = 3.0;
    private static final double MAX_COMBINED_BOOST = 5.0;

    // Additive intensity (points), not a multiplier
    private static final double INTENSITY_CLOSE_POINTS = 4.0;   // position gap ≤ 3
    private static final double INTENSITY_COMPETITIVE_POINTS = 2.0; // gap 4–6

    // Selectivity vs over/under lines
    private static final double MIN_EDGE = 8.0;
    private static final double STRONG_EDGE = 12.0;
    private static final double LINE_OVER_50 = 50.0;
    private static final double LINE_OVER_40 = 40.0;
    private static final double LINE_UNDER_40 = 40.0;
    private static final double LINE_UNDER_30 = 30.0;

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

        ScoreBreakdown breakdown = calculateExpectedBookingPoints(context);
        Optional<MarketPick> marketPick = selectMarket(breakdown.expectedPoints());
        if (marketPick.isEmpty()) {
            log.debug("No booking-points market with sufficient edge: fixtureId={}, expected={}",
                    context.getFixture().getId(), String.format("%.1f", breakdown.expectedPoints()));
            return Optional.empty();
        }

        MarketPick pick = marketPick.get();
        boolean hasReferee = context.hasRefereeStats();
        ConfidenceLevel confidence = determineConfidence(pick.edge(), hasReferee);

        Map<String, Object> factors = buildFactors(context, breakdown, pick);

        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(RecommendationType.BOOKING_POINTS)
                .confidence(confidence)
                .score(breakdown.expectedPoints())
                .market(pick.market())
                .odds(null)
                .description(buildDescription(context, confidence, breakdown.expectedPoints(), pick))
                .factors(factors)
                .build();

        log.info("Booking Points recommendation generated: fixtureId={}, expectedPoints={}, edge={}, confidence={}, market={}",
                context.getFixture().getId(),
                String.format("%.1f", breakdown.expectedPoints()),
                String.format("%.1f", pick.edge()),
                confidence,
                pick.market());

        return Optional.of(recommendation);
    }

    @Override
    public boolean isApplicable(FixtureContext context) {
        return context.hasCompleteData()
                && context.getHomeTeamStats() != null
                && context.getAwayTeamStats() != null;
    }

    private ScoreBreakdown calculateExpectedBookingPoints(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        List<WeightedSignal> signals = new ArrayList<>();

        Double homeCardsSeason = homeStats.getCardsAvgHome();
        Double awayCardsSeason = awayStats.getCardsAvgAway();
        if (homeCardsSeason != null) {
            signals.add(new WeightedSignal("homeCardsSeason", homeCardsSeason * YELLOW_CARD_POINTS, WEIGHT_HOME_CARDS_SEASON));
        }
        if (awayCardsSeason != null) {
            signals.add(new WeightedSignal("awayCardsSeason", awayCardsSeason * YELLOW_CARD_POINTS, WEIGHT_AWAY_CARDS_SEASON));
        }

        if (context.hasRecentForm()) {
            Double homeForm = context.getHomeTeamForm().getCardsAvgHome();
            Double awayForm = context.getAwayTeamForm().getCardsAvgAway();
            if (homeForm != null) {
                signals.add(new WeightedSignal("homeCardsForm", homeForm * YELLOW_CARD_POINTS, WEIGHT_HOME_CARDS_FORM));
            }
            if (awayForm != null) {
                signals.add(new WeightedSignal("awayCardsForm", awayForm * YELLOW_CARD_POINTS, WEIGHT_AWAY_CARDS_FORM));
            }
        }

        double refereeReliability = 0.0;
        if (context.hasRefereeStats()) {
            RefereeStats refStats = context.getRefereeStats();
            refereeReliability = calculateRefereeReliability(refStats);

            if (refStats.getCardsPerMatchOverall() != null) {
                double refPoints = refStats.getCardsPerMatchOverall() * YELLOW_CARD_POINTS;
                signals.add(new WeightedSignal(
                        "refereeCards",
                        refPoints,
                        WEIGHT_REFEREE_CARDS * refereeReliability));
            }
            if (refStats.getOver35CardsPercentageOverall() != null) {
                // Map O3.5% to a soft expected-points prior (~30–50 pts)
                double o35Points = clampScore(refStats.getOver35CardsPercentageOverall() * 0.5, 0, 50);
                signals.add(new WeightedSignal(
                        "refereeO35",
                        o35Points,
                        WEIGHT_REFEREE_O35_CARDS * refereeReliability));
            }

            double redRisk = calculateRedCardRisk(context);
            if (redRisk > 0) {
                signals.add(new WeightedSignal("redCardRisk", redRisk, WEIGHT_RED_CARD_RISK * refereeReliability));
            }
        }

        Double apiCardsCount = null;
        if (context.hasPotentials() && context.getPotentials().getCardsPotential() != null) {
            apiCardsCount = context.getPotentials().getCardsPotential();
            // P0: cards_potential is expected card COUNT, not a 0–100 score
            signals.add(new WeightedSignal(
                    "apiCardsPotential",
                    apiCardsCount * YELLOW_CARD_POINTS,
                    WEIGHT_API_POTENTIAL));
        }

        double basePoints = renormalizedAverage(signals);
        double intensityPoints = calculateMatchIntensityPoints(context);
        double highCardsBoost = calculateHighCardsBoost(homeStats, awayStats);
        double strictnessBoost = calculateRefereeStrictnessBoost(context);
        double rawBoost = highCardsBoost + strictnessBoost;
        double appliedBoost = Math.min(MAX_COMBINED_BOOST, rawBoost);

        double expected = basePoints + intensityPoints + appliedBoost;

        return new ScoreBreakdown(
                expected,
                basePoints,
                intensityPoints,
                highCardsBoost,
                strictnessBoost,
                appliedBoost,
                rawBoost > MAX_COMBINED_BOOST,
                refereeReliability,
                apiCardsCount,
                signals.size());
    }

    private double renormalizedAverage(List<WeightedSignal> signals) {
        if (signals.isEmpty()) {
            return 0.0;
        }
        double weightSum = signals.stream().mapToDouble(WeightedSignal::weight).sum();
        if (weightSum <= 0) {
            return 0.0;
        }
        double total = 0.0;
        for (WeightedSignal signal : signals) {
            total += signal.value() * (signal.weight() / weightSum);
        }
        return total;
    }

    private Optional<MarketPick> selectMarket(double expectedPoints) {
        List<MarketPick> candidates = new ArrayList<>();

        if (expectedPoints >= LINE_OVER_50 + MIN_EDGE) {
            candidates.add(new MarketPick(
                    "Over 50 Booking Points", LINE_OVER_50, true, expectedPoints - LINE_OVER_50));
        }
        if (expectedPoints >= LINE_OVER_40 + MIN_EDGE) {
            candidates.add(new MarketPick(
                    "Over 40 Booking Points", LINE_OVER_40, true, expectedPoints - LINE_OVER_40));
        }
        if (expectedPoints <= LINE_UNDER_30 - MIN_EDGE) {
            candidates.add(new MarketPick(
                    "Under 30 Booking Points", LINE_UNDER_30, false, LINE_UNDER_30 - expectedPoints));
        }
        if (expectedPoints <= LINE_UNDER_40 - MIN_EDGE) {
            candidates.add(new MarketPick(
                    "Under 40 Booking Points", LINE_UNDER_40, false, LINE_UNDER_40 - expectedPoints));
        }

        // Prefer stronger lines (Over 50 > Over 40, Under 30 > Under 40), then larger edge
        return candidates.stream().max(Comparator
                .comparingDouble((MarketPick p) -> p.over() ? p.line() : -p.line())
                .thenComparingDouble(MarketPick::edge));
    }

    private ConfidenceLevel determineConfidence(double edge, boolean hasReferee) {
        if (edge >= STRONG_EDGE && hasReferee) {
            return ConfidenceLevel.STRONG;
        }
        if (edge >= MIN_EDGE) {
            return ConfidenceLevel.MODERATE;
        }
        return ConfidenceLevel.WEAK;
    }

    private double calculateHighCardsBoost(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        double homeCardsAvg = safeDouble(homeStats.getCardsAvgHome());
        double awayCardsAvg = safeDouble(awayStats.getCardsAvgAway());
        double homeStrength = gradedStrength(homeCardsAvg, HIGH_CARDS_TEAM_THRESHOLD);
        double awayStrength = gradedStrength(awayCardsAvg, HIGH_CARDS_TEAM_THRESHOLD);
        return HIGH_CARDS_BOOST_MAX * Math.sqrt(homeStrength * awayStrength);
    }

    private double calculateRefereeStrictnessBoost(FixtureContext context) {
        if (!context.hasRefereeStats()) {
            return 0.0;
        }
        Double o35Pct = context.getRefereeStats().getOver35CardsPercentageOverall();
        if (o35Pct == null) {
            return 0.0;
        }
        double strength = gradedStrength(o35Pct, REFEREE_STRICT_O35_THRESHOLD);
        return REFEREE_STRICT_BOOST_MAX * strength;
    }

    /** 0 below threshold−0.25 (or −5 for %), 1 at threshold+0.5 (or +10 for %). */
    private double gradedStrength(double value, double threshold) {
        double ramp = threshold >= 20 ? 10.0 : 0.5; // percentage vs cards-avg scale
        double startPad = threshold >= 20 ? 5.0 : 0.25;
        double start = threshold - startPad;
        double end = threshold + ramp;
        if (value <= start) {
            return 0.0;
        }
        if (value >= end) {
            return 1.0;
        }
        return (value - start) / (end - start);
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
        if (!context.hasRefereeStats()) {
            return 0.0;
        }
        RefereeStats refStats = context.getRefereeStats();
        if (refStats.getRedCardsOverall() == null || refStats.getAppearancesOverall() == null
                || refStats.getAppearancesOverall() <= 0) {
            return 0.0;
        }
        double redCardRate = refStats.getRedCardsOverall() / (double) refStats.getAppearancesOverall();
        return redCardRate * RED_CARD_POINTS;
    }

    private double calculateMatchIntensityPoints(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();
        if (homeStats.getPosition() == null || awayStats.getPosition() == null) {
            return 0.0;
        }
        int positionDiff = Math.abs(homeStats.getPosition() - awayStats.getPosition());
        if (positionDiff <= 3) {
            return INTENSITY_CLOSE_POINTS;
        }
        if (positionDiff <= 6) {
            return INTENSITY_COMPETITIVE_POINTS;
        }
        return 0.0;
    }

    private Map<String, Object> buildFactors(FixtureContext context, ScoreBreakdown breakdown, MarketPick pick) {
        Map<String, Object> factors = new HashMap<>();

        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        factors.put("expectedBookingPoints", breakdown.expectedPoints());
        factors.put("basePoints", breakdown.basePoints());
        factors.put("marketLine", pick.line());
        factors.put("marketEdge", pick.edge());
        factors.put("minEdgeRequired", MIN_EDGE);
        factors.put("cardsPotentialIsCardCount", true);
        factors.put("missingDataRenormalized", true);

        if (homeStats.getCardsAvgHome() != null) {
            factors.put("homeCardsSeasonAvg", homeStats.getCardsAvgHome());
        }
        if (awayStats.getCardsAvgAway() != null) {
            factors.put("awayCardsSeasonAvg", awayStats.getCardsAvgAway());
        }

        factors.put("formDataAvailable", context.hasRecentForm());
        if (context.hasRecentForm()) {
            if (context.getHomeTeamForm().getCardsAvgHome() != null) {
                factors.put("homeCardsFormAvg", context.getHomeTeamForm().getCardsAvgHome());
            }
            if (context.getAwayTeamForm().getCardsAvgAway() != null) {
                factors.put("awayCardsFormAvg", context.getAwayTeamForm().getCardsAvgAway());
            }
        }

        factors.put("refereeDataAvailable", context.hasRefereeStats());
        factors.put("refereeRequiredForStrong", true);
        if (context.hasRefereeStats()) {
            RefereeStats refStats = context.getRefereeStats();
            factors.put("refereeCardsAvg", safeDouble(refStats.getCardsPerMatchOverall()));
            factors.put("refereeAppearances", refStats.getAppearancesOverall());
            factors.put("refereeReliability", breakdown.refereeReliability());
            factors.put("refereeYellowCards", refStats.getYellowCardsOverall());
            factors.put("refereeRedCards", refStats.getRedCardsOverall());
            if (refStats.getOver35CardsPercentageOverall() != null) {
                factors.put("refereeOver35CardsPct", refStats.getOver35CardsPercentageOverall());
            }
            factors.put("redCardRisk", calculateRedCardRisk(context));
        }

        if (breakdown.apiCardsCount() != null) {
            factors.put("apiCardsPotential", breakdown.apiCardsCount());
            factors.put("apiCardsPotentialAsPoints", breakdown.apiCardsCount() * YELLOW_CARD_POINTS);
        }

        factors.put("matchIntensityPoints", breakdown.intensityPoints());
        if (homeStats.getPosition() != null && awayStats.getPosition() != null) {
            factors.put("homePosition", homeStats.getPosition());
            factors.put("awayPosition", awayStats.getPosition());
            factors.put("positionDifference", Math.abs(homeStats.getPosition() - awayStats.getPosition()));
        }

        factors.put("highCardsBoostApplied", breakdown.highCardsBoost() > 0.01);
        if (breakdown.highCardsBoost() > 0.01) {
            factors.put("highCardsBoostAmount", breakdown.highCardsBoost());
        }
        factors.put("refereeStrictnessBoostApplied", breakdown.strictnessBoost() > 0.01);
        if (breakdown.strictnessBoost() > 0.01) {
            factors.put("refereeStrictnessBoostAmount", breakdown.strictnessBoost());
        }
        factors.put("appliedBoost", breakdown.appliedBoost());
        factors.put("boostCapped", breakdown.boostCapped());
        factors.put("maxCombinedBoost", MAX_COMBINED_BOOST);
        factors.put("signalsUsed", breakdown.signalsUsed());

        return factors;
    }

    private String buildDescription(FixtureContext context, ConfidenceLevel confidence,
            double expectedPoints, MarketPick pick) {
        return VegasTipsterCopy.narrate(VegasTipsterCopy.Brief.builder()
                .confidence(confidence)
                .selection(pick.market())
                .context(context)
                .expected(expectedPoints, "expected booking points")
                .edge(pick.edge())
                .colourNote("Cards are cooking — the whistle's got work tonight")
                .build());
    }

    private record WeightedSignal(String name, double value, double weight) {}

    private record MarketPick(String market, double line, boolean over, double edge) {}

    private record ScoreBreakdown(
            double expectedPoints,
            double basePoints,
            double intensityPoints,
            double highCardsBoost,
            double strictnessBoost,
            double appliedBoost,
            boolean boostCapped,
            double refereeReliability,
            Double apiCardsCount,
            int signalsUsed) {}
}
