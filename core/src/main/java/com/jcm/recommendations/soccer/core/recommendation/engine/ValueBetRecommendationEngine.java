package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class ValueBetRecommendationEngine implements RecommendationEngine {

    private static final double THRESHOLD_STRONG_VALUE = 15.0;
    private static final double THRESHOLD_MODERATE_VALUE = 10.0;
    private static final double THRESHOLD_STRONG_EV = 0.10;
    private static final double THRESHOLD_MODERATE_EV = 0.05;
    private static final double MIN_ODDS = 1.50;

    private final BttsRecommendationEngine bttsEngine;
    private final OverGoalsRecommendationEngine overGoalsEngine;
    private final UnderGoalsRecommendationEngine underGoalsEngine;

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
                .max(Comparator.comparingDouble(ValueOpportunity::expectedValue))
                .orElse(null);

        if (bestOpportunity == null || bestOpportunity.confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        Map<String, Object> factors = buildFactors(bestOpportunity, opportunities);

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
                .type(RecommendationType.VALUE_BET)
                .confidence(bestOpportunity.confidence)
                .score(bestOpportunity.valuePercentage)
                .market(bestOpportunity.market)
                .description(buildDescription(context, bestOpportunity))
                .factors(factors)
                .generatedAt(Instant.now())
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

        checkBttsValue(context).ifPresent(opportunities::add);
        checkOverGoalsValue(context).ifPresent(opportunities::add);
        checkUnderGoalsValue(context).ifPresent(opportunities::add);
        checkMatchResultValue(context, opportunities);

        return opportunities;
    }

    private Optional<ValueOpportunity> checkBttsValue(FixtureContext context) {
        Optional<Recommendation> bttsRec = bttsEngine.analyze(context);
        if (bttsRec.isEmpty()) {
            return Optional.empty();
        }

        Double odds = context.getOdds().getOddsBttsYes();
        if (odds == null || odds < MIN_ODDS) {
            return Optional.empty();
        }

        double ourProbability = bttsRec.get().getScore() / 100.0;
        double impliedProbability = 1.0 / odds;
        double valuePercentage = (ourProbability - impliedProbability) * 100;
        double expectedValue = (ourProbability * (odds - 1)) - (1 - ourProbability);

        if (valuePercentage >= THRESHOLD_MODERATE_VALUE && expectedValue >= THRESHOLD_MODERATE_EV) {
            return Optional.of(new ValueOpportunity(
                    "BTTS Yes",
                    ourProbability * 100,
                    impliedProbability * 100,
                    odds,
                    valuePercentage,
                    expectedValue,
                    determineConfidence(valuePercentage, expectedValue),
                    bttsRec.get().getConfidence()
            ));
        }

        return Optional.empty();
    }

    private Optional<ValueOpportunity> checkOverGoalsValue(FixtureContext context) {
        Optional<Recommendation> overRec = overGoalsEngine.analyze(context);
        if (overRec.isEmpty()) {
            return Optional.empty();
        }

        Double odds = context.getOdds().getOddsFtOver25();
        if (odds == null || odds < MIN_ODDS) {
            return Optional.empty();
        }

        double ourProbability = overRec.get().getScore() / 100.0;
        double impliedProbability = 1.0 / odds;
        double valuePercentage = (ourProbability - impliedProbability) * 100;
        double expectedValue = (ourProbability * (odds - 1)) - (1 - ourProbability);

        if (valuePercentage >= THRESHOLD_MODERATE_VALUE && expectedValue >= THRESHOLD_MODERATE_EV) {
            return Optional.of(new ValueOpportunity(
                    "Over 2.5 Goals",
                    ourProbability * 100,
                    impliedProbability * 100,
                    odds,
                    valuePercentage,
                    expectedValue,
                    determineConfidence(valuePercentage, expectedValue),
                    overRec.get().getConfidence()
            ));
        }

        return Optional.empty();
    }

    private Optional<ValueOpportunity> checkUnderGoalsValue(FixtureContext context) {
        Optional<Recommendation> underRec = underGoalsEngine.analyze(context);
        if (underRec.isEmpty()) {
            return Optional.empty();
        }

        Double odds = context.getOdds().getOddsFtUnder25();
        if (odds == null || odds < MIN_ODDS) {
            return Optional.empty();
        }

        double ourProbability = underRec.get().getScore() / 100.0;
        double impliedProbability = 1.0 / odds;
        double valuePercentage = (ourProbability - impliedProbability) * 100;
        double expectedValue = (ourProbability * (odds - 1)) - (1 - ourProbability);

        if (valuePercentage >= THRESHOLD_MODERATE_VALUE && expectedValue >= THRESHOLD_MODERATE_EV) {
            return Optional.of(new ValueOpportunity(
                    "Under 2.5 Goals",
                    ourProbability * 100,
                    impliedProbability * 100,
                    odds,
                    valuePercentage,
                    expectedValue,
                    determineConfidence(valuePercentage, expectedValue),
                    underRec.get().getConfidence()
            ));
        }

        return Optional.empty();
    }

    private void checkMatchResultValue(FixtureContext context, List<ValueOpportunity> opportunities) {
        checkSingleMatchResult(context, "Home Win", context.getOdds().getOddsFt1(), 
                calculateHomeWinProbability(context)).ifPresent(opportunities::add);
        checkSingleMatchResult(context, "Draw", context.getOdds().getOddsFtX(), 
                calculateDrawProbability(context)).ifPresent(opportunities::add);
        checkSingleMatchResult(context, "Away Win", context.getOdds().getOddsFt2(), 
                calculateAwayWinProbability(context)).ifPresent(opportunities::add);
    }

    private Optional<ValueOpportunity> checkSingleMatchResult(FixtureContext context, String market, 
            Double odds, double ourProbability) {
        if (odds == null || odds < MIN_ODDS) {
            return Optional.empty();
        }

        double impliedProbability = 1.0 / odds;
        double valuePercentage = (ourProbability - impliedProbability) * 100;
        double expectedValue = (ourProbability * (odds - 1)) - (1 - ourProbability);

        if (valuePercentage >= THRESHOLD_MODERATE_VALUE && expectedValue >= THRESHOLD_MODERATE_EV) {
            return Optional.of(new ValueOpportunity(
                    market,
                    ourProbability * 100,
                    impliedProbability * 100,
                    odds,
                    valuePercentage,
                    expectedValue,
                    determineConfidence(valuePercentage, expectedValue),
                    ConfidenceLevel.MODERATE
            ));
        }

        return Optional.empty();
    }

    private double calculateHomeWinProbability(FixtureContext context) {
        if (context.getHomeTeamStats() == null || context.getHomeTeamStats().getMatchesPlayed() == null) {
            return 0.33;
        }
        int homeWins = context.getHomeTeamStats().getSeasonWinsHome() != null 
                ? context.getHomeTeamStats().getSeasonWinsHome() : 0;
        return homeWins / (double) context.getHomeTeamStats().getMatchesPlayed();
    }

    private double calculateAwayWinProbability(FixtureContext context) {
        if (context.getAwayTeamStats() == null || context.getAwayTeamStats().getMatchesPlayed() == null) {
            return 0.33;
        }
        int awayWins = context.getAwayTeamStats().getSeasonWinsAway() != null 
                ? context.getAwayTeamStats().getSeasonWinsAway() : 0;
        return awayWins / (double) context.getAwayTeamStats().getMatchesPlayed();
    }

    private double calculateDrawProbability(FixtureContext context) {
        double homeWin = calculateHomeWinProbability(context);
        double awayWin = calculateAwayWinProbability(context);
        return Math.max(0.1, 1.0 - homeWin - awayWin);
    }

    private ConfidenceLevel determineConfidence(double valuePercentage, double expectedValue) {
        if (valuePercentage >= THRESHOLD_STRONG_VALUE && expectedValue >= THRESHOLD_STRONG_EV) {
            return ConfidenceLevel.STRONG;
        } else if (valuePercentage >= THRESHOLD_MODERATE_VALUE && expectedValue >= THRESHOLD_MODERATE_EV) {
            return ConfidenceLevel.MODERATE;
        }
        return ConfidenceLevel.WEAK;
    }

    private Map<String, Object> buildFactors(ValueOpportunity best, List<ValueOpportunity> all) {
        Map<String, Object> factors = new HashMap<>();
        
        factors.put("market", best.market);
        factors.put("ourProbability", best.ourProbability);
        factors.put("impliedProbability", best.impliedProbability);
        factors.put("odds", best.odds);
        factors.put("valuePercentage", best.valuePercentage);
        factors.put("expectedValue", best.expectedValue);
        factors.put("sourceConfidence", best.sourceConfidence.getDisplayName());
        factors.put("totalOpportunities", all.size());

        return factors;
    }

    private String buildDescription(FixtureContext context, ValueOpportunity opportunity) {
        return String.format("%s confidence Value Bet on %s @ %.2f (%.1f%% value, EV: %.3f) - %s vs %s",
                opportunity.confidence.getDisplayName(),
                opportunity.market,
                opportunity.odds,
                opportunity.valuePercentage,
                opportunity.expectedValue,
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());
    }

    private record ValueOpportunity(
            String market,
            double ourProbability,
            double impliedProbability,
            double odds,
            double valuePercentage,
            double expectedValue,
            ConfidenceLevel confidence,
            ConfidenceLevel sourceConfidence
    ) {}
}
