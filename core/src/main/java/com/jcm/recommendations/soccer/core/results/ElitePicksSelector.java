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
 * (top 10, ≤1 per fixture, ≤3 per market family, priced only).
 * Mirrors {@code site/src/utils/elitePicks.ts}.
 */
public final class ElitePicksSelector {

    public static final int ELITE_PICKS_LIMIT = 10;

    /**
     * Most slots any single market family may take. Elite ranks by probability, which structurally
     * favours short-priced markets: Over 1.5 clears in roughly three quarters of fixtures, so on
     * probability alone it outranks almost everything and fills the board on its own.
     *
     * <p>The cap counts families rather than types because capping types did not work. Three
     * separate types emit an "Over 1.5" market - full match, first half and second half - so a cap
     * of three per type still let nine of the ten slots read "Over 1.5 something". Those bets all
     * fire on the same underlying read, that the game will have goals in it, so they share one
     * budget.
     */
    public static final int ELITE_MAX_PER_FAMILY = 3;

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

    /**
     * Types that are really one bet in different clothing. Grouping is by type rather than by
     * parsing the market string, which varies per engine and would silently regroup on any wording
     * change.
     */
    private static final Map<String, String> TYPE_FAMILIES = Map.of(
            "OVER_GOALS", "GOALS_OVER",
            "OVER_15_GOALS", "GOALS_OVER",
            "OVER_25_GOALS", "GOALS_OVER",
            "FIRST_HALF_GOALS", "GOALS_OVER",
            "SECOND_HALF_GOALS", "GOALS_OVER"
    );

    private ElitePicksSelector() {}

    public static boolean isEliteEligibleType(String type) {
        return type != null && ELIGIBLE_TYPES.contains(type.toUpperCase(Locale.ROOT));
    }

    /** The shared budget key for a type, or the type itself when it stands alone. */
    public static String marketFamily(String type) {
        if (type == null) {
            return "";
        }
        String upper = type.toUpperCase(Locale.ROOT);
        return TYPE_FAMILIES.getOrDefault(upper, upper);
    }

    /**
     * Temporary: Over 0.5 half-goals score too high and crowd out other Elite picks.
     * Over 1.5 half-goals remain eligible.
     */
    public static boolean isExcludedFromElitePicks(String market) {
        return market != null && market.toLowerCase(Locale.ROOT).contains("over 0.5");
    }

    /**
     * Elite is a shortlist to bet, and an unpriced pick cannot be judged good or bad. The half-goals
     * engines hard-code a null price because the data model carries no half-time or second-half
     * lines, so they were filling the board with selections nobody could evaluate - and a market
     * that lands often but pays 1.4 can lose money at a high hit rate.
     */
    public static boolean hasUsablePrice(RecommendationSnapshot row) {
        return row != null && row.getOdds() != null && row.getOdds() > 1.0;
    }

    public static boolean isEliteEligible(RecommendationSnapshot row) {
        return row != null
                && "STRONG".equalsIgnoreCase(row.getConfidence())
                && isEliteEligibleType(row.getType())
                && !isExcludedFromElitePicks(row.getMarket())
                && hasUsablePrice(row);
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
        Map<String, Integer> familyCounts = new HashMap<>();
        List<RecommendationSnapshot> elite = new ArrayList<>();
        for (RecommendationSnapshot row : pool) {
            Long fixtureId = row.getFixtureId();
            if (fixtureId == null || seenFixtures.contains(fixtureId)) {
                continue;
            }
            String family = marketFamily(row.getType());
            int familyCount = familyCounts.getOrDefault(family, 0);
            if (familyCount >= ELITE_MAX_PER_FAMILY) {
                continue;
            }
            seenFixtures.add(fixtureId);
            familyCounts.put(family, familyCount + 1);
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
