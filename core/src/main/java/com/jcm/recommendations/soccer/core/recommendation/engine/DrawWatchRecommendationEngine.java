package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.InformationalMarkets;
import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.engine.DrawRecommendationEngine.DrawAssessment;
import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.core.recommendation.util.RecommendationFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Draw Watch: fixtures that read as level on every measure the Draw board uses, but where the
 * draw price does not cover our estimate (or disagrees with it implausibly), so no Draw pick is
 * published. Draw picks are rare, often none in a week, and this keeps the level matches visible.
 *
 * <p>Information only. Listed in {@link InformationalMarkets}, so these are never snapshotted or
 * settled and never count towards Results, Elite or the confidence tallies. Fixtures that do clear
 * the price appear on the Draw board instead, never on both.
 */
@Component
@RequiredArgsConstructor
public class DrawWatchRecommendationEngine implements RecommendationEngine {

    private final DrawRecommendationEngine drawEngine;

    @Override
    public RecommendationType getType() {
        return RecommendationType.DRAW_WATCH;
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        DrawAssessment assessment = drawEngine.assess(context);
        if (assessment == null
                || !assessment.unmetLevelGates().isEmpty()
                || assessment.unmetPriceGates().isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> factors = drawEngine.buildFactors(context, assessment);
        factors.put("watchReason", String.join("; ", assessment.unmetPriceGates()));
        factors.put("informationOnly", true);

        return Optional.of(RecommendationFactory.fromContext(context)
                .type(RecommendationType.DRAW_WATCH)
                .confidence(ConfidenceLevel.WEAK)
                .score(assessment.blendedDrawPct())
                .market("Draw")
                .odds(assessment.drawOdds())
                .description(describe(assessment))
                .factors(factors)
                .build());
    }

    private String describe(DrawAssessment assessment) {
        double fairOdds = 100.0 / assessment.modelDrawPct();
        String verdict = assessment.edgeAtPrice() < 0
                ? String.format("the draw at %.2f is shorter than our price of %.2f", assessment.drawOdds(), fairOdds)
                : String.format("our %.0f%% read is too far above the market's %.0f%% to trust",
                        assessment.modelDrawPct(), assessment.marketDrawPct());
        return String.format(
                "Level on paper (%.2f v %.2f expected goals), but %s. One to watch, not a tip, "
                        + "and not tracked in Results.",
                assessment.expectation().home(), assessment.expectation().away(), verdict);
    }
}
