package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import org.springframework.stereotype.Component;

/**
 * UC-039: dedicated Over 2.5 Goals board (never steps up to Over 3.5).
 */
@Component
public class Over25GoalsRecommendationEngine extends TotalGoalsOverRecommendationEngine {

    private static final LineSpec SPEC = new LineSpec(
            RecommendationType.OVER_25_GOALS,
            "Over 2.5 Goals",
            "over25Pct",
            "apiO25Potential",
            2.5,
            80.0,
            65.0,
            3.0,
            5.0,
            2.8,
            4.0
    );

    @Override
    protected LineSpec spec() {
        return SPEC;
    }

    @Override
    protected Double seasonOverPercentage(TeamSeasonStats stats) {
        return stats != null ? stats.getSeasonOver25PercentageOverall() : null;
    }

    @Override
    protected Double formOverPercentage(TeamRecentForm form) {
        return form != null ? form.getOver25PercentageOverall() : null;
    }

    @Override
    protected Double apiPotential(FixtureContext context) {
        if (!context.hasPotentials()) {
            return null;
        }
        return context.getPotentials().getO25Potential();
    }

    @Override
    protected Double oddsForMarket(FixtureContext context) {
        if (!context.hasOdds()) {
            return null;
        }
        return context.getOdds().getOddsFtOver25();
    }
}
