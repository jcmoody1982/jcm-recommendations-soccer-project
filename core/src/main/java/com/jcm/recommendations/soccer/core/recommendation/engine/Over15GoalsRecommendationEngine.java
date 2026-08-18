package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import org.springframework.stereotype.Component;

/**
 * UC-038: dedicated Over 1.5 Goals board.
 */
@Component
public class Over15GoalsRecommendationEngine extends TotalGoalsOverRecommendationEngine {

    private static final LineSpec SPEC = new LineSpec(
            RecommendationType.OVER_15_GOALS,
            "Over 1.5 Goals",
            "over15Pct",
            "apiO15Potential",
            1.8,
            62.0,
            78.0,
            65.0,
            2.4,
            5.0,
            2.2,
            4.0
    );

    @Override
    protected LineSpec spec() {
        return SPEC;
    }

    @Override
    protected Double seasonOverPercentage(TeamSeasonStats stats) {
        return stats != null ? stats.getSeasonOver15PercentageOverall() : null;
    }

    @Override
    protected Double formOverPercentage(TeamRecentForm form) {
        return form != null ? form.getOver15PercentageOverall() : null;
    }

    @Override
    protected Double apiPotential(FixtureContext context) {
        if (!context.hasPotentials()) {
            return null;
        }
        return context.getPotentials().getO15Potential();
    }

    @Override
    protected Double oddsForMarket(FixtureContext context) {
        if (!context.hasOdds()) {
            return null;
        }
        return context.getOdds().getOddsFtOver15();
    }
}
