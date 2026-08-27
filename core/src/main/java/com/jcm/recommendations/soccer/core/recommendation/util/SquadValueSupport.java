package com.jcm.recommendations.soccer.core.recommendation.util;

import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;

import java.util.Map;

/**
 * Additive Transfermarkt squad-value enrichment shared across engines.
 * No-op when TM profiles are missing or not engine-usable.
 */
public final class SquadValueSupport {

    public static final double RATIO_SUPPORT = 1.8;
    public static final double QUALITY_BOOST = 4.0;
    public static final double PROBABILITY_BOOST = 4.0;

    private SquadValueSupport() {
    }

    /** Boost for 0–100 quality scores (e.g. Top vs Bottom). */
    public static double qualityBoost(FixtureContext context) {
        if (!context.hasSquadValue()) {
            return 0.0;
        }
        if (context.squadValueRatioHomeOverAway() >= RATIO_SUPPORT) {
            return QUALITY_BOOST;
        }
        return 0.0;
    }

    /** Boost in percentage points on a 0–100 probability scale (e.g. Match Result). */
    public static double homeProbabilityBoost(FixtureContext context) {
        return qualityBoost(context);
    }

    /** Boost as a 0–1 probability fraction (e.g. Value Bet home-win model). */
    public static double homeProbabilityBoostFraction(FixtureContext context) {
        return homeProbabilityBoost(context) / 100.0;
    }

    public static boolean supportsFavorite(FixtureContext context) {
        return context.hasSquadValue()
                && context.squadValueRatioHomeOverAway() >= RATIO_SUPPORT;
    }

    public static boolean suggestsUpset(FixtureContext context) {
        return context.hasSquadValue()
                && context.squadValueRatioHomeOverAway() < 1.0;
    }

    public static void putFactors(FixtureContext context, Map<String, Object> factors) {
        factors.put("squadValueApplied", context.hasSquadValue());
        if (!context.hasSquadValue()) {
            return;
        }
        factors.put("homeSquadValueEur", context.getHomeSquadProfile().getTotalMarketValueEur());
        factors.put("awaySquadValueEur", context.getAwaySquadProfile().getTotalMarketValueEur());
        factors.put("squadValueRatioHomeOverAway", context.squadValueRatioHomeOverAway());
        factors.put("squadValueSupportsFavorite", supportsFavorite(context));
        factors.put("squadValueSuggestsUpset", suggestsUpset(context));
        factors.put("squadValueBoostApplied", qualityBoost(context));
    }
}
