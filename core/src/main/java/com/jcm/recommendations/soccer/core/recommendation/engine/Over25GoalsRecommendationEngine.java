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
            78.0,
            62.0,
            3.0,
            5.0,
            2.8,
            4.0,
            2.5,
            8.0,
            12.0
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
        if (stats.getSeasonOver25PercentageOverall() != null) {
            return stats.getSeasonOver25PercentageOverall();
        }
        if (stats.getSeasonOver25Overall() != null
                && stats.getMatchesPlayed() != null
                && stats.getMatchesPlayed() > 0) {
            return 100.0 * stats.getSeasonOver25Overall() / stats.getMatchesPlayed();
        }
        return null;
    }

    @Override
    protected Double formOverPercentage(TeamRecentForm form) {
        if (form == null) {
            return null;
        }
        if (form.getOver25PercentageOverall() != null) {
            return form.getOver25PercentageOverall();
        }
        if (form.getOver25Overall() != null) {
            return 100.0 * form.getOver25Overall() / 5.0;
        }
        return null;
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
