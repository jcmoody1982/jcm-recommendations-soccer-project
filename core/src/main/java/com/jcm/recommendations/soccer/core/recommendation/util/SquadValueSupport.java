package com.jcm.recommendations.soccer.core.recommendation.util;

import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;

import java.util.Locale;
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

    /**
     * Human-readable squad value gap for Info panels, e.g.
     * {@code Home €900m vs Away €200m (home 4.5×)}.
     */
    public static String formatDifference(FixtureContext context) {
        if (!context.hasSquadValue()) {
            return null;
        }
        long home = context.getHomeSquadProfile().getTotalMarketValueEur();
        long away = context.getAwaySquadProfile().getTotalMarketValueEur();
        double ratio = context.squadValueRatioHomeOverAway();
        String comparison;
        if (ratio >= 1.0) {
            comparison = String.format(Locale.ROOT, "home %.1f×", ratio);
        } else if (away > 0) {
            comparison = String.format(Locale.ROOT, "away %.1f×", (double) away / home);
        } else {
            comparison = "n/a";
        }
        return String.format(Locale.ROOT, "Home %s vs Away %s (%s)",
                formatEur(home), formatEur(away), comparison);
    }

    /** Short colour note for MatchBriefCopy when TM data is present. */
    public static String colourNote(FixtureContext context) {
        if (!context.hasSquadValue()) {
            return null;
        }
        String difference = formatDifference(context);
        if (supportsFavorite(context)) {
            return "Squad value backs the home side — " + difference;
        }
        if (suggestsUpset(context)) {
            return "Away squad richer on paper — " + difference;
        }
        return "Squad values close on paper — " + difference;
    }

    /** Append squad colour to an existing note (or return squad-only note). */
    public static String appendColourNote(String existing, FixtureContext context) {
        String squadNote = colourNote(context);
        if (squadNote == null) {
            return existing;
        }
        if (existing == null || existing.isBlank()) {
            return squadNote;
        }
        return existing + ". " + squadNote;
    }

    public static void putFactors(FixtureContext context, Map<String, Object> factors) {
        factors.put("squadValueApplied", context.hasSquadValue());
        if (!context.hasSquadValue()) {
            return;
        }
        long home = context.getHomeSquadProfile().getTotalMarketValueEur();
        long away = context.getAwaySquadProfile().getTotalMarketValueEur();
        factors.put("homeSquadValueEur", home);
        factors.put("awaySquadValueEur", away);
        factors.put("squadValueDifferenceEur", home - away);
        factors.put("squadValueDifference", formatDifference(context));
        factors.put("squadValueRatioHomeOverAway", context.squadValueRatioHomeOverAway());
        factors.put("squadValueSupportsFavorite", supportsFavorite(context));
        factors.put("squadValueSuggestsUpset", suggestsUpset(context));
        factors.put("squadValueBoostApplied", qualityBoost(context));
    }

    static String formatEur(long euros) {
        double millions = euros / 1_000_000.0;
        if (millions >= 100) {
            return String.format(Locale.ROOT, "€%.0fm", millions);
        }
        if (millions >= 10) {
            return String.format(Locale.ROOT, "€%.1fm", millions);
        }
        if (millions >= 1) {
            return String.format(Locale.ROOT, "€%.1fm", millions);
        }
        if (euros >= 1_000) {
            return String.format(Locale.ROOT, "€%.0fk", euros / 1_000.0);
        }
        return String.format(Locale.ROOT, "€%d", euros);
    }
}
