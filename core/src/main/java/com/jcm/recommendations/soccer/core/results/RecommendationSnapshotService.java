package com.jcm.recommendations.soccer.core.results;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcm.recommendations.soccer.core.config.ResultsProperties;
import com.jcm.recommendations.soccer.core.recommendation.RecommendationService;
import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.repository.RecommendationSnapshotRepository;
import com.jcm.recommendations.soccer.domain.PickOutcome;
import com.jcm.recommendations.soccer.domain.RecommendationSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * UC-031: freeze STRONG/MODERATE picks for fixtures kicking off today (brand timezone).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationSnapshotService {

    private final RecommendationService recommendationService;
    private final RecommendationSnapshotRepository snapshotRepository;
    private final ResultsProperties resultsProperties;
    private final ObjectMapper objectMapper;

    public record SnapshotSummary(LocalDate snapshotDate, int considered, int inserted, int updated, int skipped) {}

    @Transactional
    public SnapshotSummary snapshotToday() {
        return snapshotForDate(LocalDate.now(resultsProperties.zoneId()));
    }

    @Transactional
    public SnapshotSummary snapshotForDate(LocalDate snapshotDate) {
        Instant now = Instant.now();
        double daysAhead = daysAheadThroughEndOf(snapshotDate, now);
        log.info("Snapshot starting: snapshotDate={}, daysAhead={}, timezone={}",
                snapshotDate, String.format("%.3f", daysAhead), resultsProperties.getTimezone());

        List<Recommendation> recommendations = recommendationService.generateAllRecommendations(daysAhead);
        List<Recommendation> eligible = recommendations.stream()
                .filter(this::isActionableConfidence)
                .filter(r -> isKickoffOnDate(r.getMatchDateUnix(), snapshotDate))
                .toList();

        int inserted = 0;
        int updated = 0;
        int skipped = 0;

        for (Recommendation recommendation : eligible) {
            var existing = snapshotRepository.findBySnapshotDateAndFixtureIdAndType(
                    snapshotDate, recommendation.getFixtureId(), recommendation.getType().name());

            if (existing.isPresent()) {
                RecommendationSnapshot row = existing.get();
                if (row.getOutcome() != PickOutcome.PENDING) {
                    skipped++;
                    continue;
                }
                if (row.getMatchDateUnix() != null && row.getMatchDateUnix() <= now.getEpochSecond()) {
                    skipped++;
                    continue;
                }
                applyRecommendation(row, recommendation);
                snapshotRepository.save(row);
                updated++;
            } else {
                RecommendationSnapshot row = newSnapshot(snapshotDate, recommendation);
                snapshotRepository.save(row);
                inserted++;
            }
        }

        int eliteTagged = assignEliteRanks(snapshotDate);

        SnapshotSummary summary = new SnapshotSummary(snapshotDate, eligible.size(), inserted, updated, skipped);
        log.info("Snapshot completed: date={}, considered={}, inserted={}, updated={}, skipped={}, elite={}",
                snapshotDate, summary.considered(), summary.inserted(), summary.updated(), summary.skipped(),
                eliteTagged);
        return summary;
    }

    /**
     * UC-037: clear previous elite tags for the day, then assign ranks 1..N to Elite-of-day picks.
     */
    int assignEliteRanks(LocalDate snapshotDate) {
        List<RecommendationSnapshot> dayRows = snapshotRepository
                .findBySnapshotDateOrderByMatchDateUnixAscIdAsc(snapshotDate);

        boolean clearedAny = false;
        for (RecommendationSnapshot row : dayRows) {
            if (row.getEliteRank() != null) {
                row.setEliteRank(null);
                clearedAny = true;
            }
        }
        if (clearedAny) {
            snapshotRepository.saveAll(dayRows);
        }

        List<RecommendationSnapshot> elite = ElitePicksSelector.select(dayRows);
        for (int i = 0; i < elite.size(); i++) {
            elite.get(i).setEliteRank(i + 1);
        }
        if (!elite.isEmpty()) {
            snapshotRepository.saveAll(elite);
        }
        return elite.size();
    }

    private boolean isActionableConfidence(Recommendation recommendation) {
        ConfidenceLevel confidence = recommendation.getConfidence();
        return confidence == ConfidenceLevel.STRONG || confidence == ConfidenceLevel.MODERATE;
    }

    boolean isKickoffOnDate(Long matchDateUnix, LocalDate snapshotDate) {
        if (matchDateUnix == null) {
            return false;
        }
        LocalDate kickoffDate = Instant.ofEpochSecond(matchDateUnix)
                .atZone(resultsProperties.zoneId())
                .toLocalDate();
        return snapshotDate.equals(kickoffDate);
    }

    double daysAheadThroughEndOf(LocalDate snapshotDate, Instant now) {
        ZonedDateTime endOfDay = snapshotDate.plusDays(1).atStartOfDay(resultsProperties.zoneId());
        long seconds = ChronoUnit.SECONDS.between(now, endOfDay.toInstant());
        if (seconds <= 0) {
            return 0.01;
        }
        return seconds / 86_400.0;
    }

    private RecommendationSnapshot newSnapshot(LocalDate snapshotDate, Recommendation recommendation) {
        RecommendationSnapshot row = RecommendationSnapshot.builder()
                .snapshotDate(snapshotDate)
                .outcome(PickOutcome.PENDING)
                .build();
        applyRecommendation(row, recommendation);
        return row;
    }

    private void applyRecommendation(RecommendationSnapshot row, Recommendation recommendation) {
        row.setFixtureId(recommendation.getFixtureId());
        row.setHomeTeamId(recommendation.getHomeTeamId());
        row.setAwayTeamId(recommendation.getAwayTeamId());
        row.setHomeTeamName(recommendation.getHomeTeamName());
        row.setAwayTeamName(recommendation.getAwayTeamName());
        row.setMatchDateUnix(recommendation.getMatchDateUnix());
        row.setLeagueId(recommendation.getLeagueId());
        row.setLeagueName(recommendation.getLeagueName());
        row.setLeagueImage(recommendation.getLeagueImage());
        row.setType(recommendation.getType().name());
        row.setMarket(recommendation.getMarket());
        row.setConfidence(recommendation.getConfidence().name());
        row.setScore(recommendation.getScore());
        row.setOdds(recommendation.getOdds());
        row.setDescription(recommendation.getDescription());
        row.setFactorsJson(writeFactors(recommendation));
        row.setGeneratedAt(recommendation.getGeneratedAt() != null ? recommendation.getGeneratedAt() : Instant.now());
        row.setOutcome(PickOutcome.PENDING);
        row.setResolvedAt(null);
        row.setMatchResultJson(null);
    }

    private String writeFactors(Recommendation recommendation) {
        if (recommendation.getFactors() == null || recommendation.getFactors().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(recommendation.getFactors());
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize factors for fixtureId={}: {}", recommendation.getFixtureId(), e.getMessage());
            return null;
        }
    }
}
