package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.domain.PlayerSeasonStats;
import org.springframework.stereotype.Component;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.safeInt;

/**
 * UC-040: one player to score per fixture, in shadow mode (see {@link PlayerPropRecommendationEngine}).
 *
 * <p>Non-penalty goals are shrunk toward 0.15 per 90; penalties are added on top. The tracking floor
 * sits low so the shadow report covers the whole score range; 35% is the intended publish bar and
 * is uncalibrated until the report has a few weeks of graded picks.
 */
@Component
public class PlayerToScoreRecommendationEngine extends PlayerPropRecommendationEngine {

    private static final PropSpec SPEC = new PropSpec(
            RecommendationType.PLAYER_TO_SCORE,
            "to score",
            0.15,
            20.0,
            35.0
    );

    @Override
    protected PropSpec spec() {
        return SPEC;
    }

    @Override
    protected int openPlayEvents(PlayerSeasonStats player) {
        int goals = seasonCount(player.getGoalsOverall(), player.getGoalsPer90Overall(), player.getMinutesPlayedOverall());
        return Math.max(0, goals - penaltyEvents(player));
    }

    @Override
    protected int penaltyEvents(PlayerSeasonStats player) {
        return safeInt(player.getPenaltyGoals());
    }
}
