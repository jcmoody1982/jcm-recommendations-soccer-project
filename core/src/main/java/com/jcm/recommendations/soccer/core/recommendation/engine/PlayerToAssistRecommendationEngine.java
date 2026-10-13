package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.domain.PlayerSeasonStats;
import org.springframework.stereotype.Component;

/**
 * UC-041: one player to assist per fixture, in shadow mode (see {@link PlayerPropRecommendationEngine}).
 *
 * <p>Assists are rarer and noisier than goals, so the prior is lower (0.10 per 90) and so are the
 * tracking floor and intended publish bar. The 2026-08/09 model hit 10-15% on this market.
 */
@Component
public class PlayerToAssistRecommendationEngine extends PlayerPropRecommendationEngine {

    private static final PropSpec SPEC = new PropSpec(
            RecommendationType.PLAYER_TO_ASSIST,
            "to assist",
            0.10,
            15.0,
            25.0
    );

    @Override
    protected PropSpec spec() {
        return SPEC;
    }

    @Override
    protected int openPlayEvents(PlayerSeasonStats player) {
        return seasonCount(player.getAssistsOverall(), player.getAssistsPer90Overall(), player.getMinutesPlayedOverall());
    }

    @Override
    protected int penaltyEvents(PlayerSeasonStats player) {
        return 0;
    }
}
