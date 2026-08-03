package com.jcm.recommendations.soccer.core.results;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcm.recommendations.soccer.core.config.ResultsProperties;
import com.jcm.recommendations.soccer.core.repository.CompletedMatchRepository;
import com.jcm.recommendations.soccer.core.repository.RecommendationSnapshotRepository;
import com.jcm.recommendations.soccer.core.results.settlement.GradeResult;
import com.jcm.recommendations.soccer.core.results.settlement.PickSettlementGrader;
import com.jcm.recommendations.soccer.domain.CompletedMatch;
import com.jcm.recommendations.soccer.domain.PickOutcome;
import com.jcm.recommendations.soccer.domain.RecommendationSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * UC-033: settle snapshotted picks against completed match results.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementService {

    private final RecommendationSnapshotRepository snapshotRepository;
    private final CompletedMatchRepository completedMatchRepository;
    private final PickSettlementGrader grader;
    private final ResultsProperties resultsProperties;
    private final ObjectMapper objectMapper;

    public record SettlementSummary(int pendingExamined, int resolved, int stillPending, int expiredVoids) {}

    @Transactional
    public SettlementSummary settlePending() {
        LocalDate today = LocalDate.now(resultsProperties.zoneId());
        LocalDate lookbackStart = today.minusDays(resultsProperties.getPendingLookbackDays());

        List<RecommendationSnapshot> pending = snapshotRepository.findByOutcome(PickOutcome.PENDING);

        int resolved = 0;
        int stillPending = 0;
        int expiredVoids = 0;

        for (RecommendationSnapshot snapshot : pending) {
            if (grader.isUnsupportedType(snapshot.getType())) {
                applyResolved(snapshot, GradeResult.unsupported("Deferred market"), null);
                resolved++;
                continue;
            }

            if (snapshot.getSnapshotDate().isBefore(lookbackStart)) {
                applyResolved(snapshot, GradeResult.voided("Expired after lookback"),
                        completedMatchRepository.findById(snapshot.getFixtureId()).orElse(null));
                expiredVoids++;
                resolved++;
                continue;
            }

            CompletedMatch match = completedMatchRepository.findById(snapshot.getFixtureId()).orElse(null);
            if (match == null) {
                stillPending++;
                continue;
            }

            String status = match.getStatus() == null ? "unknown" : match.getStatus().toLowerCase(Locale.ROOT);
            GradeResult result = switch (status) {
                case "canceled", "cancelled", "suspended" -> GradeResult.voided("Match " + status);
                case "incomplete", "unknown" -> GradeResult.pending("Match status " + status);
                case "complete" -> grader.grade(snapshot, match);
                default -> GradeResult.pending("Unhandled status " + status);
            };

            if (result.isResolved()) {
                applyResolved(snapshot, result, match);
                resolved++;
            } else {
                stillPending++;
            }
        }

        SettlementSummary summary = new SettlementSummary(pending.size(), resolved, stillPending, expiredVoids);
        log.info("Settlement completed: examined={}, resolved={}, pending={}, expiredVoids={}",
                summary.pendingExamined(), summary.resolved(), summary.stillPending(), summary.expiredVoids());
        return summary;
    }

    private void applyResolved(RecommendationSnapshot snapshot, GradeResult result, CompletedMatch match) {
        snapshot.setOutcome(result.outcome());
        snapshot.setResolvedAt(Instant.now());
        if (match != null) {
            snapshot.setMatchResultJson(toMatchResultJson(match));
        }
        snapshotRepository.save(snapshot);
    }

    private String toMatchResultJson(CompletedMatch match) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("fixtureId", match.getFixtureId());
        payload.put("status", match.getStatus());
        payload.put("homeGoals", match.getHomeGoals());
        payload.put("awayGoals", match.getAwayGoals());
        payload.put("htHomeGoals", match.getHtHomeGoals());
        payload.put("htAwayGoals", match.getHtAwayGoals());
        payload.put("secondHalfHomeGoals", match.getSecondHalfHomeGoals());
        payload.put("secondHalfAwayGoals", match.getSecondHalfAwayGoals());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize matchResultJson for fixtureId={}", match.getFixtureId());
            return null;
        }
    }
}
