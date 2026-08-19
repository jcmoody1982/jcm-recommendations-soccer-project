package com.jcm.recommendations.soccer.core.results;

import com.jcm.recommendations.soccer.core.client.FootyStatsApiClient;
import com.jcm.recommendations.soccer.core.client.dto.MatchDto;
import com.jcm.recommendations.soccer.core.config.ResultsProperties;
import com.jcm.recommendations.soccer.core.repository.CompletedMatchRepository;
import com.jcm.recommendations.soccer.core.repository.RecommendationSnapshotRepository;
import com.jcm.recommendations.soccer.core.results.settlement.MatchGoalEvents;
import com.jcm.recommendations.soccer.domain.CompletedMatch;
import com.jcm.recommendations.soccer.domain.PickOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * UC-032: fetch completed/updated match results for snapshotted fixtures.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResultsMatchIngestService {

    private static final List<String> PLAYER_PROP_TYPES = List.of("PLAYER_TO_SCORE", "PLAYER_TO_ASSIST");
    private static final List<PickOutcome> PLAYER_PROP_OPEN_OUTCOMES =
            List.of(PickOutcome.PENDING, PickOutcome.UNSUPPORTED);

    private final FootyStatsApiClient apiClient;
    private final CompletedMatchRepository completedMatchRepository;
    private final RecommendationSnapshotRepository snapshotRepository;
    private final ResultsProperties resultsProperties;

    public record IngestSummary(
            LocalDate primaryDate,
            int datesProcessed,
            int matchesUpserted,
            int fallbackFetches,
            Set<Long> touchedFixtureIds
    ) {}

    private record FallbackResult(int fetches, int upserts) {}

    @Transactional
    public IngestSummary ingestPendingResults() {
        LocalDate today = LocalDate.now(resultsProperties.zoneId());
        LocalDate yesterday = today.minusDays(1);
        LocalDate lookbackStart = today.minusDays(resultsProperties.getPendingLookbackDays());

        Set<LocalDate> dates = new LinkedHashSet<>();
        dates.add(yesterday);
        dates.addAll(snapshotRepository.findDistinctSnapshotDatesByOutcomeAndSnapshotDateGreaterThanEqual(
                PickOutcome.PENDING, lookbackStart));

        Set<Long> touched = new HashSet<>();
        int upserted = 0;
        for (LocalDate date : dates) {
            upserted += ingestTodaysMatches(date, touched);
        }

        List<Long> pendingFixtureIds = snapshotRepository
                .findDistinctFixtureIdsByOutcomeAndSnapshotDateGreaterThanEqual(
                        PickOutcome.PENDING, lookbackStart);
        Set<Long> playerPropFixtureIds = new LinkedHashSet<>(
                snapshotRepository.findDistinctFixtureIdsByTypesAndOutcomesSince(
                        PLAYER_PROP_TYPES, PLAYER_PROP_OPEN_OUTCOMES, lookbackStart));
        Set<Long> fallbackIds = new LinkedHashSet<>(pendingFixtureIds);
        fallbackIds.addAll(playerPropFixtureIds);
        FallbackResult fallback = fallbackFetchPendingFixtures(fallbackIds, playerPropFixtureIds, yesterday, touched);

        IngestSummary summary = new IngestSummary(
                yesterday, dates.size(), upserted + fallback.upserts(), fallback.fetches(), touched);
        log.info("Results ingest completed: primaryDate={}, dates={}, upserted={}, fallbacks={}, touched={}",
                summary.primaryDate(), summary.datesProcessed(), summary.matchesUpserted(),
                summary.fallbackFetches(), summary.touchedFixtureIds().size());
        return summary;
    }

    /**
     * Ingest a single calendar day via /todays-matches, then fall back to /match for any
     * PENDING snapshot fixtures on that date that are still missing or incomplete.
     */
    @Transactional
    public IngestSummary ingestForDate(LocalDate date) {
        Set<Long> touched = new HashSet<>();
        int upserted = ingestTodaysMatches(date, touched);

        List<Long> pendingFixtureIds = snapshotRepository
                .findDistinctFixtureIdsByOutcomeAndSnapshotDate(PickOutcome.PENDING, date);
        Set<Long> playerPropFixtureIds = new LinkedHashSet<>(
                snapshotRepository.findDistinctFixtureIdsByTypesAndOutcomesOnDate(
                        PLAYER_PROP_TYPES, PLAYER_PROP_OPEN_OUTCOMES, date));
        Set<Long> fallbackIds = new LinkedHashSet<>(pendingFixtureIds);
        fallbackIds.addAll(playerPropFixtureIds);
        FallbackResult fallback = fallbackFetchPendingFixtures(fallbackIds, playerPropFixtureIds, date, touched);

        IngestSummary summary = new IngestSummary(
                date, 1, upserted + fallback.upserts(), fallback.fetches(), touched);
        log.info("Results ingest for date completed: date={}, upserted={}, fallbacks={}, touched={}",
                date, summary.matchesUpserted(), summary.fallbackFetches(), summary.touchedFixtureIds().size());
        return summary;
    }

    private int ingestTodaysMatches(LocalDate date, Set<Long> touched) {
        List<MatchDto> matches = apiClient.fetchTodaysMatches(date.toString(), resultsProperties.getTimezone());
        int upserted = 0;
        for (MatchDto match : matches) {
            if (match.getId() == null) {
                continue;
            }
            upsertFromDto(match, date);
            touched.add(match.getId());
            upserted++;
        }
        return upserted;
    }

    private FallbackResult fallbackFetchPendingFixtures(
            Collection<Long> pendingFixtureIds,
            Set<Long> playerPropFixtureIds,
            LocalDate sourceDate,
            Set<Long> touched) {
        int fetches = 0;
        int upserts = 0;
        for (Long fixtureId : pendingFixtureIds) {
            CompletedMatch existing = completedMatchRepository.findById(fixtureId).orElse(null);
            if (!needsMatchDetail(existing, playerPropFixtureIds.contains(fixtureId))) {
                touched.add(fixtureId);
                continue;
            }
            MatchDto match = apiClient.fetchMatch(fixtureId);
            fetches++;
            if (match != null && match.getId() != null) {
                upsertFromDto(match, sourceDate);
                touched.add(match.getId());
                upserts++;
            }
        }
        return new FallbackResult(fetches, upserts);
    }

    private void upsertFromDto(MatchDto dto, LocalDate sourceDate) {
        CompletedMatch row = completedMatchRepository.findById(dto.getId()).orElseGet(CompletedMatch::new);
        row.setFixtureId(dto.getId());
        row.setStatus(normalizeStatus(dto.getStatus()));
        row.setHomeGoals(nonNegativeOrNull(dto.getHomeGoalCount()));
        row.setAwayGoals(nonNegativeOrNull(dto.getAwayGoalCount()));
        row.setHtHomeGoals(nonNegativeOrNull(dto.getHtGoalsTeamA()));
        row.setHtAwayGoals(nonNegativeOrNull(dto.getHtGoalsTeamB()));
        row.setSecondHalfHomeGoals(nonNegativeOrNull(dto.getGoals2hgTeamA()));
        row.setSecondHalfAwayGoals(nonNegativeOrNull(dto.getGoals2hgTeamB()));
        row.setHomeCorners(nonNegativeOrNull(dto.getTeamACorners()));
        row.setAwayCorners(nonNegativeOrNull(dto.getTeamBCorners()));
        row.setHomeYellowCards(nonNegativeOrNull(dto.getTeamAYellowCards()));
        row.setAwayYellowCards(nonNegativeOrNull(dto.getTeamBYellowCards()));
        row.setHomeRedCards(nonNegativeOrNull(dto.getTeamARedCards()));
        row.setAwayRedCards(nonNegativeOrNull(dto.getTeamBRedCards()));
        row.setSourceDate(sourceDate);
        row.setFetchedAt(Instant.now());
        if (dto.getTeamAGoalDetails() != null || dto.getTeamBGoalDetails() != null) {
            row.setGoalEventsJson(MatchGoalEvents.toJson(
                    MatchGoalEvents.fromDetails(dto.getTeamAGoalDetails(), dto.getTeamBGoalDetails())));
        }
        completedMatchRepository.save(row);
    }

    private static boolean needsMatchDetail(CompletedMatch existing, boolean playerPropFixture) {
        if (existing == null || !isCompleteStatus(existing.getStatus())) {
            return true;
        }
        if (!playerPropFixture) {
            return false;
        }
        return existing.getGoalEventsJson() == null || needsRetryEmptyGoalDetails(existing);
    }

    private static boolean needsRetryEmptyGoalDetails(CompletedMatch existing) {
        int total = (existing.getHomeGoals() == null ? 0 : existing.getHomeGoals())
                + (existing.getAwayGoals() == null ? 0 : existing.getAwayGoals());
        if (total <= 0) {
            return false;
        }
        List<MatchGoalEvents.StoredGoalEvent> events = MatchGoalEvents.parse(existing.getGoalEventsJson());
        return events != null && events.isEmpty();
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "unknown";
        }
        return status.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isCompleteStatus(String status) {
        return "complete".equalsIgnoreCase(status);
    }

    private static Integer nonNegativeOrNull(Integer value) {
        if (value == null || value < 0) {
            return null;
        }
        return value;
    }
}
