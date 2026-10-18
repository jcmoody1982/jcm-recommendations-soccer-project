package com.jcm.recommendations.soccer.core.recommendation;

import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;

import java.util.EnumSet;
import java.util.Set;

/**
 * Board-only markets: shown for information, never snapshotted, so they are never settled and
 * never reach Results, the performance tracker or Elite. Unlike {@link ShadowMarkets} they are
 * not graded anywhere.
 */
public final class InformationalMarkets {

    public static final Set<RecommendationType> TYPES = EnumSet.of(RecommendationType.DRAW_WATCH);

    private InformationalMarkets() {}

    public static boolean isInformational(RecommendationType type) {
        return type != null && TYPES.contains(type);
    }
}
