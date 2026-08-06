package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.core.recommendation.util.RecommendationFactory;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.*;

/**
 * UC-017: Match Result Recommendations
 * 
 * Predicts 1X2 match outcomes (Home Win, Draw, Away Win) using:
 * - Team win/loss percentages (season and form)
 * - PPG analysis (home/away specific)
 * - xG comparison (team xG vs opponent xGA)
 * - Form momentum (streak detection)
 * - League position gap factor
 * - Home advantage factor
 * - Motivation factor (title race, relegation battle)
 */
@Component
@Slf4j
public class MatchResultRecommendationEngine implements RecommendationEngine {

    // Base weights when xG IS available (total = 1.0)
    private static final double WEIGHT_WIN_PCT_SEASON = 0.15;
    private static final double WEIGHT_WIN_PCT_FORM = 0.15;
    private static final double WEIGHT_LOSS_PCT_SEASON = 0.10;
    private static final double WEIGHT_LOSS_PCT_FORM = 0.10;
    private static final double WEIGHT_PPG = 0.10;
    private static final double WEIGHT_PPG_INVERSE = 0.10;
    private static final double WEIGHT_XG_COMPARISON = 0.15;
    private static final double WEIGHT_GOAL_DIFF = 0.10;
    private static final double WEIGHT_IMPLIED_ODDS = 0.05;

    // Redistributed weights when xG is NOT available
    private static final double WEIGHT_WIN_PCT_SEASON_NO_XG = 0.18;
    private static final double WEIGHT_WIN_PCT_FORM_NO_XG = 0.18;
    private static final double WEIGHT_LOSS_PCT_SEASON_NO_XG = 0.12;
    private static final double WEIGHT_LOSS_PCT_FORM_NO_XG = 0.12;
    private static final double WEIGHT_PPG_NO_XG = 0.12;
    private static final double WEIGHT_PPG_INVERSE_NO_XG = 0.10;
    private static final double WEIGHT_GOAL_DIFF_NO_XG = 0.12;
    private static final double WEIGHT_IMPLIED_ODDS_NO_XG = 0.06;

    // xG Dominance thresholds and multipliers
    private static final double XG_STRONG_DOMINANCE_THRESHOLD = 0.5;
    private static final double XG_STRONG_DOMINANCE_MULTIPLIER = 1.25;
    private static final double XG_MODERATE_DOMINANCE_THRESHOLD = 0.2;
    private static final double XG_MODERATE_DOMINANCE_MULTIPLIER = 1.10;
    private static final double XG_DISADVANTAGE_THRESHOLD = -0.2;
    private static final double XG_DISADVANTAGE_MULTIPLIER = 0.85;

    // Form Momentum thresholds and multipliers
    private static final double FORM_HOT_STREAK_MULTIPLIER = 1.20;      // W-W-W-W-W or W-W-W-W-D
    private static final double FORM_GOOD_MULTIPLIER = 1.10;            // 3+ wins in last 5
    private static final double FORM_POOR_MULTIPLIER = 0.85;            // 3+ losses in last 5
    private static final double FORM_CRISIS_MULTIPLIER = 0.70;          // L-L-L-L-L

    // Home advantage factor
    private static final double HOME_ADVANTAGE_BOOST = 0.08;            // 8% probability boost for home team

    // Position gap factors
    private static final double POSITION_GAP_LARGE_MULTIPLIER = 1.20;   // Gap >= 10
    private static final double POSITION_GAP_MEDIUM_MULTIPLIER = 1.10;  // Gap 6-9
    private static final double POSITION_GAP_SMALL_MULTIPLIER = 1.05;   // Gap 3-5

    // Motivation factors
    private static final double MOTIVATION_TITLE_MULTIPLIER = 1.15;     // Position 1-2
    private static final double MOTIVATION_EUROPE_MULTIPLIER = 1.10;    // Position 3-5
    private static final double MOTIVATION_RELEGATION_MULTIPLIER = 1.15; // Position >= 17

    // Draw probability bounds
    private static final double DRAW_MIN_PROBABILITY = 15.0;
    private static final double DRAW_MAX_PROBABILITY = 35.0;

    // Thresholds
    private static final double THRESHOLD_STRONG = 55.0;
    private static final double THRESHOLD_MODERATE = 45.0;
    private static final double VALUE_THRESHOLD = 5.0;

    @Override
    public RecommendationType getType() {
        return RecommendationType.MATCH_RESULT;
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        log.debug("Analyzing Match Result for fixture: fixtureId={}, {} vs {}", 
                context.getFixture().getId(),
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());

        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();
        boolean hasXgData = hasXgData(homeStats, awayStats);

        // Calculate base probabilities
        double homeWinProb = calculateHomeWinProbability(context, hasXgData);
        double awayWinProb = calculateAwayWinProbability(context, hasXgData);
        
        // Calculate draw probability with bounds
        double drawProb = 100.0 - homeWinProb - awayWinProb;
        drawProb = Math.max(DRAW_MIN_PROBABILITY, Math.min(DRAW_MAX_PROBABILITY, drawProb));
        
        // Adjust home/away to account for bounded draw
        double adjustment = (100.0 - homeWinProb - awayWinProb - drawProb) / 2.0;
        homeWinProb -= adjustment;
        awayWinProb -= adjustment;

        // Apply xG dominance multiplier
        if (hasXgData) {
            double homeXgDominance = calculateXgDominance(homeStats, awayStats, true);
            double awayXgDominance = calculateXgDominance(awayStats, homeStats, false);
            homeWinProb *= getXgDominanceMultiplier(homeXgDominance);
            awayWinProb *= getXgDominanceMultiplier(awayXgDominance);
        }

        // Apply form momentum multiplier
        double homeFormMomentum = calculateFormMomentum(context.getHomeTeamForm(), true);
        double awayFormMomentum = calculateFormMomentum(context.getAwayTeamForm(), false);
        homeWinProb *= homeFormMomentum;
        awayWinProb *= awayFormMomentum;

        // Apply home advantage
        homeWinProb += HOME_ADVANTAGE_BOOST * 100;
        awayWinProb -= (HOME_ADVANTAGE_BOOST / 2) * 100;

        // Apply position factor
        homeWinProb = applyPositionFactor(homeWinProb, context, true);
        awayWinProb = applyPositionFactor(awayWinProb, context, false);

        // Apply motivation factor
        homeWinProb = applyMotivationFactor(homeWinProb, context, true);
        awayWinProb = applyMotivationFactor(awayWinProb, context, false);

        // Normalize probabilities to sum to 100%
        double total = homeWinProb + awayWinProb + drawProb;
        homeWinProb = (homeWinProb / total) * 100;
        awayWinProb = (awayWinProb / total) * 100;
        drawProb = (drawProb / total) * 100;

        // Determine best outcome
        String outcomeType;
        String recommendedOutcome;
        double bestProb;
        double valueVsOdds = 0.0;
        Double odds = null;

        if (homeWinProb >= awayWinProb && homeWinProb >= drawProb) {
            outcomeType = "HOME";
            recommendedOutcome = context.getHomeTeam().getName();
            bestProb = homeWinProb;
            if (context.hasOdds() && context.getOdds().getOddsFt1() != null && context.getOdds().getOddsFt1() > 0) {
                double implied = (1.0 / context.getOdds().getOddsFt1()) * 100;
                valueVsOdds = homeWinProb - implied;
                odds = context.getOdds().getOddsFt1();
            }
        } else if (awayWinProb >= homeWinProb && awayWinProb >= drawProb) {
            outcomeType = "AWAY";
            recommendedOutcome = context.getAwayTeam().getName();
            bestProb = awayWinProb;
            if (context.hasOdds() && context.getOdds().getOddsFt2() != null && context.getOdds().getOddsFt2() > 0) {
                double implied = (1.0 / context.getOdds().getOddsFt2()) * 100;
                valueVsOdds = awayWinProb - implied;
                odds = context.getOdds().getOddsFt2();
            }
        } else {
            outcomeType = "DRAW";
            recommendedOutcome = "Draw";
            bestProb = drawProb;
            if (context.hasOdds() && context.getOdds().getOddsFtX() != null && context.getOdds().getOddsFtX() > 0) {
                double implied = (1.0 / context.getOdds().getOddsFtX()) * 100;
                valueVsOdds = drawProb - implied;
                odds = context.getOdds().getOddsFtX();
            }
        }

        ConfidenceLevel confidence = determineConfidence(bestProb, valueVsOdds);

        if (confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        Map<String, Object> factors = buildFactors(context, homeWinProb, drawProb, awayWinProb, 
                valueVsOdds, hasXgData, homeFormMomentum, awayFormMomentum);

        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(RecommendationType.MATCH_RESULT)
                .confidence(confidence)
                .score(bestProb)
                .market(recommendedOutcome)
                .odds(odds)
                .description(buildDescription(context, recommendedOutcome, bestProb, confidence, 
                        valueVsOdds, outcomeType, homeFormMomentum, awayFormMomentum))
                .factors(factors)
                .build();

        log.info("Match Result recommendation generated: fixtureId={}, outcome={}, probability={}, value={}, confidence={}", 
                context.getFixture().getId(), recommendedOutcome, 
                String.format("%.1f", bestProb), String.format("%.1f", valueVsOdds), confidence);

        return Optional.of(recommendation);
    }

    private boolean hasXgData(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        return homeStats.getXgForAvgHome() != null 
                && awayStats.getXgForAvgAway() != null
                && homeStats.getXgAgainstAvgHome() != null 
                && awayStats.getXgAgainstAvgAway() != null;
    }

    private double calculateHomeWinProbability(FixtureContext context, boolean hasXgData) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        // Season win/loss percentages
        double homeWinPctSeason = calculateWinPercentage(homeStats, true);
        double awayLossPctSeason = calculateLossPercentage(awayStats, false);

        // Form win/loss percentages
        double homeWinPctForm = homeWinPctSeason;
        double awayLossPctForm = awayLossPctSeason;
        if (context.hasRecentForm()) {
            homeWinPctForm = calculateFormWinPercentage(context.getHomeTeamForm().getWinsHome());
            awayLossPctForm = calculateFormLossPercentage(context.getAwayTeamForm().getLossesAway());
        }

        // PPG factors
        double homePpgNorm = normalizePpg(safeDouble(homeStats.getPpgHome()));
        double awayPpgInverseNorm = 100.0 - normalizePpg(safeDouble(awayStats.getPpgAway()));

        // Goal difference factor
        double goalDiffFactor = calculateGoalDiffFactor(homeStats, awayStats);

        // Implied probability from odds
        double impliedProb = 33.3;
        if (context.hasOdds() && context.getOdds().getOddsFt1() != null && context.getOdds().getOddsFt1() > 0) {
            impliedProb = (1.0 / context.getOdds().getOddsFt1()) * 100;
        }

        if (hasXgData) {
            // xG comparison factor (team xG vs opponent xGA)
            double xgComparison = calculateXgComparisonScore(homeStats, awayStats, true);

            return (homeWinPctSeason * WEIGHT_WIN_PCT_SEASON)
                    + (homeWinPctForm * WEIGHT_WIN_PCT_FORM)
                    + (awayLossPctSeason * WEIGHT_LOSS_PCT_SEASON)
                    + (awayLossPctForm * WEIGHT_LOSS_PCT_FORM)
                    + (homePpgNorm * WEIGHT_PPG)
                    + (awayPpgInverseNorm * WEIGHT_PPG_INVERSE)
                    + (xgComparison * WEIGHT_XG_COMPARISON)
                    + (goalDiffFactor * WEIGHT_GOAL_DIFF)
                    + (impliedProb * WEIGHT_IMPLIED_ODDS);
        } else {
            return (homeWinPctSeason * WEIGHT_WIN_PCT_SEASON_NO_XG)
                    + (homeWinPctForm * WEIGHT_WIN_PCT_FORM_NO_XG)
                    + (awayLossPctSeason * WEIGHT_LOSS_PCT_SEASON_NO_XG)
                    + (awayLossPctForm * WEIGHT_LOSS_PCT_FORM_NO_XG)
                    + (homePpgNorm * WEIGHT_PPG_NO_XG)
                    + (awayPpgInverseNorm * WEIGHT_PPG_INVERSE_NO_XG)
                    + (goalDiffFactor * WEIGHT_GOAL_DIFF_NO_XG)
                    + (impliedProb * WEIGHT_IMPLIED_ODDS_NO_XG);
        }
    }

    private double calculateAwayWinProbability(FixtureContext context, boolean hasXgData) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        // Season win/loss percentages
        double awayWinPctSeason = calculateWinPercentage(awayStats, false);
        double homeLossPctSeason = calculateLossPercentage(homeStats, true);

        // Form win/loss percentages
        double awayWinPctForm = awayWinPctSeason;
        double homeLossPctForm = homeLossPctSeason;
        if (context.hasRecentForm()) {
            awayWinPctForm = calculateFormWinPercentage(context.getAwayTeamForm().getWinsAway());
            homeLossPctForm = calculateFormLossPercentage(context.getHomeTeamForm().getLossesHome());
        }

        // PPG factors
        double awayPpgNorm = normalizePpg(safeDouble(awayStats.getPpgAway()));
        double homePpgInverseNorm = 100.0 - normalizePpg(safeDouble(homeStats.getPpgHome()));

        // Goal difference factor (inverted for away perspective)
        double goalDiffFactor = 100.0 - calculateGoalDiffFactor(homeStats, awayStats);

        // Implied probability from odds
        double impliedProb = 33.3;
        if (context.hasOdds() && context.getOdds().getOddsFt2() != null && context.getOdds().getOddsFt2() > 0) {
            impliedProb = (1.0 / context.getOdds().getOddsFt2()) * 100;
        }

        if (hasXgData) {
            // xG comparison factor (team xG vs opponent xGA)
            double xgComparison = calculateXgComparisonScore(awayStats, homeStats, false);

            return (awayWinPctSeason * WEIGHT_WIN_PCT_SEASON)
                    + (awayWinPctForm * WEIGHT_WIN_PCT_FORM)
                    + (homeLossPctSeason * WEIGHT_LOSS_PCT_SEASON)
                    + (homeLossPctForm * WEIGHT_LOSS_PCT_FORM)
                    + (awayPpgNorm * WEIGHT_PPG)
                    + (homePpgInverseNorm * WEIGHT_PPG_INVERSE)
                    + (xgComparison * WEIGHT_XG_COMPARISON)
                    + (goalDiffFactor * WEIGHT_GOAL_DIFF)
                    + (impliedProb * WEIGHT_IMPLIED_ODDS);
        } else {
            return (awayWinPctSeason * WEIGHT_WIN_PCT_SEASON_NO_XG)
                    + (awayWinPctForm * WEIGHT_WIN_PCT_FORM_NO_XG)
                    + (homeLossPctSeason * WEIGHT_LOSS_PCT_SEASON_NO_XG)
                    + (homeLossPctForm * WEIGHT_LOSS_PCT_FORM_NO_XG)
                    + (awayPpgNorm * WEIGHT_PPG_NO_XG)
                    + (homePpgInverseNorm * WEIGHT_PPG_INVERSE_NO_XG)
                    + (goalDiffFactor * WEIGHT_GOAL_DIFF_NO_XG)
                    + (impliedProb * WEIGHT_IMPLIED_ODDS_NO_XG);
        }
    }

    private double calculateXgComparisonScore(TeamSeasonStats teamStats, TeamSeasonStats opponentStats, boolean isHome) {
        // Team xG vs opponent xGA
        double teamXg = isHome 
                ? safeDouble(teamStats.getXgForAvgHome(), 1.0) 
                : safeDouble(teamStats.getXgForAvgAway(), 1.0);
        double opponentXga = isHome 
                ? safeDouble(opponentStats.getXgAgainstAvgAway(), 1.0) 
                : safeDouble(opponentStats.getXgAgainstAvgHome(), 1.0);

        // Higher team xG and higher opponent xGA = better for this team
        // Normalize to 0-100 scale
        double xgAdvantage = teamXg - 1.0; // How much better than average
        double xgaVulnerability = opponentXga - 1.0; // How much worse opponent's defense is
        
        return clampScore(50.0 + (xgAdvantage * 20.0) + (xgaVulnerability * 15.0));
    }

    private double calculateXgDominance(TeamSeasonStats teamStats, TeamSeasonStats opponentStats, boolean isHome) {
        double teamXg = isHome 
                ? safeDouble(teamStats.getXgForAvgHome(), 1.0) 
                : safeDouble(teamStats.getXgForAvgAway(), 1.0);
        double opponentXga = isHome 
                ? safeDouble(opponentStats.getXgAgainstAvgAway(), 1.0) 
                : safeDouble(opponentStats.getXgAgainstAvgHome(), 1.0);

        return teamXg - opponentXga;
    }

    private double getXgDominanceMultiplier(double dominance) {
        if (dominance > XG_STRONG_DOMINANCE_THRESHOLD) {
            return XG_STRONG_DOMINANCE_MULTIPLIER;
        } else if (dominance > XG_MODERATE_DOMINANCE_THRESHOLD) {
            return XG_MODERATE_DOMINANCE_MULTIPLIER;
        } else if (dominance < XG_DISADVANTAGE_THRESHOLD) {
            return XG_DISADVANTAGE_MULTIPLIER;
        }
        return 1.0;
    }

    private double calculateFormMomentum(TeamRecentForm form, boolean isHome) {
        if (form == null) {
            return 1.0;
        }

        int wins = isHome ? safeInt(form.getWinsHome()) : safeInt(form.getWinsAway());
        int losses = isHome ? safeInt(form.getLossesHome()) : safeInt(form.getLossesAway());
        int draws = isHome ? safeInt(form.getDrawsHome()) : safeInt(form.getDrawsAway());

        // Hot streak: 5 wins or 4 wins + 1 draw
        if (wins >= 5 || (wins == 4 && draws >= 1)) {
            return FORM_HOT_STREAK_MULTIPLIER;
        }
        
        // Crisis: 5 losses
        if (losses >= 5) {
            return FORM_CRISIS_MULTIPLIER;
        }
        
        // Good form: 3+ wins
        if (wins >= 3) {
            return FORM_GOOD_MULTIPLIER;
        }
        
        // Poor form: 3+ losses
        if (losses >= 3) {
            return FORM_POOR_MULTIPLIER;
        }

        return 1.0;
    }

    private String getFormMomentumDescription(double momentum) {
        if (momentum >= FORM_HOT_STREAK_MULTIPLIER) {
            return "Hot streak";
        } else if (momentum <= FORM_CRISIS_MULTIPLIER) {
            return "Crisis";
        } else if (momentum >= FORM_GOOD_MULTIPLIER) {
            return "Good form";
        } else if (momentum <= FORM_POOR_MULTIPLIER) {
            return "Poor form";
        }
        return "Neutral";
    }

    private double applyPositionFactor(double prob, FixtureContext context, boolean isHome) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        if (homeStats.getPosition() == null || awayStats.getPosition() == null) {
            return prob;
        }

        int positionDiff = homeStats.getPosition() - awayStats.getPosition();
        boolean isHigherRanked = isHome ? positionDiff < 0 : positionDiff > 0;
        int absDiff = Math.abs(positionDiff);

        if (isHigherRanked) {
            if (absDiff >= 10) return prob * POSITION_GAP_LARGE_MULTIPLIER;
            if (absDiff >= 6) return prob * POSITION_GAP_MEDIUM_MULTIPLIER;
            if (absDiff >= 3) return prob * POSITION_GAP_SMALL_MULTIPLIER;
        }

        return prob;
    }

    private double applyMotivationFactor(double prob, FixtureContext context, boolean isHome) {
        TeamSeasonStats stats = isHome ? context.getHomeTeamStats() : context.getAwayTeamStats();

        if (stats.getPosition() == null) {
            return prob;
        }

        int position = stats.getPosition();

        if (position <= 2) {
            return prob * MOTIVATION_TITLE_MULTIPLIER;
        } else if (position >= 3 && position <= 5) {
            return prob * MOTIVATION_EUROPE_MULTIPLIER;
        } else if (position >= 17) {
            return prob * MOTIVATION_RELEGATION_MULTIPLIER;
        }

        return prob;
    }

private double calculateWinPercentage(TeamSeasonStats stats, boolean isHome) {
        int matchesAtVenue = calculateMatchesAtVenue(stats, isHome);
        if (matchesAtVenue == 0) {
            return 33.3;
        }
        int wins = isHome ? safeInt(stats.getSeasonWinsHome()) : safeInt(stats.getSeasonWinsAway());
        return (wins * 100.0) / matchesAtVenue;
    }

    private double calculateLossPercentage(TeamSeasonStats stats, boolean isHome) {
        int matchesAtVenue = calculateMatchesAtVenue(stats, isHome);
        if (matchesAtVenue == 0) {
            return 33.3;
        }
        int losses = isHome ? safeInt(stats.getSeasonLossesHome()) : safeInt(stats.getSeasonLossesAway());
        return (losses * 100.0) / matchesAtVenue;
    }

    private int calculateMatchesAtVenue(TeamSeasonStats stats, boolean isHome) {
        if (isHome) {
            return safeInt(stats.getSeasonWinsHome()) 
                    + safeInt(stats.getSeasonDrawsHome()) 
                    + safeInt(stats.getSeasonLossesHome());
        } else {
            return safeInt(stats.getSeasonWinsAway()) 
                    + safeInt(stats.getSeasonDrawsAway()) 
                    + safeInt(stats.getSeasonLossesAway());
        }
    }

    private double calculateFormWinPct(Integer wins) {
        return (safeInt(wins) * 100.0) / 5.0;
    }

    private double calculateFormLossPct(Integer losses) {
        return (safeInt(losses) * 100.0) / 5.0;
    }

    private double normalizePpg(double ppg) {
        return Math.min(100.0, ppg * 33.33);
    }

    private String getMotivationDescription(Integer position) {
        if (position == null) {
            return null;
        }
        if (position <= 2) {
            return "Title race";
        } else if (position >= 3 && position <= 5) {
            return "European qualification";
        } else if (position >= 17) {
            return "Relegation battle";
        }
        return null;
    }

    private double calculateGoalDiffFactor(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        int homeGd = safeInt(homeStats.getSeasonGoalDifference());
        int awayGd = safeInt(awayStats.getSeasonGoalDifference());

        int diff = homeGd - awayGd;
        return clampScore(50.0 + (diff * 2));
    }

    private ConfidenceLevel determineConfidence(double probability, double valueVsOdds) {
        if (probability >= THRESHOLD_STRONG && valueVsOdds >= VALUE_THRESHOLD) {
            return ConfidenceLevel.STRONG;
        } else if (probability >= THRESHOLD_MODERATE) {
            return ConfidenceLevel.MODERATE;
        }
        return ConfidenceLevel.WEAK;
    }

    private Map<String, Object> buildFactors(FixtureContext context, double homeWin, double draw, 
            double awayWin, double valueVsOdds, boolean hasXgData, 
            double homeFormMomentum, double awayFormMomentum) {
        Map<String, Object> factors = new HashMap<>();
        
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();
        
        // Probabilities
        factors.put("homeWinProbability", homeWin);
        factors.put("drawProbability", draw);
        factors.put("awayWinProbability", awayWin);
        factors.put("valueVsOdds", valueVsOdds);

        // Odds
        if (context.hasOdds()) {
            factors.put("oddsFt1", context.getOdds().getOddsFt1());
            factors.put("oddsFtX", context.getOdds().getOddsFtX());
            factors.put("oddsFt2", context.getOdds().getOddsFt2());
            
            // Implied probabilities
            if (context.getOdds().getOddsFt1() != null && context.getOdds().getOddsFt1() > 0) {
                factors.put("impliedHomeWinPct", (1.0 / context.getOdds().getOddsFt1()) * 100);
            }
            if (context.getOdds().getOddsFtX() != null && context.getOdds().getOddsFtX() > 0) {
                factors.put("impliedDrawPct", (1.0 / context.getOdds().getOddsFtX()) * 100);
            }
            if (context.getOdds().getOddsFt2() != null && context.getOdds().getOddsFt2() > 0) {
                factors.put("impliedAwayWinPct", (1.0 / context.getOdds().getOddsFt2()) * 100);
            }
        }

        // Position data
        factors.put("homePosition", homeStats.getPosition());
        factors.put("awayPosition", awayStats.getPosition());
        if (homeStats.getPosition() != null && awayStats.getPosition() != null) {
            factors.put("positionGap", Math.abs(homeStats.getPosition() - awayStats.getPosition()));
        }

        // xG data
        factors.put("xgDataAvailable", hasXgData);
        if (hasXgData) {
            double homeXg = safeDouble(homeStats.getXgForAvgHome());
            double awayXg = safeDouble(awayStats.getXgForAvgAway());
            double homeXga = safeDouble(homeStats.getXgAgainstAvgHome());
            double awayXga = safeDouble(awayStats.getXgAgainstAvgAway());
            
            factors.put("homeXgForAvg", homeXg);
            factors.put("awayXgForAvg", awayXg);
            factors.put("homeXgAgainstAvg", homeXga);
            factors.put("awayXgAgainstAvg", awayXga);
            
            double homeXgDominance = calculateXgDominance(homeStats, awayStats, true);
            double awayXgDominance = calculateXgDominance(awayStats, homeStats, false);
            factors.put("homeXgDominance", homeXgDominance);
            factors.put("awayXgDominance", awayXgDominance);
            
            factors.put("homeXgDominanceMultiplier", getXgDominanceMultiplier(homeXgDominance));
            factors.put("awayXgDominanceMultiplier", getXgDominanceMultiplier(awayXgDominance));
        }

        // Form momentum
        factors.put("homeFormMomentumMultiplier", homeFormMomentum);
        factors.put("awayFormMomentumMultiplier", awayFormMomentum);
        factors.put("homeFormStatus", getFormMomentumDescription(homeFormMomentum));
        factors.put("awayFormStatus", getFormMomentumDescription(awayFormMomentum));

        // Recent form data
        factors.put("formDataAvailable", context.hasRecentForm());
        if (context.hasRecentForm()) {
            TeamRecentForm homeForm = context.getHomeTeamForm();
            TeamRecentForm awayForm = context.getAwayTeamForm();
            if (homeForm != null) {
                factors.put("homeFormWins", safeInt(homeForm.getWinsHome()));
                factors.put("homeFormDraws", safeInt(homeForm.getDrawsHome()));
                factors.put("homeFormLosses", safeInt(homeForm.getLossesHome()));
            }
            if (awayForm != null) {
                factors.put("awayFormWins", safeInt(awayForm.getWinsAway()));
                factors.put("awayFormDraws", safeInt(awayForm.getDrawsAway()));
                factors.put("awayFormLosses", safeInt(awayForm.getLossesAway()));
            }
        }

        // Home advantage
        factors.put("homeAdvantageApplied", HOME_ADVANTAGE_BOOST * 100);

        // Motivation
        String homeMotivation = getMotivationDescription(homeStats.getPosition());
        String awayMotivation = getMotivationDescription(awayStats.getPosition());
        if (homeMotivation != null) {
            factors.put("homeMotivation", homeMotivation);
        }
        if (awayMotivation != null) {
            factors.put("awayMotivation", awayMotivation);
        }

        // PPG
        factors.put("homePpgHome", safeDouble(homeStats.getPpgHome()));
        factors.put("awayPpgAway", safeDouble(awayStats.getPpgAway()));

        // Goal difference
        factors.put("homeGoalDifference", safeInt(homeStats.getSeasonGoalDifference()));
        factors.put("awayGoalDifference", safeInt(awayStats.getSeasonGoalDifference()));

        // Positive indicators and risk flags
        List<String> positiveIndicators = new ArrayList<>();
        List<String> riskFlags = new ArrayList<>();

        if (homeFormMomentum >= FORM_HOT_STREAK_MULTIPLIER) {
            positiveIndicators.add("Home team on hot streak");
        } else if (homeFormMomentum <= FORM_POOR_MULTIPLIER) {
            riskFlags.add("Home team in poor form");
        }

        if (awayFormMomentum >= FORM_HOT_STREAK_MULTIPLIER) {
            positiveIndicators.add("Away team on hot streak");
        } else if (awayFormMomentum <= FORM_POOR_MULTIPLIER) {
            riskFlags.add("Away team in poor form");
        }

        if (hasXgData) {
            double homeXgDom = calculateXgDominance(homeStats, awayStats, true);
            if (homeXgDom > XG_STRONG_DOMINANCE_THRESHOLD) {
                positiveIndicators.add("Home team xG dominance");
            }
            double awayXgDom = calculateXgDominance(awayStats, homeStats, false);
            if (awayXgDom > XG_STRONG_DOMINANCE_THRESHOLD) {
                positiveIndicators.add("Away team xG dominance");
            }
        }

        if (valueVsOdds >= VALUE_THRESHOLD) {
            positiveIndicators.add("Value vs market odds");
        }

        factors.put("positiveIndicators", positiveIndicators);
        factors.put("riskFlags", riskFlags);
        
        return factors;
    }

    private String buildDescription(FixtureContext context, String outcome, double probability, 
            ConfidenceLevel confidence, double valueVsOdds, String outcomeType,
            double homeFormMomentum, double awayFormMomentum) {
        StringBuilder sb = new StringBuilder();
        
        sb.append(confidence.getDisplayName()).append(" confidence ");
        sb.append(outcome).append(" recommendation ");
        sb.append(String.format("(%.1f%% probability)", probability));
        
        if (valueVsOdds > 0) {
            sb.append(String.format(" (+%.1f%% value)", valueVsOdds));
        }
        
        sb.append(". ");

        // Add form context
        double relevantMomentum = outcomeType.equals("HOME") ? homeFormMomentum : 
                (outcomeType.equals("AWAY") ? awayFormMomentum : 1.0);
        if (relevantMomentum >= FORM_HOT_STREAK_MULTIPLIER) {
            sb.append("Team on hot streak. ");
        } else if (relevantMomentum >= FORM_GOOD_MULTIPLIER) {
            sb.append("Team in good form. ");
        }

        // Add xG context if available
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();
        if (hasXgData(homeStats, awayStats)) {
            double xgDom = outcomeType.equals("HOME") 
                    ? calculateXgDominance(homeStats, awayStats, true)
                    : calculateXgDominance(awayStats, homeStats, false);
            if (xgDom > XG_STRONG_DOMINANCE_THRESHOLD) {
                sb.append("Strong xG advantage. ");
            }
        }

        sb.append(context.getHomeTeam().getName()).append(" vs ").append(context.getAwayTeam().getName());

        return sb.toString();
    }
}
