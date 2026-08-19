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
            72.0,
            58.0,
            2.4,
            5.0,
            2.2,
            4.0,
            1.5,
            12.0,
            18.0
    );

    @Override
    protected LineSpec spec() {
        return SPEC;
    }

    @Override
    protected Double seasonOverPercentage(TeamSeasonStats stats) {
        if (stats == null) {
            return null;
        }
        if (stats.getSeasonOver15PercentageOverall() != null) {
            return stats.getSeasonOver15PercentageOverall();
        }
        if (stats.getSeasonOver15Overall() != null
                && stats.getMatchesPlayed() != null
                && stats.getMatchesPlayed() > 0) {
            return 100.0 * stats.getSeasonOver15Overall() / stats.getMatchesPlayed();
        }
        return null;
    }

    @Override
    protected Double formOverPercentage(TeamRecentForm form) {
        if (form == null) {
            return null;
        }
        if (form.getOver15PercentageOverall() != null) {
            return form.getOver15PercentageOverall();
        }
        if (form.getOver15Overall() != null) {
            return 100.0 * form.getOver15Overall() / 5.0;
        }
        return null;
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
