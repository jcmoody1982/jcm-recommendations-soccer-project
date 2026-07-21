package com.jcm.recommendations.soccer.core.recommendation;

import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;

import java.util.List;
import java.util.Optional;

public interface RecommendationEngine {

    RecommendationType getType();

    Optional<Recommendation> analyze(FixtureContext context);

    default List<Recommendation> analyzeAll(List<FixtureContext> contexts) {
        return contexts.stream()
                .map(this::analyze)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .toList();
    }

    default boolean isApplicable(FixtureContext context) {
        return context.hasCompleteData();
    }
}
