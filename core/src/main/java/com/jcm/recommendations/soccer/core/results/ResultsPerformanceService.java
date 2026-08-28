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

    private final RecommendationSnapshotRepository snapshotRepository;
    private final ResultsProperties resultsProperties;

    public record BucketStats(
            int wins,
            int losses,
            int voids,
            int pending,
            int unsupported,
            Double hitRate,
            int sampleSize,
            boolean enoughData
    ) {}

    public record TypePerformance(
            String type,
            BucketStats overall,
            Map<String, BucketStats> byConfidence
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
                    )
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
        for (RecommendationSnapshot row : rows) {
            PickOutcome outcome = row.getOutcome() == null ? PickOutcome.PENDING : row.getOutcome();
            switch (outcome) {
                case WIN -> wins++;
                case LOSS -> losses++;
                case VOID -> voids++;
                case UNSUPPORTED -> unsupported++;
                case PENDING -> pending++;
            }
        }
        int sampleSize = wins + losses;
        Double hitRate = null;
        boolean enoughData = sampleSize >= MIN_SAMPLE;
        if (sampleSize > 0) {
            hitRate = (wins * 100.0) / sampleSize;
        }
        return new BucketStats(wins, losses, voids, pending, unsupported, hitRate, sampleSize, enoughData);
    }
}
