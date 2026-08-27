package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.core.recommendation.util.RecommendationFactory;
import com.jcm.recommendations.soccer.core.recommendation.util.VegasTipsterCopy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class ValueBetRecommendationEngine implements RecommendationEngine {

    private static final double THRESHOLD_STRONG_VALUE = 20.0;
    private static final double THRESHOLD_MODERATE_VALUE = 15.0;
    private static final double THRESHOLD_STRONG_EV = 0.12;
    private static final double THRESHOLD_MODERATE_EV = 0.08;
    private static final double MIN_ODDS = 1.50;
    private static final double MAX_ODDS = 2.50;

    // Kelly Criterion fraction (conservative)
    private static final double KELLY_FRACTION = 0.25;  // Quarter Kelly for safety

    // Source confidence weights
    private static final double SOURCE_STRONG_WEIGHT = 1.0;
    private static final double SOURCE_MODERATE_WEIGHT = 0.8;
    private static final double SOURCE_WEAK_WEIGHT = 0.5;

    private final BttsRecommendationEngine bttsEngine;
    private final OverGoalsRecommendationEngine overGoalsEngine;
    private final UnderGoalsRecommendationEngine underGoalsEngine;
    private final BookingPointsRecommendationEngine bookingPointsEngine;

    @Override
    public RecommendationType getType() {
        return RecommendationType.VALUE_BET;
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        log.debug("Analyzing Value Bets for fixture: fixtureId={}, {} vs {}", 
                context.getFixture().getId(),
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());

        List<ValueOpportunity> opportunities = findValueOpportunities(context);
        
        if (opportunities.isEmpty()) {
            return Optional.empty();
        }

        ValueOpportunity bestOpportunity = opportunities.stream()
                .max(Comparator.comparingDouble(ValueOpportunity::weightedExpectedValue))
                .orElse(null);

        if (bestOpportunity == null || bestOpportunity.confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        Map<String, Object> factors = buildFactors(bestOpportunity, opportunities);

        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(RecommendationType.VALUE_BET)
                .confidence(bestOpportunity.confidence)
                .score(bestOpportunity.valuePercentage)
                .market(bestOpportunity.market)
                .odds(bestOpportunity.odds)
                .description(buildDescription(context, bestOpportunity))
                .factors(factors)
                .build();

        log.info("Value Bet recommendation generated: fixtureId={}, market={}, value={}%, EV={}, confidence={}", 
                context.getFixture().getId(), bestOpportunity.market, 
                String.format("%.1f", bestOpportunity.valuePercentage),
                String.format("%.3f", bestOpportunity.expectedValue),
                bestOpportunity.confidence);

        return Optional.of(recommendation);
    }

    @Override
    public boolean isApplicable(FixtureContext context) {
        return context.hasCompleteData() && context.hasOdds();
    }

    private List<ValueOpportunity> findValueOpportunities(FixtureContext context) {
        List<ValueOpportunity> opportunities = new ArrayList<>();

        // BTTS markets
        checkBttsYesValue(context).ifPresent(opportunities::add);
        checkBttsNoValue(context).ifPresent(opportunities::add);

        // Goals markets - multiple thresholds
        checkOverGoalsValue(context, "Over 1.5 Goals", context.getOdds().getOddsFtOver15()).ifPresent(opportunities::add);
        checkOverGoalsValue(context, "Over 2.5 Goals", context.getOdds().getOddsFtOver25()).ifPresent(opportunities::add);
        checkOverGoalsValue(context, "Over 3.5 Goals", context.getOdds().getOddsFtOver35()).ifPresent(opportunities::add);
        
        checkUnderGoalsValue(context, "Under 1.5 Goals", context.getOdds().getOddsFtUnder15()).ifPresent(opportunities::add);
        checkUnderGoalsValue(context, "Under 2.5 Goals", context.getOdds().getOddsFtUnder25()).ifPresent(opportunities::add);
        checkUnderGoalsValue(context, "Under 3.5 Goals", context.getOdds().getOddsFtUnder35()).ifPresent(opportunities::add);

        // Match result markets
        checkMatchResultValue(context, opportunities);

        // Booking points market (if applicable)
        checkBookingPointsValue(context).ifPresent(opportunities::add);

        return opportunities;
    }

    private Optional<ValueOpportunity> checkBttsYesValue(FixtureContext context) {
        Optional<Recommendation> bttsRec = bttsEngine.analyze(context);
        if (bttsRec.isEmpty()) {
            return Optional.empty();
        }

        Double odds = context.getOdds().getOddsBttsYes();
        if (!isValidOdds(odds)) {
            return Optional.empty();
        }

        double ourProbability = bttsRec.get().getScore() / 100.0;
        return createValueOpportunity("BTTS Yes", ourProbability, odds, bttsRec.get().getConfidence());
    }

    private Optional<ValueOpportunity> checkBttsNoValue(FixtureContext context) {
        Optional<Recommendation> bttsRec = bttsEngine.analyze(context);
        if (bttsRec.isEmpty()) {
            return Optional.empty();
        }

        Double odds = context.getOdds().getOddsBttsNo();
        if (!isValidOdds(odds)) {
            return Optional.empty();
        }

        // BTTS No probability is inverse of BTTS Yes
        double ourProbability = 1.0 - (bttsRec.get().getScore() / 100.0);
        return createValueOpportunity("BTTS No", ourProbability, odds, bttsRec.get().getConfidence());
    }

    private Optional<ValueOpportunity> checkOverGoalsValue(FixtureContext context, String market, Double odds) {
        Optional<Recommendation> overRec = overGoalsEngine.analyze(context);
        if (overRec.isEmpty()) {
            return Optional.empty();
        }

        if (!isValidOdds(odds)) {
            return Optional.empty();
        }

        // Adjust probability based on market threshold
        double baseProbability = overRec.get().getScore() / 100.0;
        double adjustedProbability = adjustProbabilityForMarket(baseProbability, market, true);
        
        return createValueOpportunity(market, adjustedProbability, odds, overRec.get().getConfidence());
    }

    private Optional<ValueOpportunity> checkUnderGoalsValue(FixtureContext context, String market, Double odds) {
        Optional<Recommendation> underRec = underGoalsEngine.analyze(context);
        if (underRec.isEmpty()) {
            return Optional.empty();
        }

        if (!isValidOdds(odds)) {
            return Optional.empty();
        }

        // Adjust probability based on market threshold
        double baseProbability = underRec.get().getScore() / 100.0;
        double adjustedProbability = adjustProbabilityForMarket(baseProbability, market, false);
        
        return createValueOpportunity(market, adjustedProbability, odds, underRec.get().getConfidence());
    }

    private Optional<ValueOpportunity> checkBookingPointsValue(FixtureContext context) {
        Optional<Recommendation> bookingRec = bookingPointsEngine.analyze(context);
        if (bookingRec.isEmpty()) {
            return Optional.empty();
        }

        // Booking points score is actual expected points, convert to probability
        double expectedPoints = bookingRec.get().getScore();
        String market = bookingRec.get().getMarket();
        
        // We don't have specific booking points odds in FixtureOdds, 
        // so skip this market for now - would need odds API integration
        return Optional.empty();
    }

    private double adjustProbabilityForMarket(double baseProbability, String market, boolean isOver) {
        // Adjust probability based on how far the market is from 2.5
        // This is a rough approximation - in reality we'd use the expected goals
        if (market.contains("1.5")) {
            return isOver ? Math.min(0.95, baseProbability * 1.15) : Math.max(0.05, baseProbability * 0.85);
        } else if (market.contains("3.5")) {
            return isOver ? Math.max(0.05, baseProbability * 0.75) : Math.min(0.95, baseProbability * 1.25);
        }
        return baseProbability; // 2.5 market uses base probability
    }

    private boolean isValidOdds(Double odds) {
        return odds != null && odds >= MIN_ODDS && odds <= MAX_ODDS;
    }

    private Optional<ValueOpportunity> createValueOpportunity(String market, double ourProbability,
            Double odds, ConfidenceLevel sourceConfidence) {
        if (!isValidOdds(odds)) {
            return Optional.empty();
        }

        double impliedProbability = 1.0 / odds;
        double valuePercentage = (ourProbability - impliedProbability) * 100;
        double expectedValue = (ourProbability * (odds - 1)) - (1 - ourProbability);

        // Apply source confidence weight to EV
        double sourceWeight = getSourceWeight(sourceConfidence);
        double weightedEv = expectedValue * sourceWeight;

        // Calculate Kelly stake
        double kellyStake = calculateKellyStake(ourProbability, odds);

        if (valuePercentage >= THRESHOLD_MODERATE_VALUE && expectedValue >= THRESHOLD_MODERATE_EV) {
            return Optional.of(new ValueOpportunity(
                    market,
                    ourProbability * 100,
                    impliedProbability * 100,
                    odds,
                    valuePercentage,
                    expectedValue,
                    weightedEv,
                    kellyStake,
                    determineConfidence(valuePercentage, expectedValue, odds, sourceConfidence),
                    sourceConfidence
            ));
        }

        return Optional.empty();
    }

    private double getSourceWeight(ConfidenceLevel confidence) {
        return switch (confidence) {
            case STRONG -> SOURCE_STRONG_WEIGHT;
            case MODERATE -> SOURCE_MODERATE_WEIGHT;
            case WEAK -> SOURCE_WEAK_WEIGHT;
        };
    }

    private double calculateKellyStake(double probability, double odds) {
        // Kelly Criterion: f* = (bp - q) / b
        // where b = odds - 1, p = probability of winning, q = probability of losing
        double b = odds - 1;
        double p = probability;
        double q = 1 - probability;
        
        double kellyFull = (b * p - q) / b;
        
        // Apply fractional Kelly and cap at reasonable maximum
        double fractionalKelly = kellyFull * KELLY_FRACTION;
        return Math.max(0, Math.min(0.10, fractionalKelly)); // Cap at 10% of bankroll
    }

    private void checkMatchResultValue(FixtureContext context, List<ValueOpportunity> opportunities) {
        double homeWinProb = calculateHomeWinProbability(context);

        if (context.hasRecentForm()) {
            double homeFormPpg = safeDouble(context.getHomeTeamForm().getPpgHome(), 1.0);
            double homeFormWinRate = homeFormPpg / 3.0;
            homeWinProb = (homeWinProb * 0.6) + (homeFormWinRate * 0.4);
        }

        if (context.getHomeTeamStats() != null && context.getHomeTeamStats().getXgForAvgHome() != null
                && context.getAwayTeamStats() != null && context.getAwayTeamStats().getXgAgainstAvgHome() != null) {
            double homeXg = safeDouble(context.getHomeTeamStats().getXgForAvgHome());
            double awayXga = safeDouble(context.getAwayTeamStats().getXgAgainstAvgHome());
            double xgSignal = homeXg / (homeXg + awayXga + 0.1);
            homeWinProb = (homeWinProb * 0.7) + (xgSignal * 0.3);
        }

        homeWinProb = Math.max(0.05, Math.min(0.90, homeWinProb));

        // Home Win only — Away/Draw value paused after poor snapshot hit rates (aligned with Match Result).
        createValueOpportunity("Home Win", homeWinProb, context.getOdds().getOddsFt1(),
                ConfidenceLevel.MODERATE).ifPresent(opportunities::add);
    }

    private double calculateHomeWinProbability(FixtureContext context) {
        if (context.getHomeTeamStats() == null) {
            return 0.33;
        }
        int homeMatches = calculateMatchesAtVenue(context.getHomeTeamStats(), true);
        if (homeMatches == 0) {
            return 0.33;
        }
        int homeWins = context.getHomeTeamStats().getSeasonWinsHome() != null 
                ? context.getHomeTeamStats().getSeasonWinsHome() : 0;
        return homeWins / (double) homeMatches;
    }

    ConfidenceLevel determineConfidence(double valuePercentage, double expectedValue, double odds,
            ConfidenceLevel sourceConfidence) {
        if (valuePercentage >= THRESHOLD_STRONG_VALUE
                && expectedValue >= THRESHOLD_STRONG_EV
                && odds <= MAX_ODDS
                && sourceConfidence == ConfidenceLevel.STRONG) {
            return ConfidenceLevel.STRONG;
        } else if (valuePercentage >= THRESHOLD_MODERATE_VALUE && expectedValue >= THRESHOLD_MODERATE_EV) {
            return ConfidenceLevel.MODERATE;
        }
        return ConfidenceLevel.WEAK;
    }

    private Map<String, Object> buildFactors(ValueOpportunity best, List<ValueOpportunity> all) {
        Map<String, Object> factors = new HashMap<>();
        
        // Best opportunity details
        factors.put("market", best.market);
        factors.put("ourProbability", best.ourProbability);
        factors.put("impliedProbability", best.impliedProbability);
        factors.put("odds", best.odds);
        factors.put("valuePercentage", best.valuePercentage);
        factors.put("expectedValue", best.expectedValue);
        factors.put("weightedExpectedValue", best.weightedExpectedValue);
        factors.put("kellyStake", best.kellyStake);
        factors.put("suggestedStakePct", best.kellyStake * 100);
        factors.put("sourceConfidence", best.sourceConfidence.getDisplayName());
        factors.put("valueConfidence", best.confidence.getDisplayName());
        
        // All opportunities summary
        factors.put("totalOpportunities", all.size());
        
        // List all value opportunities found
        List<Map<String, Object>> opportunityList = new ArrayList<>();
        for (ValueOpportunity opp : all) {
            Map<String, Object> oppMap = new HashMap<>();
            oppMap.put("market", opp.market);
            oppMap.put("odds", opp.odds);
            oppMap.put("valuePercentage", opp.valuePercentage);
            oppMap.put("expectedValue", opp.expectedValue);
            oppMap.put("confidence", opp.confidence.getDisplayName());
            opportunityList.add(oppMap);
        }
        factors.put("allOpportunities", opportunityList);

        // Breakdown by market type
        long bttsCount = all.stream().filter(o -> o.market.startsWith("BTTS")).count();
        long goalsCount = all.stream().filter(o -> o.market.contains("Goals")).count();
        long matchResultCount = all.stream().filter(o -> o.market.equals("Home Win")).count();
        
        factors.put("bttsOpportunities", bttsCount);
        factors.put("goalsOpportunities", goalsCount);
        factors.put("matchResultOpportunities", matchResultCount);
        factors.put("awayWinValuePaused", true);
        factors.put("drawValuePaused", true);

        return factors;
    }

    private String buildDescription(FixtureContext context, ValueOpportunity opportunity) {
        return VegasTipsterCopy.narrate(VegasTipsterCopy.Brief.builder()
                .confidence(opportunity.confidence)
                .selection("Value Bet on " + opportunity.market)
                .context(context)
                .odds(opportunity.odds)
                .valuePct(opportunity.valuePercentage)
                .expectedValue(opportunity.expectedValue)
                .colourNote("The board's mispriced this one — grab the juice while it lasts")
                .build());
    }

    private record ValueOpportunity(
            String market,
            double ourProbability,
            double impliedProbability,
            double odds,
            double valuePercentage,
            double expectedValue,
            double weightedExpectedValue,
            double kellyStake,
            ConfidenceLevel confidence,
            ConfidenceLevel sourceConfidence
    ) {}
}
