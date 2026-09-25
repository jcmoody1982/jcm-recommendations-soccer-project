package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.domain.PlayerSeasonStats;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.safeDouble;

/**
 * UC-040: one player to score pick per fixture from season per-90 rates.
 *
 * <p>Paused after the 2026-09-11..23 window (~28% hit; high-score bands badly calibrated).
 */
@Component
public class PlayerToScoreRecommendationEngine extends PlayerPropRecommendationEngine {

    /** Flip when the prop model is ready to publish again. */
    static final boolean PAUSED = true;

    /**
     * Thresholds sit on the calibrated Poisson score (lambda already dampened). The old 33/25
     * pair labelled almost every pick STRONG while realised hit rates sat near 25%. 42/35 keeps
     * only the right tail of the post-calibration distribution.
     */
    private static final PropSpec SPEC = new PropSpec(
            RecommendationType.PLAYER_TO_SCORE,
            "to score",
            0.25,
            0.55,
            0.18,
            42.0,
            35.0
    );

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (PAUSED) {
            return Optional.empty();
        }
        return analyzeLive(context);
    }

    /** Live scoring path used by unit tests while {@link #PAUSED}. */
    Optional<Recommendation> analyzeLive(FixtureContext context) {
        return super.analyze(context);
    }

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
