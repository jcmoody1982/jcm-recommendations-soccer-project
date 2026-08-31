package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.domain.PlayerSeasonStats;
import org.springframework.stereotype.Component;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.safeDouble;

/**
 * UC-041: one player to assist pick per fixture from season per-90 rates.
 */
@Component
public class PlayerToAssistRecommendationEngine extends PlayerPropRecommendationEngine {

    /**
     * Assists are rarer than goals, so both the prior and the thresholds sit below the to-score
     * pair: an elite 0.40 assists-per-90 creator playing a full match is a 33% chance to assist.
     */
    private static final PropSpec SPEC = new PropSpec(
            RecommendationType.PLAYER_TO_ASSIST,
            "to assist",
            0.20,
            0.40,
            0.12,
            22.0,
            16.0
    );

    @Override
    protected PropSpec spec() {
        return SPEC;
    }

    @Override
    protected double per90(PlayerSeasonStats player, boolean isHome) {
        double overall = safeDouble(player.getAssistsPer90Overall());
        if (isHome) {
            return venuePer90(player.getAssistsHome(), player.getMinutesPlayedHome(), overall);
        }
        return venuePer90(player.getAssistsAway(), player.getMinutesPlayedAway(), overall);
    }
}
