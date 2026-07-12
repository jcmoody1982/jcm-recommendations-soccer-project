package com.jcm.recommendations.soccer.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jcm.recommendations.soccer.core.RecommendationService;

@RestController
public class HealthController {

    private final RecommendationService recommendationService;

    public HealthController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/health")
    public String health() {
        return "ok:" + recommendationService.moduleSummary();
    }
}
