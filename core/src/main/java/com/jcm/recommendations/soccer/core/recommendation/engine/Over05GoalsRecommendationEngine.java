package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.core.recommendation.util.RecommendationFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * UC-042: Over 0.5 Goals board.
 *
 * <p>Full-match Over 0.5 quotes are almost always 1.01–1.12 on FootyStats, so this board does not
 * use the Over 0.5 price. Instead it mirrors {@link MatchResultRecommendationEngine} tips whose
 * win price is longer than 2.00 ({@code > 2.00}), publishes them as
 * {@code "{team} Over 0.5 Goals"} with no price, and keeps the Match Result win-likelihood score
 * for ordering. The team name is the longer-priced Match Result side that selected the fixture;
 * settlement remains full-match Over 0.5.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class Over05GoalsRecommendationEngine implements RecommendationEngine {

    /** Exclusive floor on the Match Result win quote (evens = 2.00). */
    static final double MIN_MATCH_WIN_PRICE_EXCLUSIVE = 2.00;

    private static final String MARKET_SUFFIX = "Over 0.5 Goals";

    private final MatchResultRecommendationEngine matchResultEngine;

    @Override
    public RecommendationType getType() {
        return RecommendationType.OVER_05_GOALS;
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        Optional<Recommendation> matchResult = matchResultEngine.analyze(context);
        if (matchResult.isEmpty()) {
            return Optional.empty();
        }

        Recommendation source = matchResult.get();
        Double winOdds = source.getOdds();
        if (winOdds == null || winOdds <= MIN_MATCH_WIN_PRICE_EXCLUSIVE) {
            log.debug(
                    "Skipping Over 0.5 from Match Result: fixtureId={}, winOdds={} (need > {})",
                    context.getFixture().getId(),
                    winOdds,
                    MIN_MATCH_WIN_PRICE_EXCLUSIVE);
            return Optional.empty();
        }

        Map<String, Object> factors = new HashMap<>();
        if (source.getFactors() != null) {
            factors.putAll(source.getFactors());
        }
        String teamName = source.getMarket();
        String market = teamName + " " + MARKET_SUFFIX;

        factors.put("derivedFromMatchResult", true);
        factors.put("matchWinSelection", teamName);
        factors.put("matchWinOdds", winOdds);

        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(RecommendationType.OVER_05_GOALS)
                .confidence(source.getConfidence())
                .score(source.getScore())
                .market(market)
                .odds(null)
                .description(buildDescription(market, teamName, winOdds, source.getScore(),
                        source.getConfidence().name()))
                .factors(factors)
                .build();

        log.info(
                "Over 0.5 from Match Result: fixtureId={}, market={}, winOdds={}, winLikelihood={}",
                context.getFixture().getId(),
                market,
                winOdds,
                String.format("%.1f", source.getScore()));

        return Optional.of(recommendation);
    }

    private static String buildDescription(
            String market, String teamName, double winOdds, double score, String confidenceName) {
        String confidenceLabel = confidenceName.charAt(0) + confidenceName.substring(1).toLowerCase();
        return String.format(
                "%s %s — sourced from Match Result (%s @ %.2f, win likelihood %.0f%%). Price N/A.",
                confidenceLabel,
                market,
                teamName,
                winOdds,
                score);
    }
}
