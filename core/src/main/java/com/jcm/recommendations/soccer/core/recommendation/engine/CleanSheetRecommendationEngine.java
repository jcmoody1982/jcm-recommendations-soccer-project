package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.core.recommendation.util.RecommendationFactory;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.*;

@Component
@Slf4j
public class CleanSheetRecommendationEngine implements RecommendationEngine {

    // Base weights when xG data IS available (total = 1.0)
    private static final double WEIGHT_TEAM_CS_SEASON = 0.15;
    private static final double WEIGHT_TEAM_CS_FORM = 0.15;
    private static final double WEIGHT_TEAM_CONCEDED_SEASON = 0.10;
    private static final double WEIGHT_TEAM_CONCEDED_FORM = 0.10;
    private static final double WEIGHT_TEAM_XGA = 0.15;
    private static final double WEIGHT_OPPONENT_FTS_SEASON = 0.10;
    private static final double WEIGHT_OPPONENT_FTS_FORM = 0.10;
    private static final double WEIGHT_OPPONENT_XG = 0.15;

    // Weights when xG data NOT available (redistribute)
    private static final double WEIGHT_TEAM_CS_SEASON_NO_XG = 0.20;
    private static final double WEIGHT_TEAM_CS_FORM_NO_XG = 0.20;
    private static final double WEIGHT_TEAM_CONCEDED_SEASON_NO_XG = 0.15;
    private static final double WEIGHT_TEAM_CONCEDED_FORM_NO_XG = 0.15;
    private static final double WEIGHT_OPPONENT_FTS_SEASON_NO_XG = 0.15;
    private static final double WEIGHT_OPPONENT_FTS_FORM_NO_XG = 0.15;

    // Thresholds
    private static final double THRESHOLD_STRONG = 70.0;
    private static final double THRESHOLD_MODERATE = 50.0;

    // xGA Rating thresholds (team's expected goals against per game)
    private static final double XGA_ELITE_THRESHOLD = 0.80;
    private static final double XGA_STRONG_THRESHOLD = 1.10;
    private static final double XGA_AVERAGE_THRESHOLD = 1.40;

    // Opponent xG Rating thresholds (opponent's expected goals per game)
    private static final double OPP_XG_POOR_THRESHOLD = 0.80;
    private static final double OPP_XG_BELOW_AVG_THRESHOLD = 1.10;
    private static final double OPP_XG_AVERAGE_THRESHOLD = 1.50;

    // Streak and regression adjustments
    private static final double HOT_STREAK_BONUS = 1.15;       // 3+ consecutive CS
    private static final double CONCEDED_PENALTY = 0.90;       // Conceded in all recent
    private static final double XG_OVERPERFORMANCE_BONUS = 1.10;  // Opponent scoring < xG
    private static final double XG_REGRESSION_PENALTY = 0.90;     // Team conceding < xGA

    @Override
    public RecommendationType getType() {
        return RecommendationType.CLEAN_SHEET;
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        log.debug("Analyzing Clean Sheet for fixture: fixtureId={}, {} vs {}", 
                context.getFixture().getId(),
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());

        List<CleanSheetCandidate> candidates = new ArrayList<>();

        analyzeTeamCleanSheet(context, true).ifPresent(candidates::add);
        analyzeTeamCleanSheet(context, false).ifPresent(candidates::add);

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        CleanSheetCandidate best = candidates.stream()
                .max(Comparator.comparingDouble(CleanSheetCandidate::score))
                .orElse(null);

        if (best == null) {
            return Optional.empty();
        }

        ConfidenceLevel confidence = determineConfidence(best.score);
        if (confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        Map<String, Object> factors = buildFactors(best, candidates);

        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(RecommendationType.CLEAN_SHEET)
                .confidence(confidence)
                .score(best.score)
                .market(best.teamName + " Clean Sheet")
                .odds(null)
                .description(buildDescription(context, best, confidence))
                .factors(factors)
                .build();

        log.info("Clean Sheet recommendation generated: fixtureId={}, team={}, score={}, confidence={}", 
                context.getFixture().getId(), best.teamName, String.format("%.1f", best.score), confidence);

        return Optional.of(recommendation);
    }

    private Optional<CleanSheetCandidate> analyzeTeamCleanSheet(FixtureContext context, boolean isHomeTeam) {
        TeamSeasonStats teamStats = isHomeTeam ? context.getHomeTeamStats() : context.getAwayTeamStats();
        TeamSeasonStats opponentStats = isHomeTeam ? context.getAwayTeamStats() : context.getHomeTeamStats();
        String teamName = isHomeTeam ? context.getHomeTeam().getName() : context.getAwayTeam().getName();

        if (teamStats == null || opponentStats == null) {
            return Optional.empty();
        }

        // Check if xG data is available
        boolean hasXgData = hasXgData(teamStats, opponentStats, isHomeTeam);

        // Team clean sheet percentages
        double teamCsSeason = calculateCleanSheetPercentage(teamStats, isHomeTeam);
        double teamCsForm = teamCsSeason;
        int formCleanSheets = 0;
        if (context.hasRecentForm()) {
            var form = isHomeTeam ? context.getHomeTeamForm() : context.getAwayTeamForm();
            if (form != null) {
                formCleanSheets = isHomeTeam 
                        ? safeInt(form.getCleanSheetsHome()) 
                        : safeInt(form.getCleanSheetsAway());
                teamCsForm = formCleanSheets * 20.0;
            }
        }

        // Team goals conceded (inverse - lower is better)
        double teamConcededInverse = 100.0 - calculateConcededRating(teamStats, isHomeTeam);
        double teamConcededFormInverse = teamConcededInverse;
        int formConcededMatches = 0;
        if (context.hasRecentForm()) {
            var form = isHomeTeam ? context.getHomeTeamForm() : context.getAwayTeamForm();
            if (form != null) {
                Double concededAvg = isHomeTeam ? form.getConcededAvgHome() : form.getConcededAvgAway();
                teamConcededFormInverse = 100.0 - (safeDouble(concededAvg, 1.0) * 40.0);
                // Count matches where team conceded (5 form matches - clean sheets)
                formConcededMatches = 5 - formCleanSheets;
            }
        }

        // Team xGA rating (lower xGA = better for clean sheets)
        double teamXgaScore = 50.0;  // Default neutral
        double teamXga = 0.0;
        if (hasXgData) {
            teamXga = isHomeTeam 
                    ? safeDouble(teamStats.getXgAgainstAvgHome(), 1.2)
                    : safeDouble(teamStats.getXgAgainstAvgAway(), 1.2);
            teamXgaScore = calculateXgaScore(teamXga);
        }

        // Opponent failed to score percentages
        double opponentFtsSeason = calculateFailedToScorePercentage(opponentStats, !isHomeTeam);
        double opponentFtsForm = opponentFtsSeason;
        if (context.hasRecentForm()) {
            var form = isHomeTeam ? context.getAwayTeamForm() : context.getHomeTeamForm();
            if (form != null) {
                int formFts = !isHomeTeam 
                        ? safeInt(form.getFailedToScoreHome()) 
                        : safeInt(form.getFailedToScoreAway());
                opponentFtsForm = formFts * 20.0;
            }
        }

        // Opponent xG rating (lower xG = better for clean sheets)
        double opponentXgScore = 50.0;  // Default neutral
        double opponentXg = 0.0;
        if (hasXgData) {
            opponentXg = !isHomeTeam 
                    ? safeDouble(opponentStats.getXgForAvgHome(), 1.2)
                    : safeDouble(opponentStats.getXgForAvgAway(), 1.2);
            opponentXgScore = calculateOpponentXgScore(opponentXg);
        }

        // Calculate weighted score
        double score;
        if (hasXgData) {
            score = (teamCsSeason * WEIGHT_TEAM_CS_SEASON)
                    + (teamCsForm * WEIGHT_TEAM_CS_FORM)
                    + (teamConcededInverse * WEIGHT_TEAM_CONCEDED_SEASON)
                    + (teamConcededFormInverse * WEIGHT_TEAM_CONCEDED_FORM)
                    + (teamXgaScore * WEIGHT_TEAM_XGA)
                    + (opponentFtsSeason * WEIGHT_OPPONENT_FTS_SEASON)
                    + (opponentFtsForm * WEIGHT_OPPONENT_FTS_FORM)
                    + (opponentXgScore * WEIGHT_OPPONENT_XG);
        } else {
            score = (teamCsSeason * WEIGHT_TEAM_CS_SEASON_NO_XG)
                    + (teamCsForm * WEIGHT_TEAM_CS_FORM_NO_XG)
                    + (teamConcededInverse * WEIGHT_TEAM_CONCEDED_SEASON_NO_XG)
                    + (teamConcededFormInverse * WEIGHT_TEAM_CONCEDED_FORM_NO_XG)
                    + (opponentFtsSeason * WEIGHT_OPPONENT_FTS_SEASON_NO_XG)
                    + (opponentFtsForm * WEIGHT_OPPONENT_FTS_FORM_NO_XG);
        }

        // Apply defensive strength multiplier
        double defensiveRating = calculateDefensiveRating(teamStats, isHomeTeam);
        score *= defensiveRating;

        // Apply opponent attacking weakness multiplier
        double opponentWeakness = calculateOpponentAttackingWeakness(opponentStats, !isHomeTeam);
        score *= opponentWeakness;

        // Apply hot defensive streak bonus (3+ clean sheets in form)
        boolean hotStreak = formCleanSheets >= 3;
        if (hotStreak) {
            score *= HOT_STREAK_BONUS;
        }

        // Apply conceded in all recent matches penalty
        boolean concededInAllRecent = formConcededMatches >= 3 && formCleanSheets == 0;
        if (concededInAllRecent) {
            score *= CONCEDED_PENALTY;
        }

        // Apply xG regression adjustments
        boolean xgRegressionRisk = false;
        boolean opponentXgOverperformance = false;
        if (hasXgData) {
            // Check if team is conceding less than xGA (regression risk)
            double actualConceded = calculateConcededAvg(teamStats, isHomeTeam);
            if (teamXga > 0 && actualConceded < teamXga * 0.80) {
                xgRegressionRisk = true;
                score *= XG_REGRESSION_PENALTY;
            }

            // Check if opponent is scoring less than xG (good for clean sheet)
            double opponentActualGoals = !isHomeTeam 
                    ? calculateGoalsAvg(opponentStats.getSeasonGoalsHome(), opponentStats.getMatchesPlayed(), 1.0)
                    : calculateGoalsAvg(opponentStats.getSeasonGoalsAway(), opponentStats.getMatchesPlayed(), 1.0);
            if (opponentXg > 0 && opponentActualGoals < opponentXg * 0.80) {
                opponentXgOverperformance = true;
                score *= XG_OVERPERFORMANCE_BONUS;
            }
        }

        // Clamp score
        score = Math.min(100.0, Math.max(0.0, score));

        if (score < THRESHOLD_MODERATE) {
            return Optional.empty();
        }

        return Optional.of(new CleanSheetCandidate(
                teamName,
                isHomeTeam,
                score,
                teamCsSeason,
                teamCsForm,
                teamXga,
                teamXgaScore,
                opponentFtsSeason,
                opponentFtsForm,
                opponentXg,
                opponentXgScore,
                defensiveRating,
                opponentWeakness,
                hotStreak,
                concededInAllRecent,
                xgRegressionRisk,
                opponentXgOverperformance,
                hasXgData
        ));
    }

    private boolean hasXgData(TeamSeasonStats teamStats, TeamSeasonStats opponentStats, boolean isHomeTeam) {
        Double teamXga = isHomeTeam ? teamStats.getXgAgainstAvgHome() : teamStats.getXgAgainstAvgAway();
        Double oppXg = !isHomeTeam ? opponentStats.getXgForAvgHome() : opponentStats.getXgForAvgAway();
        return teamXga != null && oppXg != null;
    }

    private double calculateXgaScore(double xga) {
        // Lower xGA = higher score (better defense)
        if (xga < XGA_ELITE_THRESHOLD) {
            return 90.0;  // Elite defense
        } else if (xga < XGA_STRONG_THRESHOLD) {
            return 75.0;  // Strong defense
        } else if (xga < XGA_AVERAGE_THRESHOLD) {
            return 50.0;  // Average
        } else {
            return 25.0;  // Leaky defense
        }
    }

    private double calculateOpponentXgScore(double xg) {
        // Lower opponent xG = higher score (poor attackers = good for CS)
        if (xg < OPP_XG_POOR_THRESHOLD) {
            return 90.0;  // Poor creators
        } else if (xg < OPP_XG_BELOW_AVG_THRESHOLD) {
            return 75.0;  // Below average
        } else if (xg < OPP_XG_AVERAGE_THRESHOLD) {
            return 50.0;  // Average
        } else {
            return 25.0;  // Strong creators
        }
    }

    private double calculateDefensiveRating(TeamSeasonStats stats, boolean isHome) {
        double concededAvg = calculateConcededAvg(stats, isHome);
        
        if (concededAvg < 0.75) {
            return 1.20;  // Elite
        } else if (concededAvg < 1.0) {
            return 1.10;  // Strong
        } else if (concededAvg <= 1.25) {
            return 1.00;  // Average
        } else {
            return 0.85;  // Weak
        }
    }

    private double calculateOpponentAttackingWeakness(TeamSeasonStats oppStats, boolean isHome) {
        double ftsPct = calculateFailedToScorePercentage(oppStats, isHome);
        
        if (ftsPct > 40) {
            return 1.20;  // Poor attack
        } else if (ftsPct > 30) {
            return 1.10;  // Below average
        } else if (ftsPct >= 20) {
            return 1.00;  // Average
        } else {
            return 0.80;  // Strong attack
        }
    }

    private double calculateConcededRating(TeamSeasonStats stats, boolean isHome) {
        double avg = calculateConcededAvg(stats, isHome);
        return Math.min(100.0, avg * 40.0);
    }

    private ConfidenceLevel determineConfidence(double score) {
        if (score >= THRESHOLD_STRONG) {
            return ConfidenceLevel.STRONG;
        } else if (score >= THRESHOLD_MODERATE) {
            return ConfidenceLevel.MODERATE;
        }
        return ConfidenceLevel.WEAK;
    }

    private Map<String, Object> buildFactors(CleanSheetCandidate best, List<CleanSheetCandidate> all) {
        Map<String, Object> factors = new HashMap<>();
        
        // Team info
        factors.put("team", best.teamName);
        factors.put("isHomeTeam", best.isHomeTeam);
        factors.put("cleanSheetScore", best.score);
        
        // Data availability
        factors.put("xgDataAvailable", best.hasXgData);
        
        // Team clean sheet stats
        factors.put("teamCleanSheetSeasonPct", best.teamCsSeason);
        factors.put("teamCleanSheetFormPct", best.teamCsForm);
        
        // Team xGA (defensive quality)
        if (best.hasXgData) {
            factors.put("teamXgaPerGame", best.teamXga);
            factors.put("teamXgaScore", best.teamXgaScore);
            String xgaRating = best.teamXga < XGA_ELITE_THRESHOLD ? "Elite" :
                    best.teamXga < XGA_STRONG_THRESHOLD ? "Strong" :
                    best.teamXga < XGA_AVERAGE_THRESHOLD ? "Average" : "Leaky";
            factors.put("teamDefensiveXgRating", xgaRating);
        }
        
        // Opponent stats
        factors.put("opponentFailedToScoreSeasonPct", best.opponentFtsSeason);
        factors.put("opponentFailedToScoreFormPct", best.opponentFtsForm);
        
        // Opponent xG (attacking threat)
        if (best.hasXgData) {
            factors.put("opponentXgPerGame", best.opponentXg);
            factors.put("opponentXgScore", best.opponentXgScore);
            String oppXgRating = best.opponentXg < OPP_XG_POOR_THRESHOLD ? "Poor" :
                    best.opponentXg < OPP_XG_BELOW_AVG_THRESHOLD ? "Below Average" :
                    best.opponentXg < OPP_XG_AVERAGE_THRESHOLD ? "Average" : "Strong";
            factors.put("opponentAttackingXgRating", oppXgRating);
        }
        
        // Rating multipliers
        factors.put("defensiveRatingMultiplier", best.defensiveRating);
        factors.put("opponentWeaknessMultiplier", best.opponentWeakness);
        
        // Streaks and adjustments
        factors.put("hotDefensiveStreak", best.hotStreak);
        factors.put("concededInAllRecent", best.concededInAllRecent);
        
        // xG regression indicators
        if (best.hasXgData) {
            factors.put("xgRegressionRisk", best.xgRegressionRisk);
            factors.put("opponentXgOverperformance", best.opponentXgOverperformance);
        }
        
        // Summary
        factors.put("candidatesAnalyzed", all.size());
        
        // Risk flags
        List<String> risks = new ArrayList<>();
        if (best.xgRegressionRisk) {
            risks.add("Team conceding below xGA - regression risk");
        }
        if (best.concededInAllRecent) {
            risks.add("Conceded in all recent matches");
        }
        factors.put("riskFlags", risks);
        
        // Positive indicators
        List<String> positives = new ArrayList<>();
        if (best.hotStreak) {
            positives.add("Hot defensive streak (3+ consecutive CS)");
        }
        if (best.opponentXgOverperformance) {
            positives.add("Opponent scoring below xG - regression expected");
        }
        if (best.opponentWeakness >= 1.15) {
            positives.add("Opponent has poor attacking record");
        }
        factors.put("positiveIndicators", positives);

        return factors;
    }

    private String buildDescription(FixtureContext context, CleanSheetCandidate candidate, ConfidenceLevel confidence) {
        StringBuilder desc = new StringBuilder();
        desc.append(String.format("%s confidence Clean Sheet for %s (%.1f%% score)",
                confidence.getDisplayName(),
                candidate.teamName,
                candidate.score));
        
        if (candidate.hotStreak) {
            desc.append(" [hot streak]");
        }
        if (candidate.xgRegressionRisk) {
            desc.append(" [xG regression risk]");
        }
        if (candidate.opponentXgOverperformance) {
            desc.append(" [opponent overperforming]");
        }
        
        desc.append(String.format(" - %s vs %s",
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName()));
        
        return desc.toString();
    }

    private record CleanSheetCandidate(
            String teamName,
            boolean isHomeTeam,
            double score,
            double teamCsSeason,
            double teamCsForm,
            double teamXga,
            double teamXgaScore,
            double opponentFtsSeason,
            double opponentFtsForm,
            double opponentXg,
            double opponentXgScore,
            double defensiveRating,
            double opponentWeakness,
            boolean hotStreak,
            boolean concededInAllRecent,
            boolean xgRegressionRisk,
            boolean opponentXgOverperformance,
            boolean hasXgData
    ) {}
}
