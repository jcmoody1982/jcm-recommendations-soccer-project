package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
@Slf4j
public class FormMismatchRecommendationEngine implements RecommendationEngine {

    private static final double WEIGHT_PPG_DELTA = 0.30;
    private static final double WEIGHT_GOALS_DELTA = 0.25;
    private static final double WEIGHT_WINS_DELTA = 0.25;
    private static final double WEIGHT_CLEANSHEET_DELTA = 0.20;

    private static final double THRESHOLD_STRONG = 25.0;
    private static final double THRESHOLD_MODERATE = 15.0;
    private static final double STREAK_BONUS = 5.0;

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

        Recommendation recommendation = Recommendation.builder()
                .fixtureId(context.getFixture().getId())
                .homeTeamId(context.getHomeTeam().getId())
                .awayTeamId(context.getAwayTeam().getId())
                .homeTeamName(context.getHomeTeam().getName())
                .awayTeamName(context.getAwayTeam().getName())
                .matchDateUnix(context.getFixture().getDateUnix())
                .type(type)
                .confidence(confidence)
                .score(Math.abs(bestMismatch.mismatchScore))
                .market(bestMismatch.isHomeTeam ? "Back Home" : "Back Away")
                .description(buildDescription(context, bestMismatch, type, confidence))
                .factors(factors)
                .generatedAt(Instant.now())
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

        double ppgDelta = calculatePpgDelta(seasonStats, recentForm, isHomeTeam);
        double goalsDelta = calculateGoalsDelta(seasonStats, recentForm, isHomeTeam);
        double winsDelta = calculateWinsDelta(seasonStats, recentForm);
        double cleanSheetDelta = calculateCleanSheetDelta(seasonStats, recentForm);

        double mismatchScore = (ppgDelta * WEIGHT_PPG_DELTA)
                + (goalsDelta * WEIGHT_GOALS_DELTA)
                + (winsDelta * WEIGHT_WINS_DELTA)
                + (cleanSheetDelta * WEIGHT_CLEANSHEET_DELTA);

        boolean hasStreak = checkWinningStreak(recentForm);
        if (hasStreak && mismatchScore > 0) {
            mismatchScore += STREAK_BONUS;
        }

        boolean hasLosingStreak = checkLosingStreak(recentForm);
        if (hasLosingStreak && mismatchScore < 0) {
            mismatchScore -= STREAK_BONUS;
        }

        if (Math.abs(mismatchScore) < THRESHOLD_MODERATE) {
            return Optional.empty();
        }

        return Optional.of(new TeamMismatch(
                teamName,
                isHomeTeam,
                mismatchScore,
                ppgDelta,
                goalsDelta,
                winsDelta,
                cleanSheetDelta,
                hasStreak,
                hasLosingStreak
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
        double seasonAvg = isHome 
                ? calculateAverage(season.getSeasonGoalsHome(), season.getMatchesPlayed())
                : calculateAverage(season.getSeasonGoalsAway(), season.getMatchesPlayed());
        
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
        double formWinPct = (formWins * 100.0) / 5.0;

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

    private boolean checkWinningStreak(TeamRecentForm form) {
        return form.getWinsOverall() != null && form.getWinsOverall() >= 3;
    }

    private boolean checkLosingStreak(TeamRecentForm form) {
        return form.getLossesOverall() != null && form.getLossesOverall() >= 3;
    }

    private double calculateAverage(Integer total, Integer matches) {
        if (matches == null || matches == 0 || total == null) {
            return 0.0;
        }
        return total / (double) matches;
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
        
        factors.put("team", best.teamName);
        factors.put("isHomeTeam", best.isHomeTeam);
        factors.put("mismatchScore", best.mismatchScore);
        factors.put("ppgDelta", best.ppgDelta);
        factors.put("goalsDelta", best.goalsDelta);
        factors.put("winsDelta", best.winsDelta);
        factors.put("cleanSheetDelta", best.cleanSheetDelta);
        factors.put("hasWinningStreak", best.hasWinningStreak);
        factors.put("hasLosingStreak", best.hasLosingStreak);
        factors.put("teamsAnalyzed", all.size());

        return factors;
    }

    private String buildDescription(FixtureContext context, TeamMismatch mismatch, 
            RecommendationType type, ConfidenceLevel confidence) {
        String trend = type == RecommendationType.WINNING_FORM_MISMATCH 
                ? "Hot streak - potentially undervalued" 
                : "Cold streak - potentially overvalued";
        
        return String.format("%s confidence %s for %s (%.1f%% mismatch) - %s - %s vs %s",
                confidence.getDisplayName(),
                type.getDisplayName(),
                mismatch.teamName,
                Math.abs(mismatch.mismatchScore),
                trend,
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private record TeamMismatch(
            String teamName,
            boolean isHomeTeam,
            double mismatchScore,
            double ppgDelta,
            double goalsDelta,
            double winsDelta,
            double cleanSheetDelta,
            boolean hasWinningStreak,
            boolean hasLosingStreak
    ) {}
}
