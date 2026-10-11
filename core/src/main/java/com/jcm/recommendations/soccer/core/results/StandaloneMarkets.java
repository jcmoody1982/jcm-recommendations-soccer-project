package com.jcm.recommendations.soccer.core.results;

import java.util.Locale;
import java.util.Set;

/**
 * Markets that are published and tracked on their own board but sit outside the confidence tiers:
 * they never enter Elite, the Strong/Moderate tallies or the overall record. Their own by-market
 * row still reports how they perform. Mirrors {@code STANDALONE_TYPES} in
 * {@code site/src/utils/recommendationSections.ts}.
 */
public final class StandaloneMarkets {

    public static final Set<String> TYPES = Set.of("DRAW");

    private StandaloneMarkets() {}

    public static boolean isStandalone(String type) {
        return type != null && TYPES.contains(type.toUpperCase(Locale.ROOT));
    }
}
