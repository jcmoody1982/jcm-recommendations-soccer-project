package com.jcm.recommendations.soccer.core.results;

import com.jcm.recommendations.soccer.domain.RecommendationSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * UC-036 / UC-037: rank Strong %-style snapshot picks into Elite
 * (top 10, ≤1 per fixture, ≤3 BTTS).
 * Mirrors {@code site/src/utils/elitePicks.ts}.
 */
public final class ElitePicksSelector {

    public static final int ELITE_PICKS_LIMIT = 10;
    public static final int ELITE_BTTS_CAP = 3;

    private static final Set<String> ELIGIBLE_TYPES = Set.of(
            "MATCH_RESULT",
            "BTTS",
            "DOUBLE_CHANCE",
            "DRAW",
            "OVER_GOALS",
            "OVER_15_GOALS",
            "OVER_25_GOALS",
            "UNDER_GOALS",
            "CLEAN_SHEET",
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
                .filter(r -> "STRONG".equalsIgnoreCase(r.getConfidence()))
                .filter(r -> isEliteEligibleType(r.getType()))
                .sorted(eliteComparator())
                .toList();

        Set<Long> seenFixtures = new HashSet<>();
        int bttsCount = 0;
        List<RecommendationSnapshot> elite = new ArrayList<>();
        for (RecommendationSnapshot row : pool) {
            Long fixtureId = row.getFixtureId();
            if (fixtureId == null || seenFixtures.contains(fixtureId)) {
                continue;
            }
            boolean isBtts = "BTTS".equalsIgnoreCase(row.getType());
            if (isBtts && bttsCount >= ELITE_BTTS_CAP) {
                continue;
            }
            seenFixtures.add(fixtureId);
            if (isBtts) {
                bttsCount++;
            }
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
