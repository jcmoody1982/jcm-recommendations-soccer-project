package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.core.recommendation.util.RecommendationFactory;
import com.jcm.recommendations.soccer.core.recommendation.util.MatchBriefCopy;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.*;
import static com.jcm.recommendations.soccer.core.recommendation.util.SquadValueSupport.*;

/**
 * UC-026: Top vs Bottom — table mismatch picks for home favorites with UC form/GD filters,
 * upset detection, and short-odds goals/handicap pivots.
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

    private static final double SHORT_FAVORITE_ODDS = 1.40;
    private static final double UNDERDOG_POOR_AWAY_WIN_RATE = 20.0;
    private static final double AWAY_COMPETITIVE_WIN_RATE = 35.0;
    private static final double AWAY_IMPROVING_FORM_PPG = 1.5;

    private static final int UPSET_WATCH_FACTORS = 2;
    private static final int UPSET_SKIP_FACTORS = 3;

    private static final double BTTS_UNDERDOG_SCORED_PCT = 50.0;
    private static final double BTTS_FAVORITE_CONCEDES_PCT = 40.0;
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

        String homeTeam = context.getHomeTeam().getName();
        MarketSelection selection = selectPrimaryMarket(context, positionGap, qualityScore, upsetFactors, confidence);
        String flag = determineFlag(positionGap, qualityScore, upsetFactors, selection);

        List<String> alternativeMarkets = buildAlternativeMarkets(context, selection.market(), homeTeam);
        Map<String, Object> factors = buildFactors(
                context, positionGap, qualityScore, upsetFactors, flag, selection, alternativeMarkets);

        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(RecommendationType.TOP_VS_BOTTOM)
                .confidence(confidence)
                .score(qualityScore)
                .market(selection.market())
                .odds(selection.odds())
                .description(buildDescription(context, selection.market(), positionGap, confidence, flag))
                .factors(factors)
                .build();

        log.info("Top vs Bottom recommendation: fixtureId={}, market={}, gap={}, quality={}, flag={}, upsetFactors={}",
                context.getFixture().getId(), selection.market(), positionGap,
                String.format("%.1f", qualityScore), flag, upsetFactors);

        return Optional.of(recommendation);
    }

    private record MarketSelection(String market, Double odds) {}

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

        if (calculateVenueScoredPercentage(awayStats, false) >= BTTS_UNDERDOG_SCORED_PCT) {
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

    private MarketSelection selectPrimaryMarket(FixtureContext context, int positionGap, double qualityScore,
            int upsetFactors, ConfidenceLevel confidence) {
        String homeTeam = context.getHomeTeam().getName();
        Double homeOdds = context.hasOdds() ? context.getOdds().getOddsFt1() : null;

        if (upsetFactors >= UPSET_WATCH_FACTORS && confidence == ConfidenceLevel.MODERATE) {
            Double dcOdds = context.hasOdds() ? impliedDoubleChance1X(context) : null;
            return new MarketSelection("Home/Draw (1X)", dcOdds);
        }

        if (homeOdds != null && homeOdds < SHORT_FAVORITE_ODDS) {
            double expectedGoals = calculateVenueGoalsAvg(context.getHomeTeamStats(), true)
                    + calculateVenueConcededAvg(context.getAwayTeamStats(), false);
            if (expectedGoals >= 3.2) {
                return new MarketSelection("Over 3.5 Goals", context.getOdds().getOddsFtOver35());
            }
            if (expectedGoals >= 2.6) {
                return new MarketSelection("Over 2.5 Goals", context.getOdds().getOddsFtOver25());
            }
            if (positionGap >= MIN_GAP_STRONG && qualityScore >= QUALITY_STRONG) {
                return new MarketSelection(homeTeam + " -1.5", null);
            }
        }

        return new MarketSelection(homeTeam, homeOdds);
    }

    private Double impliedDoubleChance1X(FixtureContext context) {
        Double home = context.getOdds().getOddsFt1();
        Double draw = context.getOdds().getOddsFtX();
        if (home == null || draw == null || home <= 0 || draw <= 0) {
            return null;
        }
        double implied = (1.0 / home) + (1.0 / draw);
        if (implied <= 0) {
            return null;
        }
        return 1.0 / implied;
    }

    private List<String> buildAlternativeMarkets(FixtureContext context, String primaryMarket, String homeTeam) {
        List<String> alternatives = new ArrayList<>();
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        if (!primaryMarket.equals(homeTeam)) {
            alternatives.add(homeTeam);
        }

        if (context.hasOdds()) {
            Double homeOdds = context.getOdds().getOddsFt1();
            if (homeOdds != null && homeOdds < SHORT_FAVORITE_ODDS) {
                if (!primaryMarket.contains("Over 2.5")) {
                    alternatives.add("Over 2.5 Goals");
                }
                if (!primaryMarket.contains("-1.5")) {
                    alternatives.add(homeTeam + " -1.5");
                }
            }
        }

        if (suggestsBttsMismatch(homeStats, awayStats) && !primaryMarket.toLowerCase().contains("btts")) {
            alternatives.add("BTTS Yes");
        }

        return alternatives;
    }

    private boolean suggestsBttsMismatch(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        double awayScoredPct = calculateVenueScoredPercentage(awayStats, false);
        double homeConcededPct = 100.0 - calculateCleanSheetPercentage(homeStats, true);
        return awayScoredPct >= BTTS_UNDERDOG_SCORED_PCT && homeConcededPct >= BTTS_FAVORITE_CONCEDES_PCT;
    }

    private String determineFlag(int positionGap, double qualityScore, int upsetFactors, MarketSelection selection) {
        if (upsetFactors >= UPSET_WATCH_FACTORS) {
            return "Upset Watch";
        }
        if (selection.market().contains("Over")) {
            return "Goals Expected";
        }
        if (selection.market().contains("-1.5")) {
            return "Handicap";
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
            int upsetFactors, String flag, MarketSelection selection, List<String> alternativeMarkets) {
        Map<String, Object> factors = new HashMap<>();

        factors.put("homePosition", context.getHomeTeamStats().getPosition());
        factors.put("awayPosition", context.getAwayTeamStats().getPosition());
        factors.put("positionGap", positionGap);
        factors.put("qualityScore", qualityScore);
        factors.put("homeIsFavorite", true);
        factors.put("awayFavoritesPaused", true);
        factors.put("flag", flag);
        factors.put("upsetFactorCount", upsetFactors);
        factors.put("primaryMarketType", primaryMarketType(selection.market()));
        factors.put("alternativeMarkets", alternativeMarkets);

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

        if (suggestsBttsMismatch(context.getHomeTeamStats(), context.getAwayTeamStats())) {
            factors.put("bttsMismatchSuggested", true);
        }

        return factors;
    }

    private static String primaryMarketType(String market) {
        String lower = market.toLowerCase();
        if (lower.contains("over") && lower.contains("goals")) {
            return "GOALS_LINE";
        }
        if (lower.contains("-1.5")) {
            return "HANDICAP";
        }
        if (lower.contains("1x") || lower.contains("home/draw")) {
            return "UPSET_DOUBLE_CHANCE";
        }
        return "FAVORITE_WIN";
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
