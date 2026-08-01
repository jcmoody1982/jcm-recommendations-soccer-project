package com.jcm.recommendations.soccer.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import static org.assertj.core.api.Assertions.assertThat;

class CacheConfigTest {

    @Test
    void cacheManager_isCaffeineCacheManager() {
        CacheConfig cacheConfig = new CacheConfig();
        CacheManager cacheManager = cacheConfig.cacheManager();
        
        assertThat(cacheManager).isInstanceOf(CaffeineCacheManager.class);
    }

    @Test
    void cacheManager_hasExpectedCaches() {
        CacheConfig cacheConfig = new CacheConfig();
        CacheManager cacheManager = cacheConfig.cacheManager();
        
        assertThat(cacheManager.getCache(CacheConfig.RECOMMENDATIONS_CACHE)).isNotNull();
        assertThat(cacheManager.getCache(CacheConfig.FIXTURE_RECOMMENDATIONS_CACHE)).isNotNull();
        assertThat(cacheManager.getCache(CacheConfig.RECOMMENDATION_SUMMARY_CACHE)).isNotNull();
    }

    @Test
    void cacheConstants_haveCorrectValues() {
        assertThat(CacheConfig.RECOMMENDATIONS_CACHE).isEqualTo("recommendations");
        assertThat(CacheConfig.FIXTURE_RECOMMENDATIONS_CACHE).isEqualTo("fixtureRecommendations");
        assertThat(CacheConfig.RECOMMENDATION_SUMMARY_CACHE).isEqualTo("recommendationSummary");
    }
}
