package com.jcm.recommendations.soccer.core.results;

import com.jcm.recommendations.soccer.core.config.ResultsProperties;
import com.jcm.recommendations.soccer.core.recommendation.RecommendationService;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.repository.RecommendationSnapshotRepository;
import com.jcm.recommendations.soccer.domain.RecommendationSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Elite Picks for the boards, per matchday, from the same source Results grades. A day that has
 * been snapshotted uses its persisted ranks; a later day has none yet, so it is ranked from the live
 * picks with {@link ElitePicksSelector}. The site no longer selects Elite itself, which let the
 * board and Results disagree.
 */
@Service
@RequiredArgsConstructor
public class ElitePicksService {

    private final RecommendationService recommendationService;
    private final RecommendationSnapshotRepository snapshotRepository;
    private final ResultsProperties resultsProperties;

    public record ElitePick(LocalDate date, int rank, Long fixtureId, String type) {}

    public List<ElitePick> elitePicks(double daysAhead) {
        Map<LocalDate, List<Recommendation>> liveByDate = new TreeMap<>();
        for (Recommendation rec : recommendationService.generateAllRecommendations(daysAhead)) {
            if (rec.getMatchDateUnix() == null) {
                continue;
            }
            liveByDate.computeIfAbsent(kickoffDate(rec.getMatchDateUnix()), key -> new ArrayList<>()).add(rec);
        }

        List<ElitePick> picks = new ArrayList<>();
        for (Map.Entry<LocalDate, List<Recommendation>> day : liveByDate.entrySet()) {
            List<ElitePick> persisted = persistedElite(day.getKey());
            picks.addAll(persisted.isEmpty() ? liveElite(day.getKey(), day.getValue()) : persisted);
        }
        return picks;
    }

    private List<ElitePick> persistedElite(LocalDate date) {
        return snapshotRepository.findBySnapshotDateOrderByMatchDateUnixAscIdAsc(date).stream()
                .filter(row -> row.getEliteRank() != null)
                .filter(row -> !ElitePicksSelector.isExcludedFromElitePicks(row.getMarket()))
                .sorted(Comparator.comparing(RecommendationSnapshot::getEliteRank))
                .map(row -> new ElitePick(date, row.getEliteRank(), row.getFixtureId(), row.getType()))
                .toList();
    }

    private static List<ElitePick> liveElite(LocalDate date, List<Recommendation> recs) {
        List<RecommendationSnapshot> selected = ElitePicksSelector.select(recs.stream()
                .map(ElitePicksService::asSnapshot)
                .toList());
        List<ElitePick> picks = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            RecommendationSnapshot row = selected.get(i);
            picks.add(new ElitePick(date, i + 1, row.getFixtureId(), row.getType()));
        }
        return picks;
    }

    private static RecommendationSnapshot asSnapshot(Recommendation rec) {
        return RecommendationSnapshot.builder()
                .fixtureId(rec.getFixtureId())
                .type(rec.getType().name())
                .market(rec.getMarket())
                .confidence(rec.getConfidence().name())
                .score(rec.getScore())
                .odds(rec.getOdds())
                .matchDateUnix(rec.getMatchDateUnix())
                .build();
    }

    private LocalDate kickoffDate(long matchDateUnix) {
        return Instant.ofEpochSecond(matchDateUnix).atZone(resultsProperties.zoneId()).toLocalDate();
    }
}
