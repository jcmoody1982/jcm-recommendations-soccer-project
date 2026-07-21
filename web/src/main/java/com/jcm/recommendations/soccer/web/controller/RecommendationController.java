package com.jcm.recommendations.soccer.web.controller;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationService;
import com.jcm.recommendations.soccer.core.recommendation.RecommendationService.RecommendationSummary;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
@Slf4j
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping
    public ResponseEntity<List<Recommendation>> getAllRecommendations(
            @RequestParam(defaultValue = "7") int daysAhead) {
        log.info("GET /api/recommendations requested: daysAhead={}", daysAhead);
        List<Recommendation> recommendations = recommendationService.generateAllRecommendations(daysAhead);
        return ResponseEntity.ok(recommendations);
    }

    @GetMapping("/strong")
    public ResponseEntity<List<Recommendation>> getStrongRecommendations(
            @RequestParam(defaultValue = "7") int daysAhead) {
        log.info("GET /api/recommendations/strong requested: daysAhead={}", daysAhead);
        List<Recommendation> recommendations = recommendationService.getStrongRecommendations(daysAhead);
        return ResponseEntity.ok(recommendations);
    }

    @GetMapping("/fixture/{fixtureId}")
    public ResponseEntity<List<Recommendation>> getRecommendationsForFixture(
            @PathVariable Long fixtureId) {
        log.info("GET /api/recommendations/fixture/{} requested", fixtureId);
        List<Recommendation> recommendations = recommendationService.getRecommendationsForFixture(fixtureId);
        if (recommendations.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(recommendations);
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<Recommendation>> getRecommendationsByType(
            @PathVariable String type,
            @RequestParam(defaultValue = "7") int daysAhead) {
        log.info("GET /api/recommendations/type/{} requested: daysAhead={}", type, daysAhead);
        
        RecommendationType recommendationType;
        try {
            recommendationType = RecommendationType.valueOf(type.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid recommendation type: {}", type);
            return ResponseEntity.badRequest().build();
        }

        List<Recommendation> recommendations = recommendationService.getRecommendationsByType(
                recommendationType, daysAhead);
        return ResponseEntity.ok(recommendations);
    }

    @GetMapping("/btts")
    public ResponseEntity<List<Recommendation>> getBttsRecommendations(
            @RequestParam(defaultValue = "7") int daysAhead) {
        log.info("GET /api/recommendations/btts requested: daysAhead={}", daysAhead);
        List<Recommendation> recommendations = recommendationService.getRecommendationsByType(
                RecommendationType.BTTS, daysAhead);
        return ResponseEntity.ok(recommendations);
    }

    @GetMapping("/over-goals")
    public ResponseEntity<List<Recommendation>> getOverGoalsRecommendations(
            @RequestParam(defaultValue = "7") int daysAhead) {
        log.info("GET /api/recommendations/over-goals requested: daysAhead={}", daysAhead);
        List<Recommendation> recommendations = recommendationService.getRecommendationsByType(
                RecommendationType.OVER_GOALS, daysAhead);
        return ResponseEntity.ok(recommendations);
    }

    @GetMapping("/under-goals")
    public ResponseEntity<List<Recommendation>> getUnderGoalsRecommendations(
            @RequestParam(defaultValue = "7") int daysAhead) {
        log.info("GET /api/recommendations/under-goals requested: daysAhead={}", daysAhead);
        List<Recommendation> recommendations = recommendationService.getRecommendationsByType(
                RecommendationType.UNDER_GOALS, daysAhead);
        return ResponseEntity.ok(recommendations);
    }

    @GetMapping("/booking-points")
    public ResponseEntity<List<Recommendation>> getBookingPointsRecommendations(
            @RequestParam(defaultValue = "7") int daysAhead) {
        log.info("GET /api/recommendations/booking-points requested: daysAhead={}", daysAhead);
        List<Recommendation> recommendations = recommendationService.getRecommendationsByType(
                RecommendationType.BOOKING_POINTS, daysAhead);
        return ResponseEntity.ok(recommendations);
    }

    @GetMapping("/value-bets")
    public ResponseEntity<List<Recommendation>> getValueBetRecommendations(
            @RequestParam(defaultValue = "7") int daysAhead) {
        log.info("GET /api/recommendations/value-bets requested: daysAhead={}", daysAhead);
        List<Recommendation> recommendations = recommendationService.getRecommendationsByType(
                RecommendationType.VALUE_BET, daysAhead);
        return ResponseEntity.ok(recommendations);
    }

    @GetMapping("/corners")
    public ResponseEntity<List<Recommendation>> getCornersRecommendations(
            @RequestParam(defaultValue = "7") int daysAhead) {
        log.info("GET /api/recommendations/corners requested: daysAhead={}", daysAhead);
        List<Recommendation> overCorners = recommendationService.getRecommendationsByType(
                RecommendationType.OVER_CORNERS, daysAhead);
        List<Recommendation> underCorners = recommendationService.getRecommendationsByType(
                RecommendationType.UNDER_CORNERS, daysAhead);
        
        List<Recommendation> combined = new java.util.ArrayList<>(overCorners);
        combined.addAll(underCorners);
        combined.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        
        return ResponseEntity.ok(combined);
    }

    @GetMapping("/clean-sheets")
    public ResponseEntity<List<Recommendation>> getCleanSheetRecommendations(
            @RequestParam(defaultValue = "7") int daysAhead) {
        log.info("GET /api/recommendations/clean-sheets requested: daysAhead={}", daysAhead);
        List<Recommendation> recommendations = recommendationService.getRecommendationsByType(
                RecommendationType.CLEAN_SHEET, daysAhead);
        return ResponseEntity.ok(recommendations);
    }

    @GetMapping("/match-results")
    public ResponseEntity<List<Recommendation>> getMatchResultRecommendations(
            @RequestParam(defaultValue = "7") int daysAhead) {
        log.info("GET /api/recommendations/match-results requested: daysAhead={}", daysAhead);
        List<Recommendation> recommendations = recommendationService.getRecommendationsByType(
                RecommendationType.MATCH_RESULT, daysAhead);
        return ResponseEntity.ok(recommendations);
    }

    @GetMapping("/draws")
    public ResponseEntity<List<Recommendation>> getDrawRecommendations(
            @RequestParam(defaultValue = "7") int daysAhead) {
        log.info("GET /api/recommendations/draws requested: daysAhead={}", daysAhead);
        List<Recommendation> recommendations = recommendationService.getRecommendationsByType(
                RecommendationType.DRAW, daysAhead);
        return ResponseEntity.ok(recommendations);
    }

    @GetMapping("/grouped")
    public ResponseEntity<Map<RecommendationType, List<Recommendation>>> getRecommendationsGrouped(
            @RequestParam(defaultValue = "7") int daysAhead) {
        log.info("GET /api/recommendations/grouped requested: daysAhead={}", daysAhead);
        Map<RecommendationType, List<Recommendation>> grouped = 
                recommendationService.getRecommendationsGroupedByType(daysAhead);
        return ResponseEntity.ok(grouped);
    }

    @GetMapping("/summary")
    public ResponseEntity<RecommendationSummary> getSummary(
            @RequestParam(defaultValue = "7") int daysAhead) {
        log.info("GET /api/recommendations/summary requested: daysAhead={}", daysAhead);
        RecommendationSummary summary = recommendationService.getSummary(daysAhead);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/types")
    public ResponseEntity<RecommendationType[]> getAvailableTypes() {
        log.info("GET /api/recommendations/types requested");
        return ResponseEntity.ok(RecommendationType.values());
    }
}
