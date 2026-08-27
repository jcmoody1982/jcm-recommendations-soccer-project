package com.jcm.recommendations.soccer.core.results;

import com.jcm.recommendations.soccer.core.config.ResultsProperties;
import com.jcm.recommendations.soccer.core.repository.CompletedMatchRepository;
import com.jcm.recommendations.soccer.core.repository.RecommendationSnapshotRepository;
import com.jcm.recommendations.soccer.domain.CompletedMatch;
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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResultsQueryService {

    /** Types paused from Results day board and day summaries. */
    private static final Set<String> EXCLUDED_TYPES = Set.of("CLEAN_SHEET");

    private final RecommendationSnapshotRepository snapshotRepository;
    private final CompletedMatchRepository completedMatchRepository;
    private final ResultsProperties resultsProperties;

    public record DaySummary(
            int wins,
            int losses,
            int voids,
            int pending,
            int unsupported,
            Double hitRate
    ) {}

    public record PickView(
            Long id,
            String type,
            String market,
            String confidence,
            Double score,
            Double odds,
            String outcome,
            String description,
            Integer eliteRank
    ) {}

    public record ScorelineView(Integer home, Integer away) {}

    public record FixtureResultsView(
            Long fixtureId,
            String homeTeamName,
            String awayTeamName,
            Long matchDateUnix,
            String leagueName,
            String leagueImage,
            ScorelineView scoreline,
            String matchStatus,
            List<PickView> picks
    ) {}

    public record DayResultsView(
            LocalDate snapshotDate,
            DaySummary summary,
            DaySummary strongSummary,
            DaySummary moderateSummary,
            DaySummary eliteSummary,
            List<FixtureResultsView> fixtures,
            List<FixtureResultsView> eliteFixtures
    ) {}

    public List<LocalDate> listSnapshotDates() {
        return snapshotRepository.findDistinctSnapshotDatesOrderBySnapshotDateDesc();
    }

    public DayResultsView getDayResults(LocalDate date, String outcomeFilter) {
        LocalDate snapshotDate = date != null ? date : resolveDefaultDate();
        if (snapshotDate == null) {
            return new DayResultsView(
                    null, emptySummary(), emptySummary(), emptySummary(), emptySummary(), List.of(), List.of());
        }

        List<RecommendationSnapshot> allRows = snapshotRepository
                .findBySnapshotDateOrderByMatchDateUnixAscIdAsc(snapshotDate)
                .stream()
                .filter(ResultsQueryService::isIncludedType)
                .toList();

        DaySummary summary = summarize(allRows);
        DaySummary strongSummary = summarize(filterByConfidence(allRows, "STRONG"));
        DaySummary moderateSummary = summarize(filterByConfidence(allRows, "MODERATE"));

        List<RecommendationSnapshot> eliteRows = resolveEliteRows(allRows);
        DaySummary eliteSummary = summarize(eliteRows);

        List<RecommendationSnapshot> rows = allRows;
        PickOutcome filter = parseOutcome(outcomeFilter);
        if (filter != null) {
            rows = rows.stream().filter(r -> r.getOutcome() == filter).toList();
        }

        List<FixtureResultsView> fixtures = toFixtureViews(rows, completedMatchesFor(rows));

        List<RecommendationSnapshot> eliteFiltered = eliteRows;
        if (filter != null) {
            eliteFiltered = eliteRows.stream().filter(r -> r.getOutcome() == filter).toList();
        }
        List<FixtureResultsView> eliteFixtures = toEliteFixtureViews(
                eliteFiltered, completedMatchesFor(eliteFiltered));

        return new DayResultsView(
                snapshotDate, summary, strongSummary, moderateSummary, eliteSummary, fixtures, eliteFixtures);
    }

    LocalDate resolveDefaultDate() {
        LocalDate today = LocalDate.now(resultsProperties.zoneId());
        return listSnapshotDates().stream()
                .filter(d -> !d.isAfter(today))
                .findFirst()
                .orElse(null);
    }

    /**
     * Prefer persisted eliteRank tags; otherwise compute Elite-of-day on read (historical days).
     */
    List<RecommendationSnapshot> resolveEliteRows(List<RecommendationSnapshot> allRows) {
        boolean anyTagged = allRows.stream().anyMatch(r -> r.getEliteRank() != null);
        if (anyTagged) {
            return allRows.stream()
                    .filter(r -> r.getEliteRank() != null)
                    .sorted(Comparator.comparingInt(RecommendationSnapshot::getEliteRank))
                    .toList();
        }
        return ElitePicksSelector.select(allRows);
    }

    private Map<Long, CompletedMatch> completedMatchesFor(List<RecommendationSnapshot> rows) {
        Set<Long> fixtureIds = rows.stream()
                .map(RecommendationSnapshot::getFixtureId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (fixtureIds.isEmpty()) {
            return Map.of();
        }
        return completedMatchRepository.findByFixtureIdIn(fixtureIds).stream()
                .collect(Collectors.toMap(CompletedMatch::getFixtureId, m -> m, (a, b) -> a));
    }

    private List<FixtureResultsView> toFixtureViews(
            List<RecommendationSnapshot> rows, Map<Long, CompletedMatch> matches) {
        Map<Long, List<RecommendationSnapshot>> byFixture = new LinkedHashMap<>();
        for (RecommendationSnapshot row : rows) {
            byFixture.computeIfAbsent(row.getFixtureId(), id -> new ArrayList<>()).add(row);
        }

        List<FixtureResultsView> fixtures = new ArrayList<>();
        for (Map.Entry<Long, List<RecommendationSnapshot>> entry : byFixture.entrySet()) {
            List<RecommendationSnapshot> picks = entry.getValue();
            picks.sort(pickComparator());
            RecommendationSnapshot first = picks.getFirst();
            CompletedMatch match = matches.get(entry.getKey());
            fixtures.add(new FixtureResultsView(
                    entry.getKey(),
                    first.getHomeTeamName(),
                    first.getAwayTeamName(),
                    first.getMatchDateUnix(),
                    first.getLeagueName(),
                    first.getLeagueImage(),
                    scorelineOf(match),
                    match != null ? match.getStatus() : null,
                    picks.stream().map(this::toPickView).toList()
            ));
        }

        fixtures.sort(Comparator.comparing(
                FixtureResultsView::matchDateUnix,
                Comparator.nullsLast(Long::compareTo)));
        return fixtures;
    }

    /** One pick per fixture, ordered by elite rank. */
    private List<FixtureResultsView> toEliteFixtureViews(
            List<RecommendationSnapshot> eliteRows, Map<Long, CompletedMatch> matches) {
        List<FixtureResultsView> fixtures = new ArrayList<>();
        for (int i = 0; i < eliteRows.size(); i++) {
            RecommendationSnapshot row = eliteRows.get(i);
            Integer rank = row.getEliteRank() != null ? row.getEliteRank() : i + 1;
            CompletedMatch match = matches.get(row.getFixtureId());
            fixtures.add(new FixtureResultsView(
                    row.getFixtureId(),
                    row.getHomeTeamName(),
                    row.getAwayTeamName(),
                    row.getMatchDateUnix(),
                    row.getLeagueName(),
                    row.getLeagueImage(),
                    scorelineOf(match),
                    match != null ? match.getStatus() : null,
                    List.of(toPickView(row, rank))
            ));
        }
        return fixtures;
    }

    private DaySummary summarize(List<RecommendationSnapshot> rows) {
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
        Double hitRate = null;
        int graded = wins + losses;
        if (graded > 0) {
            hitRate = (wins * 100.0) / graded;
        }
        return new DaySummary(wins, losses, voids, pending, unsupported, hitRate);
    }

    private static DaySummary emptySummary() {
        return new DaySummary(0, 0, 0, 0, 0, null);
    }

    private static List<RecommendationSnapshot> filterByConfidence(
            List<RecommendationSnapshot> rows, String confidence) {
        return rows.stream()
                .filter(r -> confidence.equalsIgnoreCase(r.getConfidence()))
                .toList();
    }

    static boolean isIncludedType(RecommendationSnapshot row) {
        String type = row.getType();
        return type == null || !EXCLUDED_TYPES.contains(type);
    }

    private PickView toPickView(RecommendationSnapshot row) {
        return toPickView(row, row.getEliteRank());
    }

    private PickView toPickView(RecommendationSnapshot row, Integer eliteRank) {
        return new PickView(
                row.getId(),
                row.getType(),
                row.getMarket(),
                row.getConfidence(),
                row.getScore(),
                row.getOdds(),
                row.getOutcome() != null ? row.getOutcome().name() : PickOutcome.PENDING.name(),
                row.getDescription(),
                eliteRank
        );
    }

    private static ScorelineView scorelineOf(CompletedMatch match) {
        if (match == null || match.getHomeGoals() == null || match.getAwayGoals() == null) {
            return null;
        }
        return new ScorelineView(match.getHomeGoals(), match.getAwayGoals());
    }

    private static PickOutcome parseOutcome(String raw) {
        if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw)) {
            return null;
        }
        try {
            return PickOutcome.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid outcome filter: " + raw);
        }
    }

    private static Comparator<RecommendationSnapshot> pickComparator() {
        return Comparator
                .comparingInt((RecommendationSnapshot r) -> confidenceWeight(r.getConfidence())).reversed()
                .thenComparing(RecommendationSnapshot::getScore, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private static int confidenceWeight(String confidence) {
        if (confidence == null) {
            return 0;
        }
        return switch (confidence.toUpperCase(Locale.ROOT)) {
            case "STRONG" -> 3;
            case "MODERATE" -> 2;
            default -> 1;
        };
    }
}
