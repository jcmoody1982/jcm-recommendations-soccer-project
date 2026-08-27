package com.jcm.recommendations.soccer.core.recommendation.util;

import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;

import java.time.Instant;
import java.util.Map;

/**
 * Factory for creating Recommendation objects with common fixture context fields pre-populated.
 * Reduces boilerplate across recommendation engines.
 */
public final class RecommendationFactory {

    private RecommendationFactory() {
    }

    /**
     * Creates a builder pre-populated with all fixture context fields.
     * Engines only need to set recommendation-specific fields (type, confidence, score, market, etc.)
     */
    public static Recommendation.RecommendationBuilder fromContext(FixtureContext context) {
        return Recommendation.builder()
                .fixtureId(context.getFixture().getId())
                .homeTeamId(context.getHomeTeam().getId())
                .awayTeamId(context.getAwayTeam().getId())
                .homeTeamName(context.getHomeTeam().getName())
                .awayTeamName(context.getAwayTeam().getName())
                .matchDateUnix(context.getFixture().getDateUnix())
                .leagueId(context.getLeague() != null ? context.getLeague().getCurrentSeasonId() : null)
                .leagueName(context.getLeague() != null ? context.getLeague().getName() : null)
                .leagueImage(context.getLeague() != null ? context.getLeague().getImage() : null)
                .generatedAt(Instant.now());
    }

    /**
     * Creates a complete recommendation with all required fields.
     * Use when all parameters are known upfront.
     */
    public static Recommendation create(
            FixtureContext context,
            RecommendationType type,
            ConfidenceLevel confidence,
            double score,
            String market,
            Double odds,
            String description,
            Map<String, Object> factors) {
        
        return fromContext(context)
                .type(type)
                .confidence(confidence)
                .score(score)
                .market(market)
                .odds(odds)
                .description(description)
                .factors(factors)
                .build();
    }

    /**
     * Helper to build a standard description format used by many engines.
     */
    public static String buildStandardDescription(
            ConfidenceLevel confidence,
            String marketOrType,
            double scoreOrProbability,
            String scoreLabel,
            FixtureContext context) {
        return MatchBriefCopy.narrate(MatchBriefCopy.Brief.builder()
                .confidence(confidence)
                .selection(marketOrType)
                .context(context)
                .probabilityPct(scoreOrProbability)
                .build());
    }

    /**
     * Helper to build a description with expected value (for goals, corners, etc.)
     */
    public static String buildExpectedValueDescription(
            ConfidenceLevel confidence,
            String market,
            double expectedValue,
            String valueLabel,
            FixtureContext context) {
        return MatchBriefCopy.narrate(MatchBriefCopy.Brief.builder()
                .confidence(confidence)
                .selection(market)
                .context(context)
                .expected(expectedValue, valueLabel)
                .build());
    }

    /**
     * Helper to build a description for team-specific recommendations.
     */
    public static String buildTeamDescription(
            ConfidenceLevel confidence,
            String recommendationType,
            String teamName,
            double score,
            FixtureContext context) {
        return MatchBriefCopy.narrate(MatchBriefCopy.Brief.builder()
                .confidence(confidence)
                .selection(recommendationType + " for " + teamName)
                .context(context)
                .probabilityPct(score)
                .build());
    }
}
