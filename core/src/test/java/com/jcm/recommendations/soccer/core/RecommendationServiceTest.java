package com.jcm.recommendations.soccer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RecommendationServiceTest {

    @Test
    void moduleSummaryIncludesDomain() {
        RecommendationService service = new RecommendationService();
        assertEquals("core+domain", service.moduleSummary());
    }
}
