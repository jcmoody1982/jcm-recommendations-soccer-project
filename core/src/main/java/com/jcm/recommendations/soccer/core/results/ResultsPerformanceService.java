package com.jcm.recommendations.soccer.core.results;

import com.jcm.recommendations.soccer.core.config.ResultsProperties;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.core.repository.RecommendationSnapshotRepository;
import com.jcm.recommendations.soccer.domain.PickOutcome;
import com.jcm.recommendations.soccer.domain.RecommendationSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * UC-035: aggregate settled snapshot picks into hit rates by period, confidence, and type.
 */
@Service
@RequiredArgsConstructor
public class ResultsPerformanceService {

    public static final int MIN_SAMPLE = 10;

    /** Types paused from boards/metrics (engine still exists for later recalibration). */
    private static final Set<String> EXCLUDED_TYPES = Set.of("CLEAN_SHEET");

    /**
     * Types whose score is published as a probability percentage. Only these can be checked for
     * calibration — a corners or booking-points score is a predicted count, so comparing it to a
     * hit rate would be meaningless. Mirrors the {@code scoreUnit: '%'} entries in the site's
     * recommendation section config.
     */
    private static final Set<String> PROBABILITY_SCORED_TYPES = Set.of(
            "MATCH_RESULT", "BTTS", "DOUBLE_CHANCE", "RESULT_BTTS", "TOP_VS_BOTTOM",
            "OVER_15_GOALS", "OVER_25_GOALS", "PLAYER_TO_SCORE", "PLAYER_TO_ASSIST",
            "DRAW", "FIRST_HALF_GOALS", "SECOND_HALF_GOALS", "VALUE_BET",
            "OVER_GOALS", "UNDER_GOALS", "CLEAN_SHEET");

    /** Inclusive lower bounds of the reliability bands, ascending. */
    private static final int[] CALIBRATION_BAND_FLOORS = {0, 50, 60, 70, 80, 90};

    private final RecommendationSnapshotRepository snapshotRepository;
    private final ResultsProperties resultsProperties;

    /**
     * Settlement counts plus price-aware economics. Hit rate alone cannot say whether a board is
     * worth publishing: a 67% hit rate at average odds 1.44 still loses money, because it needs
     * 69.4% to break even. {@code roi} is profit per unit staked at flat stakes, over the picks
     * that carried a usable price.
     */
    public record BucketStats(
            int wins,
            int losses,
            int voids,
            int pending,
            int unsupported,
            Double hitRate,
            int sampleSize,
            boolean enoughData,
            int pricedSample,
            Double avgOdds,
            Double breakEvenRate,
            Double profitUnits,
            Double roi
    ) {}

    /**
     * One reliability band: what the engine claimed against what actually happened.
     * A negative {@code gap} means the band overstates its own chances.
     */
    public record CalibrationBand(
            String band,
            int sampleSize,
            Double avgScore,
            Double hitRate,
            Double gap,
            boolean enoughData
    ) {}

    public record TypePerformance(
            String type,
            BucketStats overall,
            Map<String, BucketStats> byConfidence,
            List<CalibrationBand> calibration
    ) {}

    public record PerformanceView(
            String period,
            LocalDate fromDate,
            LocalDate toDate,
            int minSample,
            BucketStats overall,
            BucketStats elite,
            Map<String, BucketStats> byConfidence,
            List<TypePerformance> byType
    ) {}

    public PerformanceView getPerformance(String periodRaw) {
        String period = normalizePeriod(periodRaw);
        LocalDate toDate = LocalDate.now(resultsProperties.zoneId());
        LocalDate fromDate = resolveFromDate(period, toDate);

        List<RecommendationSnapshot> rows = loadRows(fromDate, toDate).stream()
                .filter(ResultsPerformanceService::isIncludedType)
                .toList();
        List<RecommendationSnapshot> eliteRows = resolveEliteRows(rows);
        BucketStats overall = summarize(rows);
        BucketStats elite = summarize(eliteRows);
        Map<String, BucketStats> byConfidence = Map.of(
                "STRONG", summarize(filterConfidence(rows, "STRONG")),
                "MODERATE", summarize(filterConfidence(rows, "MODERATE"))
        );

        Map<String, List<RecommendationSnapshot>> byType = new LinkedHashMap<>();
        for (RecommendationType type : RecommendationType.values()) {
            if (EXCLUDED_TYPES.contains(type.name())) {
                continue;
            }
            byType.put(type.name(), new ArrayList<>());
        }
        for (RecommendationSnapshot row : rows) {
            String type = row.getType() == null ? "UNKNOWN" : row.getType();
            if (EXCLUDED_TYPES.contains(type)) {
                continue;
            }
            byType.computeIfAbsent(type, key -> new ArrayList<>()).add(row);
        }

        Map<String, List<RecommendationSnapshot>> eliteByType = new LinkedHashMap<>();
        for (RecommendationSnapshot row : eliteRows) {
            String type = row.getType() == null ? "UNKNOWN" : row.getType();
            eliteByType.computeIfAbsent(type, key -> new ArrayList<>()).add(row);
        }

        List<TypePerformance> typeRows = new ArrayList<>();
        for (Map.Entry<String, List<RecommendationSnapshot>> entry : byType.entrySet()) {
            List<RecommendationSnapshot> typePicks = entry.getValue();
            typeRows.add(new TypePerformance(
                    entry.getKey(),
                    summarize(typePicks),
                    Map.of(
                            "ELITE", summarize(eliteByType.getOrDefault(entry.getKey(), List.of())),
                            "STRONG", summarize(filterConfidence(typePicks, "STRONG")),
                            "MODERATE", summarize(filterConfidence(typePicks, "MODERATE"))
                    ),
                    calibrate(entry.getKey(), typePicks)
            ));
        }
        typeRows.sort(Comparator
                .comparingInt((TypePerformance t) -> t.overall().sampleSize()).reversed()
                .thenComparing(TypePerformance::type));

        return new PerformanceView(period, fromDate, toDate, MIN_SAMPLE, overall, elite, byConfidence, typeRows);
    }

    /**
     * Prefer persisted eliteRank tags per day; otherwise compute Elite-of-day on read
     * (same rule as {@link ResultsQueryService}).
     */
    static List<RecommendationSnapshot> resolveEliteRows(List<RecommendationSnapshot> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<LocalDate, List<RecommendationSnapshot>> byDate = new LinkedHashMap<>();
        for (RecommendationSnapshot row : rows) {
            if (row.getSnapshotDate() == null) {
                continue;
            }
            byDate.computeIfAbsent(row.getSnapshotDate(), key -> new ArrayList<>()).add(row);
        }

        List<RecommendationSnapshot> elite = new ArrayList<>();
        for (List<RecommendationSnapshot> dayRows : byDate.values()) {
            boolean anyTagged = dayRows.stream().anyMatch(r -> r.getEliteRank() != null);
            if (anyTagged) {
                dayRows.stream()
                        .filter(r -> r.getEliteRank() != null)
                        .filter(r -> !ElitePicksSelector.isExcludedFromElitePicks(r.getMarket()))
                        .forEach(elite::add);
            } else {
                elite.addAll(ElitePicksSelector.select(dayRows));
            }
        }
        return elite;
    }

    private List<RecommendationSnapshot> loadRows(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null) {
            return snapshotRepository.findBySnapshotDateLessThanEqual(toDate);
        }
        return snapshotRepository.findBySnapshotDateBetweenInclusive(fromDate, toDate);
    }

    static boolean isIncludedType(RecommendationSnapshot row) {
        String type = row.getType();
        return type == null || !EXCLUDED_TYPES.contains(type);
    }

    static String normalizePeriod(String periodRaw) {
        if (periodRaw == null || periodRaw.isBlank()) {
            return "30d";
        }
        String period = periodRaw.trim().toLowerCase(Locale.ROOT);
        return switch (period) {
            case "7d", "30d", "90d", "all" -> period;
            default -> throw new IllegalArgumentException("Invalid period: " + periodRaw);
        };
    }

    static LocalDate resolveFromDate(String period, LocalDate toDate) {
        return switch (period) {
            case "7d" -> toDate.minusDays(6);
            case "30d" -> toDate.minusDays(29);
            case "90d" -> toDate.minusDays(89);
            case "all" -> null;
            default -> toDate.minusDays(29);
        };
    }

    private static List<RecommendationSnapshot> filterConfidence(
            List<RecommendationSnapshot> rows, String confidence) {
        return rows.stream()
                .filter(r -> confidence.equalsIgnoreCase(r.getConfidence()))
                .toList();
    }

    static BucketStats summarize(List<RecommendationSnapshot> rows) {
        int wins = 0, losses = 0, voids = 0, pending = 0, unsupported = 0;
        int pricedSample = 0;
        double oddsSum = 0.0;
        double profit = 0.0;
        for (RecommendationSnapshot row : rows) {
            PickOutcome outcome = row.getOutcome() == null ? PickOutcome.PENDING : row.getOutcome();
            switch (outcome) {
                case WIN -> wins++;
                case LOSS -> losses++;
                case VOID -> voids++;
                case UNSUPPORTED -> unsupported++;
                case PENDING -> pending++;
            }
            if ((outcome == PickOutcome.WIN || outcome == PickOutcome.LOSS) && hasUsablePrice(row)) {
                pricedSample++;
                oddsSum += row.getOdds();
                profit += outcome == PickOutcome.WIN ? row.getOdds() - 1.0 : -1.0;
            }
        }
        int sampleSize = wins + losses;
        Double hitRate = sampleSize > 0 ? (wins * 100.0) / sampleSize : null;
        boolean enoughData = sampleSize >= MIN_SAMPLE;

        Double avgOdds = null;
        Double breakEvenRate = null;
        Double profitUnits = null;
        Double roi = null;
        if (pricedSample > 0) {
            avgOdds = oddsSum / pricedSample;
            breakEvenRate = 100.0 / avgOdds;
            profitUnits = profit;
            roi = (profit * 100.0) / pricedSample;
        }

        return new BucketStats(wins, losses, voids, pending, unsupported, hitRate, sampleSize,
                enoughData, pricedSample, avgOdds, breakEvenRate, profitUnits, roi);
    }

    /** A price is only usable for ROI if it could actually be backed at better than evens-out. */
    static boolean hasUsablePrice(RecommendationSnapshot row) {
        return row.getOdds() != null && row.getOdds() > 1.0;
    }

    /**
     * Bucket settled picks by published score and compare the claim to the outcome. This is the
     * check that catches an engine drifting away from reality while its hit rate still looks
     * respectable, and it is why the player prop boards could publish ~80% claims at a 10-19%
     * strike rate without any existing metric flagging it.
     */
    static List<CalibrationBand> calibrate(String type, List<RecommendationSnapshot> rows) {
        if (type == null || !PROBABILITY_SCORED_TYPES.contains(type) || rows == null) {
            return List.of();
        }

        int bandCount = CALIBRATION_BAND_FLOORS.length;
        int[] wins = new int[bandCount];
        int[] counts = new int[bandCount];
        double[] scoreSums = new double[bandCount];

        for (RecommendationSnapshot row : rows) {
            PickOutcome outcome = row.getOutcome();
            if (outcome != PickOutcome.WIN && outcome != PickOutcome.LOSS) {
                continue;
            }
            Double score = row.getScore();
            if (score == null) {
                continue;
            }
            int index = bandIndex(score);
            counts[index]++;
            scoreSums[index] += score;
            if (outcome == PickOutcome.WIN) {
                wins[index]++;
            }
        }

        List<CalibrationBand> bands = new ArrayList<>();
        for (int i = 0; i < bandCount; i++) {
            if (counts[i] == 0) {
                continue;
            }
            double avgScore = scoreSums[i] / counts[i];
            double hitRate = (wins[i] * 100.0) / counts[i];
            bands.add(new CalibrationBand(
                    bandLabel(i),
                    counts[i],
                    avgScore,
                    hitRate,
                    hitRate - avgScore,
                    counts[i] >= MIN_SAMPLE));
        }
        return bands;
    }

    private static int bandIndex(double score) {
        int index = 0;
        for (int i = 0; i < CALIBRATION_BAND_FLOORS.length; i++) {
            if (score >= CALIBRATION_BAND_FLOORS[i]) {
                index = i;
            }
        }
        return index;
    }

    private static String bandLabel(int index) {
        int floor = CALIBRATION_BAND_FLOORS[index];
        if (index == 0) {
            return "<" + CALIBRATION_BAND_FLOORS[1];
        }
        if (index == CALIBRATION_BAND_FLOORS.length - 1) {
            return floor + "+";
        }
        return floor + "-" + (CALIBRATION_BAND_FLOORS[index + 1] - 1);
    }
}
