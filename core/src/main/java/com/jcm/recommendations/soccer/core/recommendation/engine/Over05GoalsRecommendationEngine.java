package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import org.springframework.stereotype.Component;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.calculateCleanSheetPercentageOverall;
import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.calculateFailedToScorePercentageOverall;
import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.safeInt;

/**
 * UC-042: dedicated Over 0.5 Goals board. Full-match only, and only when the price is
 * genuinely backable — short 1.01–1.10 quotes are the league-average outcome dressed as a pick.
 */
@Component
public class Over05GoalsRecommendationEngine extends TotalGoalsOverRecommendationEngine {

    static final double MIN_PRICE_EXCLUSIVE = 1.20;

    /**
     * Over 0.5 clears in around 90% of matches, so the thresholds sit high and the price gate
     * does most of the thinning. A 1.20+ quote already implies the market is less sure than
     * the typical lock, which is the only Over 0.5 worth putting on a board.
     */
    private static final LineSpec SPEC = new LineSpec(
            RecommendationType.OVER_05_GOALS,
            "Over 0.5 Goals",
            "over05Pct",
            "apiO05Potential",
            1.0,
            80.0,
            72.0,
            0.5
    );

    @Override
    protected LineSpec spec() {
        return SPEC;
    }

    /**
     * P(the match has a goal) ≈ 1 − P(0-0). A team's 0-0 rate is approximated as
     * failed-to-score × clean-sheet, treating those as independent.
     */
    @Override
    protected Double seasonOverPercentage(TeamSeasonStats stats) {
        if (stats == null) {
            return null;
        }
        double failedToScore = calculateFailedToScorePercentageOverall(stats) / 100.0;
        double cleanSheet = calculateCleanSheetPercentageOverall(stats) / 100.0;
        return 100.0 * (1.0 - (failedToScore * cleanSheet));
    }

    @Override
    protected Double formOverPercentage(TeamRecentForm form) {
        if (form == null || form.getFailedToScoreOverall() == null || form.getCleanSheetsOverall() == null) {
            return null;
        }
        double failedToScore = safeInt(form.getFailedToScoreOverall()) / 5.0;
        double cleanSheet = safeInt(form.getCleanSheetsOverall()) / 5.0;
        return 100.0 * (1.0 - (failedToScore * cleanSheet));
    }

    @Override
    protected Double apiPotential(FixtureContext context) {
        return null;
    }

    @Override
    protected Double oddsForMarket(FixtureContext context) {
        if (!context.hasOdds()) {
            return null;
        }
        return context.getOdds().getOddsFtOver05();
    }

    @Override
    protected boolean passesOddsGate(Double odds) {
        return odds != null && odds > MIN_PRICE_EXCLUSIVE;
    }
}
