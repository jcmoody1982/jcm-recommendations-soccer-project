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
public class MatchResultRecommendationEngine implements RecommendationEngine {

    private static final double WEIGHT_WIN_PCT_SEASON = 0.30;
    private static final double WEIGHT_WIN_PCT_FORM = 0.30;
    private static final double WEIGHT_PPG = 0.20;
    private static final double WEIGHT_GOAL_DIFF = 0.10;
    private static final double WEIGHT_IMPLIED_ODDS = 0.10;

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

        double homeWinProb = calculateHomeWinProbability(context);
        double awayWinProb = calculateAwayWinProbability(context);
        double drawProb = Math.max(10.0, 100.0 - homeWinProb - awayWinProb);

        homeWinProb = applyPositionFactor(homeWinProb, context, true);
        awayWinProb = applyPositionFactor(awayWinProb, context, false);
        homeWinProb = applyMotivationFactor(homeWinProb, context, true);
        awayWinProb = applyMotivationFactor(awayWinProb, context, false);

        double total = homeWinProb + awayWinProb + drawProb;
        homeWinProb = (homeWinProb / total) * 100;
        awayWinProb = (awayWinProb / total) * 100;
        drawProb = (drawProb / total) * 100;

        String recommendedOutcome;
        double bestProb;
        double valueVsOdds = 0.0;

        if (homeWinProb >= awayWinProb && homeWinProb >= drawProb) {
            recommendedOutcome = "Home Win";
            bestProb = homeWinProb;
            if (context.hasOdds() && context.getOdds().getOddsFt1() != null) {
                double implied = (1.0 / context.getOdds().getOddsFt1()) * 100;
                valueVsOdds = homeWinProb - implied;
            }
        } else if (awayWinProb >= homeWinProb && awayWinProb >= drawProb) {
            recommendedOutcome = "Away Win";
            bestProb = awayWinProb;
            if (context.hasOdds() && context.getOdds().getOddsFt2() != null) {
                double implied = (1.0 / context.getOdds().getOddsFt2()) * 100;
                valueVsOdds = awayWinProb - implied;
            }
        } else {
            recommendedOutcome = "Draw";
            bestProb = drawProb;
            if (context.hasOdds() && context.getOdds().getOddsFtX() != null) {
                double implied = (1.0 / context.getOdds().getOddsFtX()) * 100;
                valueVsOdds = drawProb - implied;
            }
        }

        ConfidenceLevel confidence = determineConfidence(bestProb, valueVsOdds);

        if (confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        Double odds = getOddsForOutcome(context, recommendedOutcome);
        Map<String, Object> factors = buildFactors(context, homeWinProb, drawProb, awayWinProb, valueVsOdds);

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
                .type(RecommendationType.MATCH_RESULT)
                .confidence(confidence)
                .score(bestProb)
                .market(recommendedOutcome)
                .odds(odds)
                .description(buildDescription(context, recommendedOutcome, bestProb, confidence, valueVsOdds))
                .factors(factors)
                .generatedAt(Instant.now())
                .build();

        log.info("Match Result recommendation generated: fixtureId={}, outcome={}, probability={}, value={}, confidence={}", 
                context.getFixture().getId(), recommendedOutcome, 
                String.format("%.1f", bestProb), String.format("%.1f", valueVsOdds), confidence);

        return Optional.of(recommendation);
    }

    private double calculateHomeWinProbability(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double homeWinPctSeason = calculateWinPercentage(homeStats, true);
        double awayLossPctSeason = calculateLossPercentage(awayStats, false);

        double homeWinPctForm = homeWinPctSeason;
        double awayLossPctForm = awayLossPctSeason;
        if (context.hasRecentForm()) {
            homeWinPctForm = calculateFormWinPct(context.getHomeTeamForm().getWinsHome());
            awayLossPctForm = calculateFormLossPct(context.getAwayTeamForm().getLossesAway());
        }

        double homePpgNorm = normalizePpg(safeDouble(homeStats.getPpgHome()));
        double awayPpgInverseNorm = 100.0 - normalizePpg(safeDouble(awayStats.getPpgAway()));

        double goalDiffFactor = calculateGoalDiffFactor(homeStats, awayStats);

        double impliedProb = 33.3;
        if (context.hasOdds() && context.getOdds().getOddsFt1() != null) {
            impliedProb = (1.0 / context.getOdds().getOddsFt1()) * 100;
        }

        return ((homeWinPctSeason + awayLossPctSeason) / 2 * WEIGHT_WIN_PCT_SEASON)
                + ((homeWinPctForm + awayLossPctForm) / 2 * WEIGHT_WIN_PCT_FORM)
                + ((homePpgNorm + awayPpgInverseNorm) / 2 * WEIGHT_PPG)
                + (goalDiffFactor * WEIGHT_GOAL_DIFF)
                + (impliedProb * WEIGHT_IMPLIED_ODDS);
    }

    private double calculateAwayWinProbability(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double awayWinPctSeason = calculateWinPercentage(awayStats, false);
        double homeLossPctSeason = calculateLossPercentage(homeStats, true);

        double awayWinPctForm = awayWinPctSeason;
        double homeLossPctForm = homeLossPctSeason;
        if (context.hasRecentForm()) {
            awayWinPctForm = calculateFormWinPct(context.getAwayTeamForm().getWinsAway());
            homeLossPctForm = calculateFormLossPct(context.getHomeTeamForm().getLossesHome());
        }

        double awayPpgNorm = normalizePpg(safeDouble(awayStats.getPpgAway()));
        double homePpgInverseNorm = 100.0 - normalizePpg(safeDouble(homeStats.getPpgHome()));

        double goalDiffFactor = 100.0 - calculateGoalDiffFactor(homeStats, awayStats);

        double impliedProb = 33.3;
        if (context.hasOdds() && context.getOdds().getOddsFt2() != null) {
            impliedProb = (1.0 / context.getOdds().getOddsFt2()) * 100;
        }

        return ((awayWinPctSeason + homeLossPctSeason) / 2 * WEIGHT_WIN_PCT_SEASON)
                + ((awayWinPctForm + homeLossPctForm) / 2 * WEIGHT_WIN_PCT_FORM)
                + ((awayPpgNorm + homePpgInverseNorm) / 2 * WEIGHT_PPG)
                + (goalDiffFactor * WEIGHT_GOAL_DIFF)
                + (impliedProb * WEIGHT_IMPLIED_ODDS);
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
            if (absDiff >= 10) return prob * 1.20;
            if (absDiff >= 6) return prob * 1.10;
            if (absDiff >= 3) return prob * 1.05;
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
            return prob * 1.15;
        } else if (position >= 3 && position <= 5) {
            return prob * 1.10;
        } else if (position >= 17) {
            return prob * 1.15;
        }

        return prob;
    }

    private double calculateWinPercentage(TeamSeasonStats stats, boolean isHome) {
        if (stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 33.3;
        }
        int wins = isHome ? safeInt(stats.getSeasonWinsHome()) : safeInt(stats.getSeasonWinsAway());
        return (wins * 100.0) / stats.getMatchesPlayed();
    }

    private double calculateLossPercentage(TeamSeasonStats stats, boolean isHome) {
        if (stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 33.3;
        }
        int losses = isHome ? safeInt(stats.getSeasonLossesHome()) : safeInt(stats.getSeasonLossesAway());
        return (losses * 100.0) / stats.getMatchesPlayed();
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

    private double calculateGoalDiffFactor(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        int homeGd = safeInt(homeStats.getSeasonGoalDifference());
        int awayGd = safeInt(awayStats.getSeasonGoalDifference());

        int diff = homeGd - awayGd;
        return Math.min(100.0, Math.max(0.0, 50.0 + (diff * 2)));
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
            double awayWin, double valueVsOdds) {
        Map<String, Object> factors = new HashMap<>();
        
        factors.put("homeWinProbability", homeWin);
        factors.put("drawProbability", draw);
        factors.put("awayWinProbability", awayWin);
        factors.put("valueVsOdds", valueVsOdds);

        if (context.hasOdds()) {
            factors.put("oddsFt1", context.getOdds().getOddsFt1());
            factors.put("oddsFtX", context.getOdds().getOddsFtX());
            factors.put("oddsFt2", context.getOdds().getOddsFt2());
        }

        factors.put("homePosition", context.getHomeTeamStats().getPosition());
        factors.put("awayPosition", context.getAwayTeamStats().getPosition());

        return factors;
    }

    private String buildDescription(FixtureContext context, String outcome, double probability, 
            ConfidenceLevel confidence, double valueVsOdds) {
        String valueStr = valueVsOdds > 0 ? String.format(" (+%.1f%% value)", valueVsOdds) : "";
        return String.format("%s confidence %s recommendation (%.1f%% probability)%s - %s vs %s",
                confidence.getDisplayName(),
                outcome,
                probability,
                valueStr,
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private double safeDouble(Double value) {
        return value != null ? value : 0.0;
    }

    private Double getOddsForOutcome(FixtureContext context, String outcome) {
        if (!context.hasOdds()) {
            return null;
        }
        return switch (outcome) {
            case "Home Win" -> context.getOdds().getOddsFt1();
            case "Draw" -> context.getOdds().getOddsFtX();
            case "Away Win" -> context.getOdds().getOddsFt2();
            default -> null;
        };
    }
}
