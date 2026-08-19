package com.jcm.recommendations.soccer.core.recommendation;

import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public interface RecommendationEngine {

    RecommendationType getType();

    Optional<Recommendation> analyze(FixtureContext context);

    default List<Recommendation> analyzeAll(List<FixtureContext> contexts) {
        List<Recommendation> out = new ArrayList<>();
        for (FixtureContext context : contexts) {
            try {
                analyze(context).ifPresent(out::add);
            } catch (RuntimeException e) {
                org.slf4j.LoggerFactory.getLogger(getClass())
                        .warn("Skipping fixture for {}: {}", getType(), e.toString());
            }
        }
        out.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return out;
    }

    default boolean isApplicable(FixtureContext context) {
        return context.hasCompleteData();
    }
}
