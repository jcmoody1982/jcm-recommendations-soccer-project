package com.jcm.recommendations.soccer.core.results;

import com.jcm.recommendations.soccer.domain.RecommendationSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * UC-036 / UC-037: rank Strong %-style snapshot picks into Elite
 * (top 10, ≤1 per fixture, ≤3 per market type).
 * Mirrors {@code site/src/utils/elitePicks.ts}.
 */
public final class ElitePicksSelector {

    public static final int ELITE_PICKS_LIMIT = 10;

    /**
     * Most slots any single market may take. Elite ranks by probability, which structurally
     * favours short-priced markets: Over 1.5 clears in roughly three quarters of fixtures, so on
     * probability alone it outranks almost everything and fills the board on its own. BTTS was
     * capped for exactly this reason; applying one cap to every type retires the special case
     * rather than adding a new exception each time a market runs hot.
     */
    public static final int ELITE_MAX_PER_TYPE = 3;

    private static final Set<String> ELIGIBLE_TYPES = Set.of(
            "MATCH_RESULT",
            "BTTS",
            "DOUBLE_CHANCE",
            "DRAW",
            "OVER_GOALS",
            "OVER_15_GOALS",
            "OVER_25_GOALS",
            "UNDER_GOALS",
            "RESULT_BTTS",
            "TOP_VS_BOTTOM",
            "FIRST_HALF_GOALS",
            "SECOND_HALF_GOALS",
            "VALUE_BET"
    );

    private ElitePicksSelector() {}

    public static boolean isEliteEligibleType(String type) {
        return type != null && ELIGIBLE_TYPES.contains(type.toUpperCase(Locale.ROOT));
    }

    /**
     * Temporary: Over 0.5 half-goals score too high and crowd out other Elite picks.
     * Over 1.5 half-goals remain eligible.
     */
    public static boolean isExcludedFromElitePicks(String market) {
        return market != null && market.toLowerCase(Locale.ROOT).contains("over 0.5");
    }

    public static boolean isEliteEligible(RecommendationSnapshot row) {
        return row != null
                && "STRONG".equalsIgnoreCase(row.getConfidence())
                && isEliteEligibleType(row.getType())
                && !isExcludedFromElitePicks(row.getMarket());
    }

    /**
     * Returns Elite picks in rank order (best first). Does not mutate input rows.
     */
    public static List<RecommendationSnapshot> select(List<RecommendationSnapshot> daySnaps) {
        return select(daySnaps, ELITE_PICKS_LIMIT);
    }

    public static List<RecommendationSnapshot> select(List<RecommendationSnapshot> daySnaps, int limit) {
        if (daySnaps == null || daySnaps.isEmpty() || limit <= 0) {
            return List.of();
        }

        List<RecommendationSnapshot> pool = daySnaps.stream()
                .filter(ElitePicksSelector::isEliteEligible)
                .sorted(eliteComparator())
                .toList();

        Set<Long> seenFixtures = new HashSet<>();
        Map<String, Integer> typeCounts = new HashMap<>();
        List<RecommendationSnapshot> elite = new ArrayList<>();
        for (RecommendationSnapshot row : pool) {
            Long fixtureId = row.getFixtureId();
            if (fixtureId == null || seenFixtures.contains(fixtureId)) {
                continue;
            }
            String type = row.getType() == null ? "" : row.getType().toUpperCase(Locale.ROOT);
            int typeCount = typeCounts.getOrDefault(type, 0);
            if (typeCount >= ELITE_MAX_PER_TYPE) {
                continue;
            }
            seenFixtures.add(fixtureId);
            typeCounts.put(type, typeCount + 1);
            elite.add(row);
            if (elite.size() >= limit) {
                break;
            }
        }
        return elite;
    }

    private static Comparator<RecommendationSnapshot> eliteComparator() {
        return Comparator
                .comparing(RecommendationSnapshot::getScore, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ElitePicksSelector::oddsAscNullsLast)
                .thenComparing(RecommendationSnapshot::getMatchDateUnix, Comparator.nullsLast(Long::compareTo));
    }

    private static double oddsAscNullsLast(RecommendationSnapshot row) {
        Double odds = row.getOdds();
        if (odds == null || odds <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return odds;
    }
}
