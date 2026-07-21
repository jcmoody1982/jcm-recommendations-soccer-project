package com.jcm.recommendations.soccer.core.recommendation;

import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.core.service.FixtureService;
import com.jcm.recommendations.soccer.domain.Fixture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private final FixtureService fixtureService;
    private final FixtureContextBuilder contextBuilder;
    private final List<RecommendationEngine> engines;

    public List<Recommendation> generateAllRecommendations() {
        return generateAllRecommendations(7);
    }

    public List<Recommendation> generateAllRecommendations(int daysAhead) {
        log.info("Generating recommendations for fixtures: daysAhead={}", daysAhead);
        Instant startTime = Instant.now();

        List<Fixture> fixtures = fixtureService.getUpcomingFixtures(daysAhead);
        log.info("Found upcoming fixtures: count={}", fixtures.size());

        if (fixtures.isEmpty()) {
            log.info("No upcoming fixtures found, returning empty recommendations");
            return Collections.emptyList();
        }

        List<FixtureContext> contexts = contextBuilder.buildContextsForFixtures(fixtures);
        log.info("Built fixture contexts: complete={}", contexts.size());

        List<Recommendation> allRecommendations = new ArrayList<>();

        for (RecommendationEngine engine : engines) {
            try {
                log.debug("Running engine: type={}", engine.getType());
                List<Recommendation> engineRecs = engine.analyzeAll(contexts);
                allRecommendations.addAll(engineRecs);
                log.info("Engine completed: type={}, recommendations={}", engine.getType(), engineRecs.size());
            } catch (Exception e) {
                log.error("Engine failed: type={}, error={}", engine.getType(), e.getMessage(), e);
            }
        }

        allRecommendations.sort((a, b) -> {
            int confCompare = Integer.compare(b.getConfidence().getWeight(), a.getConfidence().getWeight());
            if (confCompare != 0) return confCompare;
            return Double.compare(b.getScore(), a.getScore());
        });

        Duration duration = Duration.between(startTime, Instant.now());
        log.info("Recommendations generated: total={}, duration={}ms, fixtures={}, engines={}",
                allRecommendations.size(), duration.toMillis(), fixtures.size(), engines.size());

        return allRecommendations;
    }

    public List<Recommendation> getRecommendationsForFixture(Long fixtureId) {
        log.info("Generating recommendations for fixture: fixtureId={}", fixtureId);

        Fixture fixture = fixtureService.getFixtureById(fixtureId);
        if (fixture == null) {
            log.warn("Fixture not found: fixtureId={}", fixtureId);
            return Collections.emptyList();
        }

        FixtureContext context = contextBuilder.buildContext(fixture);
        if (!context.hasCompleteData()) {
            log.warn("Fixture has incomplete data: fixtureId={}", fixtureId);
            return Collections.emptyList();
        }

        List<Recommendation> recommendations = new ArrayList<>();

        for (RecommendationEngine engine : engines) {
            try {
                engine.analyze(context).ifPresent(recommendations::add);
            } catch (Exception e) {
                log.error("Engine failed for fixture: type={}, fixtureId={}, error={}",
                        engine.getType(), fixtureId, e.getMessage(), e);
            }
        }

        recommendations.sort((a, b) -> {
            int confCompare = Integer.compare(b.getConfidence().getWeight(), a.getConfidence().getWeight());
            if (confCompare != 0) return confCompare;
            return Double.compare(b.getScore(), a.getScore());
        });

        log.info("Recommendations for fixture: fixtureId={}, count={}", fixtureId, recommendations.size());
        return recommendations;
    }

    public List<Recommendation> getRecommendationsByType(RecommendationType type) {
        return getRecommendationsByType(type, 7);
    }

    public List<Recommendation> getRecommendationsByType(RecommendationType type, int daysAhead) {
        log.info("Generating recommendations by type: type={}, daysAhead={}", type, daysAhead);

        List<Fixture> fixtures = fixtureService.getUpcomingFixtures(daysAhead);
        List<FixtureContext> contexts = contextBuilder.buildContextsForFixtures(fixtures);

        RecommendationEngine engine = engines.stream()
                .filter(e -> e.getType() == type)
                .findFirst()
                .orElse(null);

        if (engine == null) {
            log.warn("No engine found for type: type={}", type);
            return Collections.emptyList();
        }

        List<Recommendation> recommendations = engine.analyzeAll(contexts);
        log.info("Recommendations by type: type={}, count={}", type, recommendations.size());

        return recommendations;
    }

    public List<Recommendation> getStrongRecommendations() {
        return getStrongRecommendations(7);
    }

    public List<Recommendation> getStrongRecommendations(int daysAhead) {
        log.info("Getting strong recommendations: daysAhead={}", daysAhead);

        List<Recommendation> all = generateAllRecommendations(daysAhead);
        List<Recommendation> strong = all.stream()
                .filter(Recommendation::isStrong)
                .collect(Collectors.toList());

        log.info("Strong recommendations: count={}", strong.size());
        return strong;
    }

    public Map<RecommendationType, List<Recommendation>> getRecommendationsGroupedByType() {
        return getRecommendationsGroupedByType(7);
    }

    public Map<RecommendationType, List<Recommendation>> getRecommendationsGroupedByType(int daysAhead) {
        log.info("Generating grouped recommendations: daysAhead={}", daysAhead);

        List<Recommendation> all = generateAllRecommendations(daysAhead);

        Map<RecommendationType, List<Recommendation>> grouped = all.stream()
                .collect(Collectors.groupingBy(Recommendation::getType));

        log.info("Grouped recommendations: types={}, totalRecs={}",
                grouped.size(), all.size());

        return grouped;
    }

    public RecommendationSummary getSummary() {
        return getSummary(7);
    }

    public RecommendationSummary getSummary(int daysAhead) {
        log.info("Generating recommendation summary: daysAhead={}", daysAhead);

        List<Fixture> fixtures = fixtureService.getUpcomingFixtures(daysAhead);
        List<Recommendation> all = generateAllRecommendations(daysAhead);

        long strongCount = all.stream().filter(Recommendation::isStrong).count();
        long moderateCount = all.stream().filter(Recommendation::isModerate).count();

        Map<RecommendationType, Long> byType = all.stream()
                .collect(Collectors.groupingBy(Recommendation::getType, Collectors.counting()));

        return new RecommendationSummary(
                fixtures.size(),
                all.size(),
                strongCount,
                moderateCount,
                byType,
                Instant.now()
        );
    }

    public record RecommendationSummary(
            int fixturesAnalyzed,
            int totalRecommendations,
            long strongRecommendations,
            long moderateRecommendations,
            Map<RecommendationType, Long> recommendationsByType,
            Instant generatedAt
    ) {}
}
