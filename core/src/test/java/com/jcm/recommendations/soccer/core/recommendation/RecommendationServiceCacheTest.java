package com.jcm.recommendations.soccer.core.recommendation;

import com.jcm.recommendations.soccer.core.config.CacheConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationServiceCacheTest {

    @Test
    void cacheConfig_hasCorrectCacheNames() {
        assertThat(CacheConfig.RECOMMENDATIONS_CACHE).isEqualTo("recommendations");
        assertThat(CacheConfig.FIXTURE_RECOMMENDATIONS_CACHE).isEqualTo("fixtureRecommendations");
        assertThat(CacheConfig.RECOMMENDATION_SUMMARY_CACHE).isEqualTo("recommendationSummary");
    }
}
