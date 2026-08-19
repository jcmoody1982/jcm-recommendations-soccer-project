package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.domain.PlayerSeasonStats;
import org.springframework.stereotype.Component;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.safeDouble;

/**
 * UC-040: one player to score pick per fixture from season per-90 rates.
 */
@Component
public class PlayerToScoreRecommendationEngine extends PlayerPropRecommendationEngine {

    private static final PropSpec SPEC = new PropSpec(
            RecommendationType.PLAYER_TO_SCORE,
            "to score",
            0.25,
            0.55
    );

    @Override
    protected PropSpec spec() {
        return SPEC;
    }

    @Override
    protected double per90(PlayerSeasonStats player, boolean isHome) {
        double overall = safeDouble(player.getGoalsPer90Overall());
        if (isHome) {
            if (player.getGoalsPer90Home() != null) {
                return player.getGoalsPer90Home();
            }
            return venuePer90(player.getGoalsHome(), player.getMinutesPlayedHome(), overall);
        }
        if (player.getGoalsPer90Away() != null) {
            return player.getGoalsPer90Away();
        }
        return venuePer90(player.getGoalsAway(), player.getMinutesPlayedAway(), overall);
    }
}
