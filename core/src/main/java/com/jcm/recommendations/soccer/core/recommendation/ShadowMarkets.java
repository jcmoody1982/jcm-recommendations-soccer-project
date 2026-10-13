package com.jcm.recommendations.soccer.core.recommendation;

import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Engines that run in shadow: their picks are snapshotted and graded every day, but never reach the
 * boards, Results, the performance tracker or Elite. Their record is read from the admin shadow
 * report until they are promoted.
 */
public final class ShadowMarkets {

    public static final Set<RecommendationType> TYPES = EnumSet.of(
            RecommendationType.PLAYER_TO_SCORE,
            RecommendationType.PLAYER_TO_ASSIST
    );

    private ShadowMarkets() {}

    public static boolean isShadow(RecommendationType type) {
        return type != null && TYPES.contains(type);
    }

    public static boolean isShadow(String typeName) {
        if (typeName == null) {
            return false;
        }
        String upper = typeName.toUpperCase(Locale.ROOT);
        return TYPES.stream().anyMatch(type -> type.name().equals(upper));
    }
}
