package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.domain.PlayerSeasonStats;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.safeDouble;

/**
 * UC-041: one player to assist pick per fixture from season per-90 rates.
 *
 * <p>Paused (no {@code @Component}): production grading showed ~14% STRONG hit rate with scores
 * claiming the mid-20s to mid-40s. Re-enable only after assist-specific calibration; to-score
 * carries the player-prop board until then.
 */
public class PlayerToAssistRecommendationEngine extends PlayerPropRecommendationEngine {

    /**
     * Floors raised while paused so an accidental re-register does not flood the board. Assists
     * remain rarer than goals; elite 0.40 per-90 over a full match is still only ~33% raw.
     */
    private static final PropSpec SPEC = new PropSpec(
            RecommendationType.PLAYER_TO_ASSIST,
            "to assist",
            0.20,
            0.40,
            0.12,
            38.0,
            30.0
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
