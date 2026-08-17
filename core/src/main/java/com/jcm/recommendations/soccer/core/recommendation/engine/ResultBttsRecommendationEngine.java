package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.core.recommendation.util.RecommendationFactory;
import com.jcm.recommendations.soccer.core.recommendation.util.VegasTipsterCopy;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.*;

/**
 * UC-025: Result + BTTS Combo Recommendations
 *
 * Identifies fixtures where a combined Result + Both Teams To Score bet has
 * strong probability, offering enhanced odds with good confidence.
 *
 * Combined Probability = Result Probability x BTTS Probability, then adjusted
 * for clean sheet and failed-to-score tendencies.
 *
 * Each market carries its own goals requirements: a side can only be backed to
 * win with BTTS if it both scores and concedes at a high enough rate, and the
 * market is excluded outright when the winner keeps too many clean sheets or the
 * opponent fails to score too often (both point to a win-to-nil instead).
 */
@Component
@Slf4j
public class ResultBttsRecommendationEngine implements RecommendationEngine {

    private static final double THRESHOLD_STRONG = 35.0;
    private static final double THRESHOLD_MODERATE = 28.0;

    private static final double HOME_WIN_MIN = 50.0;
    private static final double AWAY_WIN_MIN = 45.0;
    private static final double DRAW_MIN = 25.0;
    private static final double BTTS_MIN = 55.0;
    private static final double BTTS_MIN_DRAW = 60.0;

    // Per-market goals requirements
    private static final double HOME_SCORED_MIN = 1.3;
    private static final double HOME_CONCEDED_MIN = 0.8;
    private static final double AWAY_SCORED_MIN = 1.0;
    private static final double AWAY_CONCEDED_MIN = 0.7;
    private static final double DRAW_SCORED_MIN = 1.0;
    private static final double DRAW_PPG_DIFF_MAX = 0.4;

    // Exclusion criteria
    private static final double CLEAN_SHEET_EXCLUDE = 40.0;
    private static final double FAILED_TO_SCORE_EXCLUDE = 35.0;

    // Confidence adjustment thresholds
    private static final double CLEAN_SHEET_PENALTY_THRESHOLD = 35.0;
    private static final double CLEAN_SHEET_BONUS_THRESHOLD = 25.0;
    private static final double FAILED_TO_SCORE_PENALTY_THRESHOLD = 30.0;
    private static final double FORM_BTTS_BONUS_THRESHOLD = 60.0;

    // Confidence adjustment multipliers
    private static final double MULTIPLIER_FORM_BTTS_BONUS = 1.10;
    private static final double MULTIPLIER_BOTH_CONCEDE_BONUS = 1.05;
    private static final double MULTIPLIER_CLEAN_SHEET_PENALTY = 0.90;
    private static final double MULTIPLIER_FAILED_TO_SCORE_PENALTY = 0.95;

    @Override
    public RecommendationType getType() {
        return RecommendationType.RESULT_BTTS;
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        log.debug("Analyzing Result + BTTS for fixture: fixtureId={}, {} vs {}",
                context.getFixture().getId(),
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());

        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();
        boolean hasXgData = hasXgData(homeStats, awayStats);

        double bttsProb = calculateBttsProb(context, hasXgData);
        if (bttsProb < BTTS_MIN) {
            log.debug("BTTS probability {} below minimum {}, skipping fixture: {}",
                    String.format("%.1f", bttsProb), BTTS_MIN, context.getFixture().getId());
            return Optional.empty();
        }

        double homeWinProb = calculateWinProbability(context, true, hasXgData);
        double awayWinProb = calculateWinProbability(context, false, hasXgData);
        double drawProb = calculateDrawProbability(context);

        ConfidenceAdjustment adjustment = calculateConfidenceAdjustment(context);

        ResultBttsCandidate best = findBestCandidate(context, homeWinProb, drawProb, awayWinProb,
                bttsProb, adjustment.multiplier());

        if (best == null || best.adjustedProb < THRESHOLD_MODERATE) {
            return Optional.empty();
        }

        ConfidenceLevel confidence = best.adjustedProb >= THRESHOLD_STRONG
                ? ConfidenceLevel.STRONG : ConfidenceLevel.MODERATE;

        Map<String, Object> factors = buildFactors(context, homeWinProb, drawProb, awayWinProb,
                bttsProb, best, adjustment, hasXgData);

        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(RecommendationType.RESULT_BTTS)
                .confidence(confidence)
                .score(best.adjustedProb)
                .market(best.market)
                .odds(null)
                .description(buildDescription(context, best, confidence, adjustment))
                .factors(factors)
                .build();

        log.info("Result + BTTS recommendation: fixtureId={}, market={}, combined={}, adjusted={}, confidence={}",
                context.getFixture().getId(), best.market,
                String.format("%.1f", best.combinedProb),
                String.format("%.1f", best.adjustedProb), confidence);

        return Optional.of(recommendation);
    }

    private boolean hasXgData(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        return homeStats.getXgForAvgHome() != null && homeStats.getXgAgainstAvgHome() != null
                && awayStats.getXgForAvgAway() != null && awayStats.getXgAgainstAvgAway() != null;
    }

    private double calculateBttsProb(FixtureContext context, boolean hasXgData) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double homeBtts = safeDouble(homeStats.getSeasonBttsPercentageHome());
        double awayBtts = safeDouble(awayStats.getSeasonBttsPercentageAway());

        boolean hasApiPotential = context.hasPotentials()
                && context.getPotentials().getBttsPotential() != null;

        if (hasApiPotential && hasXgData) {
            double apiPotential = context.getPotentials().getBttsPotential();
            double xgBtts = calculateXgBttsIndicator(homeStats, awayStats);
            return (homeBtts * 0.28) + (awayBtts * 0.28) + (apiPotential * 0.24) + (xgBtts * 0.20);
        }

        if (hasApiPotential) {
            double apiPotential = context.getPotentials().getBttsPotential();
            return (homeBtts * 0.35) + (awayBtts * 0.35) + (apiPotential * 0.30);
        }

        if (hasXgData) {
            double xgBtts = calculateXgBttsIndicator(homeStats, awayStats);
            return (homeBtts * 0.40) + (awayBtts * 0.40) + (xgBtts * 0.20);
        }

        return (homeBtts + awayBtts) / 2;
    }

    /**
     * Both teams are likely to score when each side's expected goals combine well
     * with the opposing defence's expected goals conceded.
     */
    private double calculateXgBttsIndicator(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        double homeExpected = (safeDouble(homeStats.getXgForAvgHome())
                + safeDouble(awayStats.getXgAgainstAvgAway())) / 2.0;
        double awayExpected = (safeDouble(awayStats.getXgForAvgAway())
                + safeDouble(homeStats.getXgAgainstAvgHome())) / 2.0;

        // Both sides need to threaten, so use the weaker of the two expectations
        double limiting = Math.min(homeExpected, awayExpected);

        // ~1.2 expected goals for the weaker side maps to a high BTTS likelihood
        return clampScore((limiting / 1.2) * 100.0);
    }

    private double calculateWinProbability(FixtureContext context, boolean isHome, boolean hasXgData) {
        TeamSeasonStats stats = isHome ? context.getHomeTeamStats() : context.getAwayTeamStats();
        TeamSeasonStats opponentStats = isHome ? context.getAwayTeamStats() : context.getHomeTeamStats();

        if (stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 33.3;
        }

        double winPct = calculateWinPercentage(stats, isHome);
        double ppg = isHome ? safeDouble(stats.getPpgHome()) : safeDouble(stats.getPpgAway());
        double ppgBonus = Math.min(15.0, ppg * 5);

        double probability = winPct * 0.8 + ppgBonus;

        if (hasXgData) {
            probability = probability * 0.85 + calculateXgDominance(stats, opponentStats, isHome) * 0.15;
        }

        return probability;
    }

    /**
     * Expresses how far a team's expected goals outstrip the opponent's, on a
     * 0-100 scale where 50 is parity.
     */
    private double calculateXgDominance(TeamSeasonStats stats, TeamSeasonStats opponentStats, boolean isHome) {
        double xgFor = isHome ? safeDouble(stats.getXgForAvgHome()) : safeDouble(stats.getXgForAvgAway());
        double opponentXgFor = isHome
                ? safeDouble(opponentStats.getXgForAvgAway())
                : safeDouble(opponentStats.getXgForAvgHome());

        double diff = xgFor - opponentXgFor;

        // Each full goal of xG advantage moves the score 40 points from parity
        return clampScore(50.0 + (diff * 40.0));
    }

    private double calculateDrawProbability(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double homeDrawPct = calculateDrawPercentage(homeStats, true);
        double awayDrawPct = calculateDrawPercentage(awayStats, false);

        return (homeDrawPct + awayDrawPct) / 2;
    }

    /**
     * Applies the spec's confidence adjustments. The recent-form BTTS bonus stands
     * in for the specified head-to-head check, since head-to-head history is not
     * part of the fixture context.
     */
    private ConfidenceAdjustment calculateConfidenceAdjustment(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double multiplier = 1.0;
        List<String> applied = new ArrayList<>();

        double homeCleanSheetPct = calculateCleanSheetPercentage(homeStats, true);
        double awayCleanSheetPct = calculateCleanSheetPercentage(awayStats, false);
        double homeFtsPct = calculateFailedToScorePercentage(homeStats, true);
        double awayFtsPct = calculateFailedToScorePercentage(awayStats, false);

        if (context.hasRecentForm()) {
            TeamRecentForm homeForm = context.getHomeTeamForm();
            TeamRecentForm awayForm = context.getAwayTeamForm();
            double homeFormBtts = safeDouble(homeForm.getBttsPercentageHome());
            double awayFormBtts = safeDouble(awayForm.getBttsPercentageAway());

            if (homeFormBtts >= FORM_BTTS_BONUS_THRESHOLD && awayFormBtts >= FORM_BTTS_BONUS_THRESHOLD) {
                multiplier *= MULTIPLIER_FORM_BTTS_BONUS;
                applied.add("Both teams BTTS-heavy in recent form (+10%)");
            }
        }

        if (homeCleanSheetPct < CLEAN_SHEET_BONUS_THRESHOLD && awayCleanSheetPct < CLEAN_SHEET_BONUS_THRESHOLD) {
            multiplier *= MULTIPLIER_BOTH_CONCEDE_BONUS;
            applied.add("Both teams concede regularly (+5%)");
        }

        if (homeCleanSheetPct > CLEAN_SHEET_PENALTY_THRESHOLD || awayCleanSheetPct > CLEAN_SHEET_PENALTY_THRESHOLD) {
            multiplier *= MULTIPLIER_CLEAN_SHEET_PENALTY;
            applied.add("High clean sheet rate (-10%)");
        }

        if (homeFtsPct > FAILED_TO_SCORE_PENALTY_THRESHOLD || awayFtsPct > FAILED_TO_SCORE_PENALTY_THRESHOLD) {
            multiplier *= MULTIPLIER_FAILED_TO_SCORE_PENALTY;
            applied.add("Team fails to score too often (-5%)");
        }

        return new ConfidenceAdjustment(multiplier, applied);
    }

    private ResultBttsCandidate findBestCandidate(FixtureContext context, double homeWinProb,
            double drawProb, double awayWinProb, double bttsProb, double adjustmentMultiplier) {

        List<ResultBttsCandidate> candidates = new ArrayList<>();

        buildHomeCandidate(context, homeWinProb, bttsProb, adjustmentMultiplier).ifPresent(candidates::add);
        buildAwayCandidate(context, awayWinProb, bttsProb, adjustmentMultiplier).ifPresent(candidates::add);
        buildDrawCandidate(context, drawProb, bttsProb, adjustmentMultiplier).ifPresent(candidates::add);

        return candidates.stream()
                .max(Comparator.comparingDouble(ResultBttsCandidate::adjustedProb))
                .orElse(null);
    }

    private Optional<ResultBttsCandidate> buildHomeCandidate(FixtureContext context, double homeWinProb,
            double bttsProb, double adjustmentMultiplier) {
        if (homeWinProb < HOME_WIN_MIN) {
            return Optional.empty();
        }

        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double scoredAvg = calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 0.0);
        double concededAvg = calculateConcededAvg(homeStats, true);

        // The winner must score enough and leak enough for BTTS to land alongside the win
        if (scoredAvg < HOME_SCORED_MIN || concededAvg < HOME_CONCEDED_MIN) {
            return Optional.empty();
        }

        // A winner that shuts teams out, or an opponent that blanks often, points to a win to nil
        if (calculateCleanSheetPercentage(homeStats, true) > CLEAN_SHEET_EXCLUDE
                || calculateFailedToScorePercentage(awayStats, false) > FAILED_TO_SCORE_EXCLUDE) {
            return Optional.empty();
        }

        return Optional.of(createCandidate(context.getHomeTeam().getName() + " + BTTS", "HOME",
                homeWinProb, bttsProb, adjustmentMultiplier, scoredAvg, concededAvg));
    }

    private Optional<ResultBttsCandidate> buildAwayCandidate(FixtureContext context, double awayWinProb,
            double bttsProb, double adjustmentMultiplier) {
        if (awayWinProb < AWAY_WIN_MIN) {
            return Optional.empty();
        }

        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double scoredAvg = calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 0.0);
        double concededAvg = calculateConcededAvg(awayStats, false);

        if (scoredAvg < AWAY_SCORED_MIN || concededAvg < AWAY_CONCEDED_MIN) {
            return Optional.empty();
        }

        if (calculateCleanSheetPercentage(awayStats, false) > CLEAN_SHEET_EXCLUDE
                || calculateFailedToScorePercentage(homeStats, true) > FAILED_TO_SCORE_EXCLUDE) {
            return Optional.empty();
        }

        return Optional.of(createCandidate(context.getAwayTeam().getName() + " + BTTS", "AWAY",
                awayWinProb, bttsProb, adjustmentMultiplier, scoredAvg, concededAvg));
    }

    private Optional<ResultBttsCandidate> buildDrawCandidate(FixtureContext context, double drawProb,
            double bttsProb, double adjustmentMultiplier) {
        if (drawProb < DRAW_MIN || bttsProb < BTTS_MIN_DRAW) {
            return Optional.empty();
        }

        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double homeScoredAvg = calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 0.0);
        double awayScoredAvg = calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 0.0);

        // A score draw needs both sides scoring regularly
        if (homeScoredAvg < DRAW_SCORED_MIN || awayScoredAvg < DRAW_SCORED_MIN) {
            return Optional.empty();
        }

        double ppgDiff = Math.abs(safeDouble(homeStats.getPpgOverall()) - safeDouble(awayStats.getPpgOverall()));
        if (ppgDiff > DRAW_PPG_DIFF_MAX) {
            return Optional.empty();
        }

        return Optional.of(createCandidate("Draw + BTTS", "DRAW", drawProb, bttsProb,
                adjustmentMultiplier, (homeScoredAvg + awayScoredAvg) / 2.0,
                (calculateConcededAvg(homeStats, true) + calculateConcededAvg(awayStats, false)) / 2.0));
    }

    private ResultBttsCandidate createCandidate(String market, String resultType, double resultProb,
            double bttsProb, double adjustmentMultiplier, double scoredAvg, double concededAvg) {
        double combined = (resultProb / 100.0) * bttsProb;
        double adjusted = clampScore(combined * adjustmentMultiplier);
        return new ResultBttsCandidate(market, resultType, resultProb, bttsProb,
                combined, adjusted, scoredAvg, concededAvg);
    }

    private Map<String, Object> buildFactors(FixtureContext context, double homeWinProb, double drawProb,
            double awayWinProb, double bttsProb, ResultBttsCandidate best,
            ConfidenceAdjustment adjustment, boolean hasXgData) {
        Map<String, Object> factors = new HashMap<>();

        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        // Core probabilities
        factors.put("homeWinProbability", homeWinProb);
        factors.put("drawProbability", drawProb);
        factors.put("awayWinProbability", awayWinProb);
        factors.put("bttsProbability", bttsProb);
        factors.put("resultProbability", best.resultProb);
        factors.put("combinedProbability", best.combinedProb);
        factors.put("adjustedProbability", best.adjustedProb);
        factors.put("selectedResultType", best.resultType);

        // Goals data backing the market requirements
        double homeScoredAvg = calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 0.0);
        double awayScoredAvg = calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 0.0);
        factors.put("homeScoredAvg", homeScoredAvg);
        factors.put("awayScoredAvg", awayScoredAvg);
        factors.put("homeConcededAvg", calculateConcededAvg(homeStats, true));
        factors.put("awayConcededAvg", calculateConcededAvg(awayStats, false));

        // Exclusion-related rates
        double homeCleanSheetPct = calculateCleanSheetPercentage(homeStats, true);
        double awayCleanSheetPct = calculateCleanSheetPercentage(awayStats, false);
        double homeFtsPct = calculateFailedToScorePercentage(homeStats, true);
        double awayFtsPct = calculateFailedToScorePercentage(awayStats, false);
        factors.put("homeCleanSheetPct", homeCleanSheetPct);
        factors.put("awayCleanSheetPct", awayCleanSheetPct);
        factors.put("homeFailedToScorePct", homeFtsPct);
        factors.put("awayFailedToScorePct", awayFtsPct);

        // BTTS inputs
        factors.put("homeBttsPctSeason", safeDouble(homeStats.getSeasonBttsPercentageHome()));
        factors.put("awayBttsPctSeason", safeDouble(awayStats.getSeasonBttsPercentageAway()));
        if (context.hasPotentials() && context.getPotentials().getBttsPotential() != null) {
            factors.put("apiBttsPotential", context.getPotentials().getBttsPotential());
        }
        if (context.hasRecentForm()) {
            factors.put("homeBttsPctForm", safeDouble(context.getHomeTeamForm().getBttsPercentageHome()));
            factors.put("awayBttsPctForm", safeDouble(context.getAwayTeamForm().getBttsPercentageAway()));
        }

        // xG data
        factors.put("xgDataAvailable", hasXgData);
        if (hasXgData) {
            factors.put("homeXgFor", safeDouble(homeStats.getXgForAvgHome()));
            factors.put("homeXgAgainst", safeDouble(homeStats.getXgAgainstAvgHome()));
            factors.put("awayXgFor", safeDouble(awayStats.getXgForAvgAway()));
            factors.put("awayXgAgainst", safeDouble(awayStats.getXgAgainstAvgAway()));
            factors.put("xgBttsIndicator", calculateXgBttsIndicator(homeStats, awayStats));
        }

        // Confidence adjustments
        factors.put("confidenceAdjustmentMultiplier", adjustment.multiplier());
        factors.put("adjustmentsApplied", adjustment.applied());

        // Positive indicators and risk flags
        List<String> positiveIndicators = new ArrayList<>();
        List<String> riskFlags = new ArrayList<>();

        if (bttsProb >= 65.0) {
            positiveIndicators.add("Strong BTTS probability");
        }
        if (homeCleanSheetPct < CLEAN_SHEET_BONUS_THRESHOLD && awayCleanSheetPct < CLEAN_SHEET_BONUS_THRESHOLD) {
            positiveIndicators.add("Neither team keeps many clean sheets");
        }
        if (homeScoredAvg >= HOME_SCORED_MIN && awayScoredAvg >= AWAY_SCORED_MIN) {
            positiveIndicators.add("Both teams score at a healthy rate");
        }
        if (hasXgData && calculateXgBttsIndicator(homeStats, awayStats) >= 70.0) {
            positiveIndicators.add("xG profiles support both teams scoring");
        }
        if (adjustment.multiplier() > 1.0) {
            positiveIndicators.add("Net positive confidence adjustment");
        }

        if (!hasXgData) {
            riskFlags.add("No xG data available for validation");
        }
        if (homeCleanSheetPct > CLEAN_SHEET_PENALTY_THRESHOLD || awayCleanSheetPct > CLEAN_SHEET_PENALTY_THRESHOLD) {
            riskFlags.add("A team keeps clean sheets often - win to nil risk");
        }
        if (homeFtsPct > FAILED_TO_SCORE_PENALTY_THRESHOLD || awayFtsPct > FAILED_TO_SCORE_PENALTY_THRESHOLD) {
            riskFlags.add("A team fails to score often - BTTS at risk");
        }
        if (!context.hasRecentForm()) {
            riskFlags.add("No recent form data - head-to-head proxy unavailable");
        }

        factors.put("positiveIndicators", positiveIndicators);
        factors.put("riskFlags", riskFlags);

        return factors;
    }

    private String buildDescription(FixtureContext context, ResultBttsCandidate best,
            ConfidenceLevel confidence, ConfidenceAdjustment adjustment) {
        StringBuilder colour = new StringBuilder();
        colour.append(String.format("Result %.1f%% x BTTS %.1f%%", best.resultProb, best.bttsProb));
        if (Math.abs(adjustment.multiplier() - 1.0) > 0.001) {
            colour.append(String.format(" — adjusted to %.1f%%", best.adjustedProb));
        }
        if (!adjustment.applied().isEmpty()) {
            colour.append(". ").append(String.join("; ", adjustment.applied()));
        }

        return VegasTipsterCopy.narrate(VegasTipsterCopy.Brief.builder()
                .confidence(confidence)
                .selection(best.market)
                .context(context)
                .probabilityPct(best.combinedProb)
                .colourNote(colour.toString())
                .build());
    }

    private record ResultBttsCandidate(String market, String resultType, double resultProb,
                                       double bttsProb, double combinedProb, double adjustedProb,
                                       double scoredAvg, double concededAvg) {}

    private record ConfidenceAdjustment(double multiplier, List<String> applied) {}
}
