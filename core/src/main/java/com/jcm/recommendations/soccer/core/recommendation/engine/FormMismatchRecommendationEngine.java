package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.core.recommendation.util.RecommendationFactory;
import com.jcm.recommendations.soccer.core.recommendation.util.MatchBriefCopy;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.*;

@Component
@Slf4j
public class FormMismatchRecommendationEngine implements RecommendationEngine {

    // Base weights (total = 1.0)
    private static final double WEIGHT_PPG_DELTA = 0.25;
    private static final double WEIGHT_GOALS_DELTA = 0.20;
    private static final double WEIGHT_CONCEDED_DELTA = 0.15;  // New: defensive improvement
    private static final double WEIGHT_WINS_DELTA = 0.20;
    private static final double WEIGHT_CLEANSHEET_DELTA = 0.20;

    // Home/Away context multiplier
    private static final double HOME_AWAY_CONTEXT_MULTIPLIER = 1.25;

    // Thresholds
    private static final double THRESHOLD_STRONG = 25.0;
    private static final double THRESHOLD_MODERATE = 15.0;
    private static final double STREAK_BONUS = 5.0;

    // Trend detection thresholds
    private static final double SCORING_TREND_THRESHOLD = 0.3;    // Goals increase per match
    private static final double DEFENSIVE_TREND_THRESHOLD = 0.3;  // Goals conceded decrease per match

    // xG regression risk threshold
    private static final double XG_REGRESSION_THRESHOLD = 0.5;  // Goals exceed xG by this much = risk

    @Override
    public RecommendationType getType() {
        return RecommendationType.WINNING_FORM_MISMATCH;
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        log.debug("Analyzing Form Mismatch for fixture: fixtureId={}, {} vs {}", 
                context.getFixture().getId(),
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());

        List<TeamMismatch> mismatches = new ArrayList<>();

        analyzeTeamMismatch(context, true).ifPresent(mismatches::add);
        analyzeTeamMismatch(context, false).ifPresent(mismatches::add);

        if (mismatches.isEmpty()) {
            return Optional.empty();
        }

        TeamMismatch bestMismatch = mismatches.stream()
                .max(Comparator.comparingDouble(m -> Math.abs(m.mismatchScore)))
                .orElse(null);

        if (bestMismatch == null) {
            return Optional.empty();
        }

        RecommendationType type = bestMismatch.mismatchScore > 0 
                ? RecommendationType.WINNING_FORM_MISMATCH 
                : RecommendationType.LOSING_FORM_MISMATCH;

        ConfidenceLevel confidence = determineConfidence(Math.abs(bestMismatch.mismatchScore));

        if (confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        Map<String, Object> factors = buildFactors(bestMismatch, mismatches);

        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(type)
                .confidence(confidence)
                .score(Math.abs(bestMismatch.mismatchScore))
                .market(bestMismatch.teamName)
                .odds(null)
                .description(buildDescription(context, bestMismatch, type, confidence))
                .factors(factors)
                .build();

        log.info("Form Mismatch recommendation generated: fixtureId={}, team={}, type={}, score={}, confidence={}", 
                context.getFixture().getId(), 
                bestMismatch.teamName,
                type,
                String.format("%.1f", Math.abs(bestMismatch.mismatchScore)), 
                confidence);

        return Optional.of(recommendation);
    }

    @Override
    public boolean isApplicable(FixtureContext context) {
        return context.hasCompleteData() && context.hasRecentForm();
    }

    private Optional<TeamMismatch> analyzeTeamMismatch(FixtureContext context, boolean isHomeTeam) {
        TeamSeasonStats seasonStats = isHomeTeam ? context.getHomeTeamStats() : context.getAwayTeamStats();
        TeamRecentForm recentForm = isHomeTeam ? context.getHomeTeamForm() : context.getAwayTeamForm();
        String teamName = isHomeTeam ? context.getHomeTeam().getName() : context.getAwayTeam().getName();

        if (seasonStats == null || recentForm == null) {
            return Optional.empty();
        }

        // Calculate all deltas
        double ppgDelta = calculatePpgDelta(seasonStats, recentForm, isHomeTeam);
        double goalsDelta = calculateGoalsDelta(seasonStats, recentForm, isHomeTeam);
        double concededDelta = calculateConcededDelta(seasonStats, recentForm, isHomeTeam);
        double winsDelta = calculateWinsDelta(seasonStats, recentForm);
        double cleanSheetDelta = calculateCleanSheetDelta(seasonStats, recentForm);

        // Base mismatch score
        double mismatchScore = (ppgDelta * WEIGHT_PPG_DELTA)
                + (goalsDelta * WEIGHT_GOALS_DELTA)
                + (concededDelta * WEIGHT_CONCEDED_DELTA)
                + (winsDelta * WEIGHT_WINS_DELTA)
                + (cleanSheetDelta * WEIGHT_CLEANSHEET_DELTA);

        // Apply home/away context weighting
        // If this team is playing at their strength (home team at home, away team away)
        // their form in that context matters more
        boolean playingAtStrength = isHomeTeam; // Home team plays at home
        if (playingAtStrength) {
            mismatchScore *= HOME_AWAY_CONTEXT_MULTIPLIER;
        }

        // Streak bonuses
        boolean hasWinningStreak = checkWinningStreak(recentForm);
        if (hasWinningStreak && mismatchScore > 0) {
            mismatchScore += STREAK_BONUS;
        }

        boolean hasLosingStreak = checkLosingStreak(recentForm);
        if (hasLosingStreak && mismatchScore < 0) {
            mismatchScore -= STREAK_BONUS;
        }

        // Detect trends
        boolean scoringTrendUp = detectScoringTrendUp(recentForm, isHomeTeam);
        boolean defensiveTrendUp = detectDefensiveTrendUp(recentForm, isHomeTeam);

        // Add trend bonuses
        if (scoringTrendUp && mismatchScore > 0) {
            mismatchScore += 3.0;  // Scoring momentum bonus
        }
        if (defensiveTrendUp && mismatchScore > 0) {
            mismatchScore += 2.0;  // Defensive solidity bonus
        }

        // xG regression risk assessment
        boolean xgRegressionRisk = assessXgRegressionRisk(seasonStats, recentForm, isHomeTeam);

        if (Math.abs(mismatchScore) < THRESHOLD_MODERATE) {
            return Optional.empty();
        }

        return Optional.of(new TeamMismatch(
                teamName,
                isHomeTeam,
                mismatchScore,
                ppgDelta,
                goalsDelta,
                concededDelta,
                winsDelta,
                cleanSheetDelta,
                hasWinningStreak,
                hasLosingStreak,
                scoringTrendUp,
                defensiveTrendUp,
                xgRegressionRisk,
                playingAtStrength
        ));
    }

    private double calculatePpgDelta(TeamSeasonStats season, TeamRecentForm form, boolean isHome) {
        Double seasonPpg = isHome ? season.getPpgHome() : season.getPpgAway();
        Double formPpg = isHome ? form.getPpgHome() : form.getPpgAway();

        if (seasonPpg == null || seasonPpg == 0 || formPpg == null) {
            return 0.0;
        }

        return ((formPpg - seasonPpg) / seasonPpg) * 100;
    }

    private double calculateGoalsDelta(TeamSeasonStats season, TeamRecentForm form, boolean isHome) {
        double seasonAvg = calculateVenueGoalsAvg(season, isHome);
        
        Double formAvg = isHome ? form.getScoredAvgHome() : form.getScoredAvgAway();

        if (seasonAvg == 0 || formAvg == null) {
            return 0.0;
        }

        return ((formAvg - seasonAvg) / seasonAvg) * 100;
    }

    private double calculateWinsDelta(TeamSeasonStats season, TeamRecentForm form) {
        if (season.getMatchesPlayed() == null || season.getMatchesPlayed() == 0) {
            return 0.0;
        }

        int seasonWins = safeInt(season.getSeasonWinsOverall());
        int formWins = safeInt(form.getWinsOverall());

        double seasonWinPct = (seasonWins * 100.0) / season.getMatchesPlayed();
        double formWinPct = calculateFormWinPercentage(formWins);

        return formWinPct - seasonWinPct;
    }

    private double calculateCleanSheetDelta(TeamSeasonStats season, TeamRecentForm form) {
        if (season.getMatchesPlayed() == null || season.getMatchesPlayed() == 0) {
            return 0.0;
        }

        int seasonCS = safeInt(season.getSeasonCleanSheetsOverall());
        int formCS = safeInt(form.getCleanSheetsOverall());

        double seasonCsPct = (seasonCS * 100.0) / season.getMatchesPlayed();
        double formCsPct = (formCS * 100.0) / 5.0;

        return formCsPct - seasonCsPct;
    }

    private double calculateConcededDelta(TeamSeasonStats season, TeamRecentForm form, boolean isHome) {
        // Calculate goals conceded improvement (negative delta = improvement)
        double seasonAvg = calculateVenueConcededAvg(season, isHome);
        
        Double formAvg = isHome ? form.getConcededAvgHome() : form.getConcededAvgAway();

        if (seasonAvg == 0 || formAvg == null) {
            return 0.0;
        }

        // Invert: lower conceding = positive delta (improvement)
        return ((seasonAvg - formAvg) / seasonAvg) * 100;
    }

    private boolean checkWinningStreak(TeamRecentForm form) {
        return form.getWinsOverall() != null && form.getWinsOverall() >= 3;
    }

    private boolean checkLosingStreak(TeamRecentForm form) {
        return form.getLossesOverall() != null && form.getLossesOverall() >= 3;
    }

    private boolean detectScoringTrendUp(TeamRecentForm form, boolean isHome) {
        // Check if team's scoring is trending upward in recent form
        // We use the difference between home/away averages as a proxy for trend
        Double scoredAvg = isHome ? form.getScoredAvgHome() : form.getScoredAvgAway();
        Double overallAvg = form.getScoredAvgOverall();
        
        if (scoredAvg == null || overallAvg == null || overallAvg == 0) {
            return false;
        }
        
        // If location-specific avg > overall avg, suggests positive trend at that venue
        return (scoredAvg - overallAvg) > SCORING_TREND_THRESHOLD;
    }

    private boolean detectDefensiveTrendUp(TeamRecentForm form, boolean isHome) {
        // Check if team's defense is improving (conceding less)
        Double concededAvg = isHome ? form.getConcededAvgHome() : form.getConcededAvgAway();
        Double overallAvg = form.getConcededAvgOverall();
        
        if (concededAvg == null || overallAvg == null || overallAvg == 0) {
            return false;
        }
        
        // If location-specific conceded < overall, suggests defensive improvement
        return (overallAvg - concededAvg) > DEFENSIVE_TREND_THRESHOLD;
    }

    private boolean assessXgRegressionRisk(TeamSeasonStats season, TeamRecentForm form, boolean isHome) {
        // Check if actual goals significantly exceed xG (regression risk)
        Double xgFor = isHome ? season.getXgForAvgHome() : season.getXgForAvgAway();
        Double actualGoals = isHome ? form.getScoredAvgHome() : form.getScoredAvgAway();
        
        if (xgFor == null || actualGoals == null || xgFor == 0) {
            return false;
        }
        
        // If scoring significantly above xG, there's regression risk
        return (actualGoals - xgFor) > XG_REGRESSION_THRESHOLD;
    }

    private ConfidenceLevel determineConfidence(double absoluteScore) {
        if (absoluteScore >= THRESHOLD_STRONG) {
            return ConfidenceLevel.STRONG;
        } else if (absoluteScore >= THRESHOLD_MODERATE) {
            return ConfidenceLevel.MODERATE;
        }
        return ConfidenceLevel.WEAK;
    }

    private Map<String, Object> buildFactors(TeamMismatch best, List<TeamMismatch> all) {
        Map<String, Object> factors = new HashMap<>();
        
        // Team info
        factors.put("team", best.teamName);
        factors.put("isHomeTeam", best.isHomeTeam);
        factors.put("playingAtStrength", best.playingAtStrength);
        
        // Mismatch score and deltas
        factors.put("mismatchScore", best.mismatchScore);
        factors.put("ppgDelta", best.ppgDelta);
        factors.put("goalsDelta", best.goalsDelta);
        factors.put("concededDelta", best.concededDelta);
        factors.put("winsDelta", best.winsDelta);
        factors.put("cleanSheetDelta", best.cleanSheetDelta);
        
        // Streaks
        factors.put("hasWinningStreak", best.hasWinningStreak);
        factors.put("hasLosingStreak", best.hasLosingStreak);
        
        // Trends
        factors.put("scoringTrendUp", best.scoringTrendUp);
        factors.put("defensiveTrendUp", best.defensiveTrendUp);
        
        // Risk indicators
        factors.put("xgRegressionRisk", best.xgRegressionRisk);
        
        // Context weighting applied
        factors.put("homeAwayContextMultiplier", best.playingAtStrength ? HOME_AWAY_CONTEXT_MULTIPLIER : 1.0);
        
        // Analysis summary
        factors.put("teamsAnalyzed", all.size());
        
        // Momentum indicators summary
        int momentumFlags = 0;
        if (best.scoringTrendUp) momentumFlags++;
        if (best.defensiveTrendUp) momentumFlags++;
        if (best.hasWinningStreak) momentumFlags++;
        factors.put("positiveMomentumIndicators", momentumFlags);
        
        // Risk summary
        List<String> risks = new ArrayList<>();
        if (best.xgRegressionRisk) {
            risks.add("xG regression risk - scoring above expected");
        }
        if (best.hasLosingStreak && best.mismatchScore > 0) {
            risks.add("Conflicting signal - losing streak present");
        }
        factors.put("riskFlags", risks);

        return factors;
    }

    private String buildDescription(FixtureContext context, TeamMismatch mismatch, 
            RecommendationType type, ConfidenceLevel confidence) {
        String trend = type == RecommendationType.WINNING_FORM_MISMATCH
                ? "Hot streak - potentially undervalued"
                : "Cold streak - potentially overvalued";

        StringBuilder extras = new StringBuilder();
        if (mismatch.scoringTrendUp) {
            extras.append(", scoring trending up");
        }
        if (mismatch.defensiveTrendUp) {
            extras.append(", defense improving");
        }
        if (mismatch.xgRegressionRisk) {
            extras.append(", xG regression risk");
        }

        return MatchBriefCopy.narrate(MatchBriefCopy.Brief.builder()
                .confidence(confidence)
                .selection(type.getDisplayName() + " for " + mismatch.teamName)
                .context(context)
                .probabilityPct(Math.abs(mismatch.mismatchScore))
                .colourNote(trend + extras)
                .build());
    }

    private record TeamMismatch(
            String teamName,
            boolean isHomeTeam,
            double mismatchScore,
            double ppgDelta,
            double goalsDelta,
            double concededDelta,
            double winsDelta,
            double cleanSheetDelta,
            boolean hasWinningStreak,
            boolean hasLosingStreak,
            boolean scoringTrendUp,
            boolean defensiveTrendUp,
            boolean xgRegressionRisk,
            boolean playingAtStrength
    ) {}
}
