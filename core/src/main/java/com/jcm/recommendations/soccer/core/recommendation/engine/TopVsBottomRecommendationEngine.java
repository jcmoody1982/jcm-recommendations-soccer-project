package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.core.recommendation.util.RecommendationFactory;
import com.jcm.recommendations.soccer.core.recommendation.util.MatchBriefCopy;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.*;
import static com.jcm.recommendations.soccer.core.recommendation.util.SquadValueSupport.*;

/**
 * UC-026: Top vs Bottom — table mismatch picks backing the home favorite to win (match result
 * only), with UC form/GD filters and upset detection.
 */
@Component
@Slf4j
public class TopVsBottomRecommendationEngine implements RecommendationEngine {

    // Option A — tightened publishing floors
    private static final int MIN_GAP_MODERATE = 10;
    private static final int MIN_GAP_STRONG = 12;
    private static final int MIN_GAP_BANKER = 14;

    private static final double QUALITY_MODERATE = 50.0;
    private static final double QUALITY_STRONG = 65.0;
    private static final double QUALITY_BANKER = 70.0;

    // UC-026 quality filters (venue-specific)
    private static final double FAVORITE_MIN_PPG_STRONG = 1.8;
    private static final double FAVORITE_MIN_PPG_MODERATE = 1.5;
    private static final double UNDERDOG_MAX_PPG_STRONG = 1.0;
    private static final double UNDERDOG_MAX_PPG_MODERATE = 1.2;

    private static final int FAVORITE_MIN_GD_STRONG = 10;
    private static final int UNDERDOG_MAX_GD_STRONG = -5;

    private static final double UNDERDOG_POOR_AWAY_WIN_RATE = 20.0;
    private static final double AWAY_COMPETITIVE_WIN_RATE = 35.0;
    private static final double AWAY_IMPROVING_FORM_PPG = 1.5;

    private static final int UPSET_WATCH_FACTORS = 2;
    private static final int UPSET_SKIP_FACTORS = 3;

    private static final double UNDERDOG_SCORES_AWAY_PCT = 50.0;
    private static final int H2H_MIN_MEETINGS = 3;
    private static final double H2H_FAVORITE_SUPPORT_BOOST = 4.0;

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

        // Option A — home favorites only
        if (homePos >= awayPos) {
            log.debug("Skipping Top vs Bottom — home team is not table favorite: fixtureId={}, homePos={}, awayPos={}",
                    context.getFixture().getId(), homePos, awayPos);
            return Optional.empty();
        }

        int positionGap = awayPos - homePos;
        if (positionGap < MIN_GAP_MODERATE) {
            return Optional.empty();
        }

        int upsetFactors = countUpsetFactors(context);
        if (upsetFactors >= UPSET_SKIP_FACTORS) {
            log.debug("Skipping Top vs Bottom — too many upset factors: fixtureId={}, count={}",
                    context.getFixture().getId(), upsetFactors);
            return Optional.empty();
        }

        double qualityScore = calculateQualityScore(context);
        ConfidenceLevel confidence = determineConfidence(positionGap, qualityScore, upsetFactors);
        if (confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        if (!passesQualityFilters(homeStats, awayStats, confidence)) {
            return Optional.empty();
        }

        // The selection is only ever the favourite to win: the quality score grades the table
        // mismatch, so it speaks for the match result and nothing else.
        String homeTeam = context.getHomeTeam().getName();
        Double homeOdds = context.hasOdds() ? context.getOdds().getOddsFt1() : null;
        String flag = determineFlag(positionGap, qualityScore, upsetFactors);

        Map<String, Object> factors = buildFactors(context, positionGap, qualityScore, upsetFactors, flag);

        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(RecommendationType.TOP_VS_BOTTOM)
                .confidence(confidence)
                .score(qualityScore)
                .market(homeTeam)
                .odds(homeOdds)
                .description(buildDescription(context, homeTeam, positionGap, confidence, flag))
                .factors(factors)
                .build();

        log.info("Top vs Bottom recommendation: fixtureId={}, market={}, gap={}, quality={}, flag={}, upsetFactors={}",
                context.getFixture().getId(), homeTeam, positionGap,
                String.format("%.1f", qualityScore), flag, upsetFactors);

        return Optional.of(recommendation);
    }

    private double calculateQualityScore(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double ppgDiff = safeDouble(homeStats.getPpgHome()) - safeDouble(awayStats.getPpgAway());
        int gdDiff = safeInt(homeStats.getSeasonGoalDifference()) - safeInt(awayStats.getSeasonGoalDifference());
        int positionGap = awayStats.getPosition() - homeStats.getPosition();

        double ppgScore = Math.min(35.0, Math.max(0, ppgDiff * 18));
        double gdScore = Math.min(25.0, Math.max(0, gdDiff * 1.2));
        double posScore = Math.min(25.0, positionGap * 1.8);

        double formScore = 0.0;
        if (context.hasRecentForm()) {
            double formPpgDiff = safeDouble(context.getHomeTeamForm().getPpgHome())
                    - safeDouble(context.getAwayTeamForm().getPpgAway());
            formScore = Math.min(15.0, Math.max(0, formPpgDiff * 10));
        }

        double underdogBoost = 0.0;
        if (calculateWinPercentage(awayStats, false) < UNDERDOG_POOR_AWAY_WIN_RATE) {
            underdogBoost = 5.0;
        }

        double h2hBoost = 0.0;
        if (supportsFavoriteInH2h(context)) {
            h2hBoost = H2H_FAVORITE_SUPPORT_BOOST;
        }

        double squadValueBoost = qualityBoost(context);

        return clampScore(ppgScore + gdScore + posScore + formScore + underdogBoost + h2hBoost + squadValueBoost);
    }

    private boolean passesQualityFilters(TeamSeasonStats homeStats, TeamSeasonStats awayStats,
            ConfidenceLevel confidence) {
        boolean strong = confidence == ConfidenceLevel.STRONG;
        double favoriteMinPpg = strong ? FAVORITE_MIN_PPG_STRONG : FAVORITE_MIN_PPG_MODERATE;
        double underdogMaxPpg = strong ? UNDERDOG_MAX_PPG_STRONG : UNDERDOG_MAX_PPG_MODERATE;

        if (safeDouble(homeStats.getPpgHome()) < favoriteMinPpg) {
            return false;
        }
        if (safeDouble(awayStats.getPpgAway()) > underdogMaxPpg) {
            return false;
        }

        if (strong) {
            if (safeInt(homeStats.getSeasonGoalDifference()) < FAVORITE_MIN_GD_STRONG) {
                return false;
            }
            if (safeInt(awayStats.getSeasonGoalDifference()) > UNDERDOG_MAX_GD_STRONG) {
                return false;
            }
        }
        return true;
    }

    private int countUpsetFactors(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();
        int count = 0;

        if (context.hasRecentForm()) {
            TeamRecentForm homeForm = context.getHomeTeamForm();
            TeamRecentForm awayForm = context.getAwayTeamForm();
            if (safeDouble(awayForm.getPpgAway()) >= AWAY_IMPROVING_FORM_PPG) {
                count++;
            }
            if (safeInt(homeForm.getLossesHome()) >= 2) {
                count++;
            }
            if (safeInt(awayForm.getWinsAway()) >= 2) {
                count++;
            }
        }

        if (calculateWinPercentage(awayStats, false) >= AWAY_COMPETITIVE_WIN_RATE) {
            count++;
        }

        if (calculateVenueScoredPercentage(awayStats, false) >= UNDERDOG_SCORES_AWAY_PCT) {
            count++;
        }

        if (suggestsUpsetFromH2h(context)) {
            count++;
        }

        if (suggestsUpset(context)) {
            count++;
        }

        return count;
    }

    private boolean supportsFavoriteInH2h(FixtureContext context) {
        if (!context.hasHeadToHead()
                || context.getHeadToHead().getPreviousMeetings() < H2H_MIN_MEETINGS) {
            return false;
        }
        int homeWins = safeInt(context.getHeadToHead().getHomeWins());
        int awayWins = safeInt(context.getHeadToHead().getAwayWins());
        return homeWins > awayWins;
    }

    private boolean suggestsUpsetFromH2h(FixtureContext context) {
        if (!context.hasHeadToHead()
                || context.getHeadToHead().getPreviousMeetings() < H2H_MIN_MEETINGS) {
            return false;
        }
        int homeWins = safeInt(context.getHeadToHead().getHomeWins());
        int awayWins = safeInt(context.getHeadToHead().getAwayWins());
        return awayWins > homeWins;
    }

    private ConfidenceLevel determineConfidence(int positionGap, double qualityScore, int upsetFactors) {
        ConfidenceLevel level = ConfidenceLevel.WEAK;

        if (positionGap >= MIN_GAP_BANKER && qualityScore >= QUALITY_BANKER) {
            level = ConfidenceLevel.STRONG;
        } else if (positionGap >= MIN_GAP_STRONG && qualityScore >= QUALITY_STRONG) {
            level = ConfidenceLevel.STRONG;
        } else if (positionGap >= MIN_GAP_MODERATE && qualityScore >= QUALITY_MODERATE) {
            level = ConfidenceLevel.MODERATE;
        }

        if (level == ConfidenceLevel.WEAK) {
            return ConfidenceLevel.WEAK;
        }

        // Option A — Strong requires home favorite (always true here); cap when upset watch fires
        if (upsetFactors >= UPSET_WATCH_FACTORS && level == ConfidenceLevel.STRONG) {
            return ConfidenceLevel.MODERATE;
        }
        return level;
    }

    private String determineFlag(int positionGap, double qualityScore, int upsetFactors) {
        if (upsetFactors >= UPSET_WATCH_FACTORS) {
            return "Upset Watch";
        }
        if (positionGap >= MIN_GAP_BANKER && qualityScore >= QUALITY_BANKER) {
            return "Banker";
        }
        if (positionGap >= MIN_GAP_STRONG && qualityScore >= 60.0) {
            return "Strong Favorite";
        }
        return "Mismatch";
    }

    private Map<String, Object> buildFactors(FixtureContext context, int positionGap, double qualityScore,
            int upsetFactors, String flag) {
        Map<String, Object> factors = new HashMap<>();

        factors.put("homePosition", context.getHomeTeamStats().getPosition());
        factors.put("awayPosition", context.getAwayTeamStats().getPosition());
        factors.put("positionGap", positionGap);
        factors.put("qualityScore", qualityScore);
        factors.put("homeIsFavorite", true);
        factors.put("awayFavoritesPaused", true);
        factors.put("flag", flag);
        factors.put("upsetFactorCount", upsetFactors);
        factors.put("homePpg", context.getHomeTeamStats().getPpgOverall());
        factors.put("awayPpg", context.getAwayTeamStats().getPpgOverall());
        factors.put("homePpgHome", context.getHomeTeamStats().getPpgHome());
        factors.put("awayPpgAway", context.getAwayTeamStats().getPpgAway());
        factors.put("homeGd", context.getHomeTeamStats().getSeasonGoalDifference());
        factors.put("awayGd", context.getAwayTeamStats().getSeasonGoalDifference());
        factors.put("awayAwayWinPct", calculateWinPercentage(context.getAwayTeamStats(), false));
        factors.put("formDataAvailable", context.hasRecentForm());

        if (context.hasRecentForm()) {
            factors.put("homeFormPpgHome", context.getHomeTeamForm().getPpgHome());
            factors.put("awayFormPpgAway", context.getAwayTeamForm().getPpgAway());
        }

        if (context.hasHeadToHead()) {
            factors.put("h2hPreviousMeetings", context.getHeadToHead().getPreviousMeetings());
            factors.put("h2hHomeWins", context.getHeadToHead().getHomeWins());
            factors.put("h2hAwayWins", context.getHeadToHead().getAwayWins());
            factors.put("h2hSupportsFavorite", supportsFavoriteInH2h(context));
            factors.put("h2hSuggestsUpset", suggestsUpsetFromH2h(context));
        }

        putFactors(context, factors);

        return factors;
    }

    private String buildDescription(FixtureContext context, String market, int positionGap,
            ConfidenceLevel confidence, String flag) {
        String colour = String.format("%d places between them on the table, %s — clear gulf in standing",
                positionGap, flag);
        return MatchBriefCopy.narrate(MatchBriefCopy.Brief.builder()
                .confidence(confidence)
                .selection(market)
                .context(context)
                .colourNote(appendColourNote(colour, context))
                .build());
    }
}
