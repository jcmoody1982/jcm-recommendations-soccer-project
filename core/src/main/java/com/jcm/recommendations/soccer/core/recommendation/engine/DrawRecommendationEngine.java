package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.core.recommendation.util.RecommendationFactory;
import com.jcm.recommendations.soccer.core.recommendation.util.MatchBriefCopy;
import com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils;
import com.jcm.recommendations.soccer.domain.RefereeStats;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.*;

/**
 * UC-019: Draw Recommendations
 *
 * <p>Publishes a draw only when three things line up: the goal expectations say the match is
 * genuinely level, the market agrees a draw is live, and the price on offer is worth taking.
 *
 * <p>The previous version scored a weighted index of draw proxies - season draw percentages, recent
 * draw counts, "draw specialist" labels - and then multiplied it by up to four compounding
 * multipliers. That was measurably anti-predictive: over the last seven days the highest-scoring
 * band claimed 64.7% and returned 12.1%, and every band got worse as the claimed score rose. The
 * cause was that the loudest inputs were the noisiest. Draw frequency barely persists between
 * fixtures, so a team with three draws in five games carries no edge into the next match, yet a
 * recent-draw streak plus a specialist label could nearly double the score. Raising the old
 * threshold would therefore have selected harder for noise, not for quality.
 *
 * <p>So the index is gone. Draw probability now comes from the scoreline distribution: expected
 * goals for each side, then the probability that both land on the same number. Closeness and
 * low-scoring - the two proxies that actually carried signal - are implied by those expectations
 * rather than scored separately. Draw-heavy form and referee tendencies survive only as reported
 * colour, because they read as noise once the expectations are in place.
 *
 * <p>Because a draw pays around 3.0-4.0, hit rate alone cannot tell us whether the board is
 * profitable, so publishing is gated on the price rather than on the score.
 */
@Component
@Slf4j
public class DrawRecommendationEngine implements RecommendationEngine {

    /** Share of the published probability taken from our own model, the rest from the market. */
    private static final double WEIGHT_MODEL = 0.45;
    private static final double WEIGHT_MARKET = 0.55;

    /** How much of the goal expectation comes from xG rather than the scored/conceded record. */
    private static final double WEIGHT_XG = 0.40;

    /** Publishing gates. All must pass; see {@link #evaluateGates}. */
    private static final double MIN_MODEL_DRAW_PCT = 27.0;
    private static final double MIN_MARKET_DRAW_PCT = 26.0;
    private static final double MAX_PPG_GAP = 0.45;
    private static final double MAX_COMBINED_EXPECTED_GOALS = 2.80;
    private static final double MIN_EDGE_AT_PRICE = 0.03;
    private static final double MAX_EDGE_AT_PRICE = 0.25;

    /** Below this a venue record is too thin to build an expectation from. */
    private static final int MIN_VENUE_MATCHES = 4;

    /** Floor on the published probability. Draw stays capped at MODERATE - there is no STRONG tier. */
    private static final double THRESHOLD_MODERATE = 26.0;

    // Descriptive thresholds — reported, not scored
    private static final double DRAW_SPECIALIST_HIGH = 35.0;
    private static final double XG_VERY_SIMILAR = 0.2;
    private static final double REFEREE_DRAW_FRIENDLY_PCT = 30.0;

    @Override
    public RecommendationType getType() {
        return RecommendationType.DRAW;
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        log.debug("Analyzing Draw for fixture: fixtureId={}, {} vs {}",
                context.getFixture().getId(),
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());

        // A draw at the wrong price loses money however level the match is, so no price means no pick.
        double[] fairOutcomes = fairOutcomes(context);
        if (fairOutcomes == null) {
            log.debug("Draw withheld, no complete 1X2 price: fixtureId={}", context.getFixture().getId());
            return Optional.empty();
        }
        double marketDrawPct = fairOutcomes[1];
        double drawOdds = context.getOdds().getOddsFtX();

        GoalExpectation expectation = expectedGoals(context.getHomeTeamStats(), context.getAwayTeamStats());
        if (expectation == null) {
            log.debug("Draw withheld, venue record too thin to model: fixtureId={}",
                    context.getFixture().getId());
            return Optional.empty();
        }

        double modelDrawPct = poissonDrawProbability(expectation.home(), expectation.away());
        double blendedDrawPct = (modelDrawPct * WEIGHT_MODEL) + (marketDrawPct * WEIGHT_MARKET);
        double edgeAtPrice = ((modelDrawPct / 100.0) * drawOdds) - 1.0;

        List<String> unmetGates = evaluateGates(context, expectation, modelDrawPct, marketDrawPct, edgeAtPrice);
        if (!unmetGates.isEmpty()) {
            log.debug("Draw withheld for fixtureId={}: {}", context.getFixture().getId(), unmetGates);
            return Optional.empty();
        }

        ConfidenceLevel confidence = determineConfidence(blendedDrawPct);
        if (confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        Map<String, Object> factors = buildFactors(
                context, expectation, modelDrawPct, marketDrawPct, blendedDrawPct, edgeAtPrice);

        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(RecommendationType.DRAW)
                .confidence(confidence)
                .score(blendedDrawPct)
                .market("Draw")
                .odds(drawOdds)
                .description(buildDescription(context, confidence, blendedDrawPct, factors))
                .factors(factors)
                .build();

        log.info("Draw recommendation generated: fixtureId={}, model={}, market={}, published={}, edge={}",
                context.getFixture().getId(),
                String.format("%.1f", modelDrawPct),
                String.format("%.1f", marketDrawPct),
                String.format("%.1f", blendedDrawPct),
                String.format("%.3f", edgeAtPrice));

        return Optional.of(recommendation);
    }

    /**
     * Reasons this fixture should not be published, empty when it clears every gate.
     *
     * <p>Note the edge requirement is a band rather than a floor. Some edge is unavoidable - the
     * price has to be beaten for the bet to pay - but a large disagreement with the market is far
     * more likely to mean our expectations are wrong than that we have found a mispriced match, so
     * the far tail is rejected rather than treated as the best of the board.
     */
    private List<String> evaluateGates(FixtureContext context, GoalExpectation expectation,
            double modelDrawPct, double marketDrawPct, double edgeAtPrice) {
        List<String> unmet = new ArrayList<>();

        if (modelDrawPct < MIN_MODEL_DRAW_PCT) {
            unmet.add(String.format("model draw %.1f%% below %.1f%%", modelDrawPct, MIN_MODEL_DRAW_PCT));
        }
        if (marketDrawPct < MIN_MARKET_DRAW_PCT) {
            unmet.add(String.format("market draw %.1f%% below %.1f%%", marketDrawPct, MIN_MARKET_DRAW_PCT));
        }

        double ppgGap = ppgGap(context.getHomeTeamStats(), context.getAwayTeamStats());
        if (ppgGap > MAX_PPG_GAP) {
            unmet.add(String.format("PPG gap %.2f above %.2f", ppgGap, MAX_PPG_GAP));
        }

        double combined = expectation.combined();
        if (combined > MAX_COMBINED_EXPECTED_GOALS) {
            unmet.add(String.format("expected goals %.2f above %.2f", combined, MAX_COMBINED_EXPECTED_GOALS));
        }

        if (edgeAtPrice < MIN_EDGE_AT_PRICE) {
            unmet.add(String.format("edge %.3f below %.3f", edgeAtPrice, MIN_EDGE_AT_PRICE));
        } else if (edgeAtPrice > MAX_EDGE_AT_PRICE) {
            unmet.add(String.format("edge %.3f above %.3f, model likely wrong", edgeAtPrice, MAX_EDGE_AT_PRICE));
        }

        return unmet;
    }

    /** Expected goals for each side, or null when either venue record is too thin. */
    GoalExpectation expectedGoals(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        if (calculateMatchesAtVenue(homeStats, true) < MIN_VENUE_MATCHES
                || calculateMatchesAtVenue(awayStats, false) < MIN_VENUE_MATCHES) {
            return null;
        }

        double recordHome = (calculateVenueGoalsAvg(homeStats, true)
                + calculateVenueConcededAvg(awayStats, false)) / 2.0;
        double recordAway = (calculateVenueGoalsAvg(awayStats, false)
                + calculateVenueConcededAvg(homeStats, true)) / 2.0;

        Double homeXgFor = homeStats.getXgForAvgHome();
        Double homeXgAgainst = homeStats.getXgAgainstAvgHome();
        Double awayXgFor = awayStats.getXgForAvgAway();
        Double awayXgAgainst = awayStats.getXgAgainstAvgAway();
        if (homeXgFor == null || homeXgAgainst == null || awayXgFor == null || awayXgAgainst == null) {
            return new GoalExpectation(recordHome, recordAway, false);
        }

        double xgHome = (homeXgFor + awayXgAgainst) / 2.0;
        double xgAway = (awayXgFor + homeXgAgainst) / 2.0;
        return new GoalExpectation(
                (recordHome * (1 - WEIGHT_XG)) + (xgHome * WEIGHT_XG),
                (recordAway * (1 - WEIGHT_XG)) + (xgAway * WEIGHT_XG),
                true);
    }

    /** Expected goals for each side of the fixture. */
    record GoalExpectation(double home, double away, boolean usedXg) {
        double combined() {
            return home + away;
        }
    }

    private double[] fairOutcomes(FixtureContext context) {
        if (!context.hasOdds()) {
            return null;
        }
        return RecommendationUtils.fairOutcomeProbabilities(
                context.getOdds().getOddsFt1(),
                context.getOdds().getOddsFtX(),
                context.getOdds().getOddsFt2());
    }

    private double ppgGap(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        return Math.abs(safeDouble(homeStats.getPpgOverall()) - safeDouble(awayStats.getPpgOverall()));
    }

    ConfidenceLevel determineConfidence(double publishedDrawPct) {
        if (publishedDrawPct >= THRESHOLD_MODERATE) {
            return ConfidenceLevel.MODERATE;
        }
        return ConfidenceLevel.WEAK;
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private Map<String, Object> buildFactors(FixtureContext context, GoalExpectation expectation,
            double modelDrawPct, double marketDrawPct, double blendedDrawPct, double edgeAtPrice) {
        Map<String, Object> factors = new HashMap<>();

        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        factors.put("modelDrawProbability", modelDrawPct);
        factors.put("marketDrawProbability", marketDrawPct);
        factors.put("publishedDrawProbability", blendedDrawPct);
        factors.put("edgeAtPrice", edgeAtPrice);
        factors.put("drawOdds", context.getOdds().getOddsFtX());

        factors.put("expectedGoalsHome", expectation.home());
        factors.put("expectedGoalsAway", expectation.away());
        factors.put("combinedExpectedGoals", expectation.combined());
        factors.put("xgDataAvailable", expectation.usedXg());

        factors.put("ppgDifference", ppgGap(homeStats, awayStats));
        if (homeStats.getPosition() != null && awayStats.getPosition() != null) {
            factors.put("homePosition", homeStats.getPosition());
            factors.put("awayPosition", awayStats.getPosition());
            factors.put("positionDifference", Math.abs(homeStats.getPosition() - awayStats.getPosition()));
        }

        // Reported for context only. These no longer move the score; when they did, the score
        // tracked draw streaks rather than draw likelihood and the board lost money.
        double homeDrawPct = calculateDrawPercentage(homeStats, true);
        double awayDrawPct = calculateDrawPercentage(awayStats, false);
        factors.put("homeDrawPctSeason", homeDrawPct);
        factors.put("awayDrawPctSeason", awayDrawPct);
        if (context.hasRecentForm()) {
            factors.put("homeDrawsLast5", safeInt(context.getHomeTeamForm().getDrawsHome()));
            factors.put("awayDrawsLast5", safeInt(context.getAwayTeamForm().getDrawsAway()));
        }
        if (context.hasRefereeStats()) {
            RefereeStats refStats = context.getRefereeStats();
            factors.put("refereeDrawPct", safeDouble(refStats.getDrawsPer()));
            factors.put("refereeAppearances", refStats.getAppearancesOverall());
            if (refStats.getCardsPerMatchOverall() != null) {
                factors.put("refereeCardsPerMatch", refStats.getCardsPerMatchOverall());
            }
        }

        factors.put("positiveIndicators", positiveIndicators(context, expectation, edgeAtPrice));
        factors.put("riskFlags", riskFlags(context, expectation));

        return factors;
    }

    private List<String> positiveIndicators(FixtureContext context, GoalExpectation expectation,
            double edgeAtPrice) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();
        List<String> indicators = new ArrayList<>();

        double ppgGap = ppgGap(homeStats, awayStats);
        if (ppgGap < 0.3) {
            indicators.add("Very evenly matched teams (PPG diff < 0.3)");
        }
        if (Math.abs(expectation.home() - expectation.away()) < 0.15) {
            indicators.add(String.format("Near-identical goal expectations (%.2f v %.2f)",
                    expectation.home(), expectation.away()));
        }
        if (expectation.combined() < 2.3) {
            indicators.add(String.format("Low-scoring profile (%.2f expected goals)", expectation.combined()));
        }
        indicators.add(String.format("Price carries %.1f%% edge over our estimate", edgeAtPrice * 100));

        if (calculateDrawPercentage(homeStats, true) >= DRAW_SPECIALIST_HIGH
                || calculateDrawPercentage(awayStats, false) >= DRAW_SPECIALIST_HIGH) {
            indicators.add("Draw specialist team(s) involved");
        }
        if (expectation.usedXg()) {
            indicators.add("Goal expectations corroborated by xG");
        }
        if (context.hasRefereeStats()
                && safeDouble(context.getRefereeStats().getDrawsPer()) > REFEREE_DRAW_FRIENDLY_PCT) {
            indicators.add("Draw-friendly referee");
        }

        return indicators;
    }

    private List<String> riskFlags(FixtureContext context, GoalExpectation expectation) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();
        List<String> flags = new ArrayList<>();

        if (!expectation.usedXg()) {
            flags.add("No xG data available for validation");
        }
        if (expectation.combined() > 2.5) {
            flags.add("Goals expected - a draw needs the scoring to stay level");
        }
        if (Math.abs(expectation.home() - expectation.away()) > 0.35) {
            flags.add("One side expected to outscore the other");
        }
        if (context.hasRecentForm()) {
            int homeDraws = safeInt(context.getHomeTeamForm().getDrawsHome());
            int awayDraws = safeInt(context.getAwayTeamForm().getDrawsAway());
            if (homeDraws == 0 && awayDraws == 0) {
                flags.add("Neither team has drawn recently (decisive form)");
            }
        }
        if (homeStats.getPosition() != null && awayStats.getPosition() != null
                && ((homeStats.getPosition() >= 17 && awayStats.getPosition() <= 10)
                        || (awayStats.getPosition() >= 17 && homeStats.getPosition() <= 10))) {
            flags.add("Mismatch in stakes - one team desperate");
        }

        return flags;
    }

    private String buildDescription(FixtureContext context, ConfidenceLevel confidence,
            double publishedDrawPct, Map<String, Object> factors) {
        StringBuilder colour = new StringBuilder();

        double homeDrawPct = calculateDrawPercentage(context.getHomeTeamStats(), true);
        double awayDrawPct = calculateDrawPercentage(context.getAwayTeamStats(), false);
        if (homeDrawPct >= 30 && awayDrawPct >= 30) {
            colour.append("Draw specialists meeting under the neon");
        }

        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();
        if (homeStats.getXgForAvgOverall() != null && awayStats.getXgForAvgOverall() != null
                && Math.abs(safeDouble(homeStats.getXgForAvgOverall())
                        - safeDouble(awayStats.getXgForAvgOverall())) < XG_VERY_SIMILAR) {
            if (!colour.isEmpty()) {
                colour.append(". ");
            }
            colour.append("Similar xG profiles — stalemate perfume in the air");
        }

        return MatchBriefCopy.narrate(MatchBriefCopy.Brief.builder()
                .confidence(confidence)
                .selection("Draw")
                .context(context)
                .probabilityPct(publishedDrawPct)
                .colourNote(colour.isEmpty() ? null : colour.toString())
                .build());
    }
}
