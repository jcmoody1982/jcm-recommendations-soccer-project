package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class TopVsBottomRecommendationEngine implements RecommendationEngine {

    private static final int MIN_POSITION_GAP = 8;
    private static final int STRONG_POSITION_GAP = 12;
    private static final int EXTREME_POSITION_GAP = 14;

    @Override
    public RecommendationType getType() {
        return RecommendationType.TOP_VS_BOTTOM;
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        if (homeStats.getPosition() == null || awayStats.getPosition() == null) {
            return Optional.empty();
        }

        int homePos = homeStats.getPosition();
        int awayPos = awayStats.getPosition();
        int positionGap = Math.abs(homePos - awayPos);

        if (positionGap < MIN_POSITION_GAP) {
            return Optional.empty();
        }

        log.debug("Analyzing Top vs Bottom for fixture: fixtureId={}, {} (pos {}) vs {} (pos {}), gap={}",
                context.getFixture().getId(),
                context.getHomeTeam().getName(), homePos,
                context.getAwayTeam().getName(), awayPos,
                positionGap);

        boolean homeIsFavorite = homePos < awayPos;
        String favoriteTeam = homeIsFavorite ? context.getHomeTeam().getName() : context.getAwayTeam().getName();
        String underdogTeam = homeIsFavorite ? context.getAwayTeam().getName() : context.getHomeTeam().getName();

        double qualityScore = calculateQualityScore(context, homeIsFavorite);
        ConfidenceLevel confidence = determineConfidence(positionGap, qualityScore, homeIsFavorite);

        if (confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        String market = buildMarket(favoriteTeam, positionGap, homeIsFavorite);
        String flag = determineFlag(positionGap, qualityScore, homeIsFavorite);

        Map<String, Object> factors = buildFactors(context, positionGap, qualityScore, homeIsFavorite, flag);

        Recommendation recommendation = Recommendation.builder()
                .fixtureId(context.getFixture().getId())
                .homeTeamId(context.getHomeTeam().getId())
                .awayTeamId(context.getAwayTeam().getId())
                .homeTeamName(context.getHomeTeam().getName())
                .awayTeamName(context.getAwayTeam().getName())
                .matchDateUnix(context.getFixture().getDateUnix())
                .leagueId(context.getLeague() != null ? context.getLeague().getCurrentSeasonId() : null)
                .leagueName(context.getLeague() != null ? context.getLeague().getName() : null)
                .leagueImage(context.getLeague() != null ? context.getLeague().getImage() : null)
                .type(RecommendationType.TOP_VS_BOTTOM)
                .confidence(confidence)
                .score(qualityScore)
                .market(market)
                .odds(null)
                .description(buildDescription(context, favoriteTeam, positionGap, confidence, flag))
                .factors(factors)
                .generatedAt(Instant.now())
                .build();

        log.info("Top vs Bottom recommendation: fixtureId={}, market={}, gap={}, quality={}, flag={}",
                context.getFixture().getId(), market, positionGap, 
                String.format("%.1f", qualityScore), flag);

        return Optional.of(recommendation);
    }

    private double calculateQualityScore(FixtureContext context, boolean homeIsFavorite) {
        TeamSeasonStats favoriteStats = homeIsFavorite ? context.getHomeTeamStats() : context.getAwayTeamStats();
        TeamSeasonStats underdogStats = homeIsFavorite ? context.getAwayTeamStats() : context.getHomeTeamStats();

        double ppgFavorite = homeIsFavorite ? 
                safeDouble(favoriteStats.getPpgHome()) : safeDouble(favoriteStats.getPpgAway());
        double ppgUnderdog = homeIsFavorite ? 
                safeDouble(underdogStats.getPpgAway()) : safeDouble(underdogStats.getPpgHome());

        double ppgDiff = ppgFavorite - ppgUnderdog;

        int gdFavorite = safeInt(favoriteStats.getSeasonGoalDifference());
        int gdUnderdog = safeInt(underdogStats.getSeasonGoalDifference());
        double gdDiff = gdFavorite - gdUnderdog;

        double ppgScore = Math.min(40.0, ppgDiff * 20);
        double gdScore = Math.min(30.0, Math.max(0, gdDiff * 1.5));

        int posDiff = Math.abs(favoriteStats.getPosition() - underdogStats.getPosition());
        double posScore = Math.min(30.0, posDiff * 2);

        return ppgScore + gdScore + posScore;
    }

    private ConfidenceLevel determineConfidence(int positionGap, double qualityScore, boolean favoriteAtHome) {
        if (positionGap >= EXTREME_POSITION_GAP && qualityScore >= 70 && favoriteAtHome) {
            return ConfidenceLevel.STRONG;
        }
        if (positionGap >= STRONG_POSITION_GAP && qualityScore >= 50) {
            return ConfidenceLevel.STRONG;
        }
        if (positionGap >= MIN_POSITION_GAP && qualityScore >= 30) {
            return ConfidenceLevel.MODERATE;
        }
        return ConfidenceLevel.WEAK;
    }

    private String buildMarket(String favoriteTeam, int positionGap, boolean favoriteAtHome) {
        if (positionGap >= EXTREME_POSITION_GAP && favoriteAtHome) {
            return favoriteTeam;
        }
        return favoriteTeam;
    }

    private String determineFlag(int positionGap, double qualityScore, boolean favoriteAtHome) {
        if (positionGap >= EXTREME_POSITION_GAP && qualityScore >= 70 && favoriteAtHome) {
            return "Banker";
        }
        if (positionGap >= STRONG_POSITION_GAP && qualityScore >= 60) {
            return "Strong Favorite";
        }
        if (!favoriteAtHome && positionGap >= MIN_POSITION_GAP) {
            return "Away Favorite";
        }
        return "Mismatch";
    }

    private Map<String, Object> buildFactors(FixtureContext context, int positionGap, 
            double qualityScore, boolean homeIsFavorite, String flag) {
        Map<String, Object> factors = new HashMap<>();
        
        factors.put("homePosition", context.getHomeTeamStats().getPosition());
        factors.put("awayPosition", context.getAwayTeamStats().getPosition());
        factors.put("positionGap", positionGap);
        factors.put("qualityScore", qualityScore);
        factors.put("homeIsFavorite", homeIsFavorite);
        factors.put("flag", flag);

        factors.put("homePpg", context.getHomeTeamStats().getPpgOverall());
        factors.put("awayPpg", context.getAwayTeamStats().getPpgOverall());
        factors.put("homeGd", context.getHomeTeamStats().getSeasonGoalDifference());
        factors.put("awayGd", context.getAwayTeamStats().getSeasonGoalDifference());

        return factors;
    }

    private String buildDescription(FixtureContext context, String favoriteTeam, int positionGap,
            ConfidenceLevel confidence, String flag) {
        return String.format("%s confidence %s recommendation (%d position gap, %s) - %s vs %s",
                confidence.getDisplayName(),
                favoriteTeam,
                positionGap,
                flag,
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private double safeDouble(Double value) {
        return value != null ? value : 0.0;
    }
}
