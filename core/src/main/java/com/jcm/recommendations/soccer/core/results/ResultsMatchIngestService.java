package com.jcm.recommendations.soccer.core.results;

import com.jcm.recommendations.soccer.core.client.FootyStatsApiClient;
import com.jcm.recommendations.soccer.core.client.dto.MatchDto;
import com.jcm.recommendations.soccer.core.config.ResultsProperties;
import com.jcm.recommendations.soccer.core.repository.CompletedMatchRepository;
import com.jcm.recommendations.soccer.core.repository.RecommendationSnapshotRepository;
import com.jcm.recommendations.soccer.domain.CompletedMatch;
import com.jcm.recommendations.soccer.domain.PickOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
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
            List<MatchDto> matches = apiClient.fetchTodaysMatches(date.toString(), resultsProperties.getTimezone());
            for (MatchDto match : matches) {
                if (match.getId() == null) {
                    continue;
                }
                upsertFromDto(match, date);
                touched.add(match.getId());
                upserted++;
            }
        }

        List<Long> pendingFixtureIds = snapshotRepository
                .findDistinctFixtureIdsByOutcomeAndSnapshotDateGreaterThanEqual(
                        PickOutcome.PENDING, lookbackStart);

        int fallbackFetches = 0;
        for (Long fixtureId : pendingFixtureIds) {
            CompletedMatch existing = completedMatchRepository.findById(fixtureId).orElse(null);
            if (existing != null && isCompleteStatus(existing.getStatus())) {
                touched.add(fixtureId);
                continue;
            }
            MatchDto match = apiClient.fetchMatch(fixtureId);
            fallbackFetches++;
            if (match != null && match.getId() != null) {
                upsertFromDto(match, yesterday);
                touched.add(match.getId());
                upserted++;
            }
        }

        IngestSummary summary = new IngestSummary(yesterday, dates.size(), upserted, fallbackFetches, touched);
        log.info("Results ingest completed: primaryDate={}, dates={}, upserted={}, fallbacks={}, touched={}",
                summary.primaryDate(), summary.datesProcessed(), summary.matchesUpserted(),
                summary.fallbackFetches(), summary.touchedFixtureIds().size());
        return summary;
    }

    @Transactional
    public IngestSummary ingestForDate(LocalDate date) {
        List<MatchDto> matches = apiClient.fetchTodaysMatches(date.toString(), resultsProperties.getTimezone());
        Set<Long> touched = new HashSet<>();
        int upserted = 0;
        for (MatchDto match : matches) {
            if (match.getId() == null) {
                continue;
            }
            upsertFromDto(match, date);
            touched.add(match.getId());
            upserted++;
        }
        return new IngestSummary(date, 1, upserted, 0, touched);
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
        completedMatchRepository.save(row);
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
