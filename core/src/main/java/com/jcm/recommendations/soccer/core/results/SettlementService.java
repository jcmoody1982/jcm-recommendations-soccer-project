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
import java.util.ArrayList;
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

        List<RecommendationSnapshot> candidates = new ArrayList<>(
                snapshotRepository.findByOutcome(PickOutcome.PENDING));
        // One-time / catch-up: re-grade corners & bookings previously marked UNSUPPORTED before graders existed
        for (RecommendationSnapshot unsupported : snapshotRepository.findByOutcome(PickOutcome.UNSUPPORTED)) {
            if (isCornersOrBookingsType(unsupported.getType()) || isPlayerPropType(unsupported.getType())) {
                candidates.add(unsupported);
            }
        }

        int resolved = 0;
        int stillPending = 0;
        int expiredVoids = 0;

        for (RecommendationSnapshot snapshot : candidates) {
            if (grader.isUnsupportedType(snapshot.getType())) {
                applyResolved(snapshot, GradeResult.unsupported("Unknown type: " + snapshot.getType()), null);
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
                demoteUnsupportedToPending(snapshot);
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
                demoteUnsupportedToPending(snapshot);
                stillPending++;
            }
        }

        SettlementSummary summary = new SettlementSummary(candidates.size(), resolved, stillPending, expiredVoids);
        log.info("Settlement completed: examined={}, resolved={}, pending={}, expiredVoids={}",
                summary.pendingExamined(), summary.resolved(), summary.stillPending(), summary.expiredVoids());
        return summary;
    }

    private void demoteUnsupportedToPending(RecommendationSnapshot snapshot) {
        if (snapshot.getOutcome() == PickOutcome.UNSUPPORTED) {
            snapshot.setOutcome(PickOutcome.PENDING);
            snapshot.setResolvedAt(null);
            snapshotRepository.save(snapshot);
        }
    }

    private static boolean isCornersOrBookingsType(String typeName) {
        return "OVER_CORNERS".equals(typeName)
                || "UNDER_CORNERS".equals(typeName)
                || "BOOKING_POINTS".equals(typeName);
    }

    private static boolean isPlayerPropType(String typeName) {
        return "PLAYER_TO_SCORE".equals(typeName) || "PLAYER_TO_ASSIST".equals(typeName);
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
        payload.put("homeCorners", match.getHomeCorners());
        payload.put("awayCorners", match.getAwayCorners());
        payload.put("homeYellowCards", match.getHomeYellowCards());
        payload.put("awayYellowCards", match.getAwayYellowCards());
        payload.put("homeRedCards", match.getHomeRedCards());
        payload.put("awayRedCards", match.getAwayRedCards());
        payload.put("goalEventsJson", match.getGoalEventsJson());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize matchResultJson for fixtureId={}", match.getFixtureId());
            return null;
        }
    }
}
