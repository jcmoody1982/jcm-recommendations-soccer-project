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
            String description
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
            List<FixtureResultsView> fixtures
    ) {}

    public List<LocalDate> listSnapshotDates() {
        return snapshotRepository.findDistinctSnapshotDatesOrderBySnapshotDateDesc();
    }

    public DayResultsView getDayResults(LocalDate date, String outcomeFilter) {
        LocalDate snapshotDate = date != null ? date : resolveDefaultDate();
        if (snapshotDate == null) {
            return new DayResultsView(null, emptySummary(), emptySummary(), emptySummary(), List.of());
        }

        List<RecommendationSnapshot> allRows = snapshotRepository
                .findBySnapshotDateOrderByMatchDateUnixAscIdAsc(snapshotDate);

        DaySummary summary = summarize(allRows);
        DaySummary strongSummary = summarize(filterByConfidence(allRows, "STRONG"));
        DaySummary moderateSummary = summarize(filterByConfidence(allRows, "MODERATE"));

        List<RecommendationSnapshot> rows = allRows;
        PickOutcome filter = parseOutcome(outcomeFilter);
        if (filter != null) {
            rows = rows.stream().filter(r -> r.getOutcome() == filter).toList();
        }

        Set<Long> fixtureIds = rows.stream()
                .map(RecommendationSnapshot::getFixtureId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, CompletedMatch> matches = completedMatchRepository.findByFixtureIdIn(fixtureIds).stream()
                .collect(Collectors.toMap(CompletedMatch::getFixtureId, m -> m, (a, b) -> a));

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

        return new DayResultsView(snapshotDate, summary, strongSummary, moderateSummary, fixtures);
    }

    LocalDate resolveDefaultDate() {
        LocalDate today = LocalDate.now(resultsProperties.zoneId());
        return listSnapshotDates().stream()
                .filter(d -> !d.isAfter(today))
                .findFirst()
                .orElse(null);
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

    private PickView toPickView(RecommendationSnapshot row) {
        return new PickView(
                row.getId(),
                row.getType(),
                row.getMarket(),
                row.getConfidence(),
                row.getScore(),
                row.getOdds(),
                row.getOutcome() != null ? row.getOutcome().name() : PickOutcome.PENDING.name(),
                row.getDescription()
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
