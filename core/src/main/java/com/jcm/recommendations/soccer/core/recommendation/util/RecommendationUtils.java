package com.jcm.recommendations.soccer.core.recommendation.util;

import com.jcm.recommendations.soccer.domain.TeamSeasonStats;

/**
 * Shared utility methods for recommendation engine calculations.
 * Consolidates common null-safe operations and statistical calculations
 * used across multiple recommendation engines.
 */
public final class RecommendationUtils {

    private RecommendationUtils() {
    }

    // ===== Null-safe value accessors =====

    public static int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    public static int safeInt(Integer value, int defaultValue) {
        return value != null ? value : defaultValue;
    }

    public static double safeDouble(Double value) {
        return value != null ? value : 0.0;
    }

    public static double safeDouble(Double value, double defaultValue) {
        return value != null ? value : defaultValue;
    }

    public static double safePercentage(Double value) {
        return value != null ? value : 50.0;
    }

    public static double safePercentage(Double value, double defaultValue) {
        return value != null ? value : defaultValue;
    }

    // ===== Goals calculations =====

    public static double calculateGoalsAvg(Integer goals, Integer matches) {
        if (matches == null || matches == 0) {
            return 0.0;
        }
        return safeInt(goals) / (double) matches;
    }

    public static double calculateGoalsAvg(Integer goals, Integer matches, double defaultValue) {
        if (matches == null || matches == 0) {
            return defaultValue;
        }
        return safeInt(goals) / (double) matches;
    }

    public static double normalizeGoals(double goalsAvg) {
        return Math.min(100.0, goalsAvg * 33.33);
    }

    public static double inverseNormalizeGoals(double goalsAvg) {
        return Math.max(0.0, 100.0 - (goalsAvg * 33.33));
    }

    // ===== Win/Loss/Draw percentage calculations =====

    public static double calculateWinPercentage(TeamSeasonStats stats, boolean isHome) {
        if (stats == null || stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 33.3;
        }
        int wins = isHome ? safeInt(stats.getSeasonWinsHome()) : safeInt(stats.getSeasonWinsAway());
        return (wins * 100.0) / stats.getMatchesPlayed();
    }

    public static double calculateLossPercentage(TeamSeasonStats stats, boolean isHome) {
        if (stats == null || stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 33.3;
        }
        int losses = isHome ? safeInt(stats.getSeasonLossesHome()) : safeInt(stats.getSeasonLossesAway());
        return (losses * 100.0) / stats.getMatchesPlayed();
    }

    public static double calculateDrawPercentage(TeamSeasonStats stats, boolean isHome) {
        if (stats == null || stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 25.0;
        }
        int draws = isHome ? safeInt(stats.getSeasonDrawsHome()) : safeInt(stats.getSeasonDrawsAway());
        return (draws * 100.0) / stats.getMatchesPlayed();
    }

    // ===== Clean sheet calculations =====

    public static double calculateCleanSheetPercentage(TeamSeasonStats stats, boolean isHome) {
        if (stats == null || stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 30.0;
        }
        int cleanSheets = isHome 
                ? safeInt(stats.getSeasonCleanSheetsHome()) 
                : safeInt(stats.getSeasonCleanSheetsAway());
        return (cleanSheets * 100.0) / stats.getMatchesPlayed();
    }

    public static double calculateCleanSheetPercentageOverall(TeamSeasonStats stats) {
        if (stats == null || stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 30.0;
        }
        int cleanSheets = safeInt(stats.getSeasonCleanSheetsOverall());
        return (cleanSheets * 100.0) / stats.getMatchesPlayed();
    }

    // ===== Failed to score calculations =====

    public static double calculateFailedToScorePercentage(TeamSeasonStats stats, boolean isHome) {
        if (stats == null || stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 20.0;
        }
        int fts = isHome 
                ? safeInt(stats.getSeasonFailedToScoreHome()) 
                : safeInt(stats.getSeasonFailedToScoreAway());
        return (fts * 100.0) / stats.getMatchesPlayed();
    }

    public static double calculateFailedToScorePercentageOverall(TeamSeasonStats stats) {
        if (stats == null || stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 20.0;
        }
        int fts = safeInt(stats.getSeasonFailedToScoreOverall());
        return (fts * 100.0) / stats.getMatchesPlayed();
    }

    // ===== Conceded calculations =====

    public static double calculateConcededAvg(TeamSeasonStats stats, boolean isHome) {
        if (stats == null || stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 1.0;
        }
        int conceded = isHome 
                ? safeInt(stats.getSeasonConcededHome()) 
                : safeInt(stats.getSeasonConcededAway());
        return conceded / (double) stats.getMatchesPlayed();
    }

    // ===== Scored percentage =====

    public static double calculateScoredPercentage(TeamSeasonStats stats) {
        if (stats == null || stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 50.0;
        }
        int scored = stats.getMatchesPlayed() - safeInt(stats.getSeasonFailedToScoreOverall());
        return (scored * 100.0) / stats.getMatchesPlayed();
    }

    // ===== PPG normalization =====

    public static double normalizePpg(double ppg) {
        return Math.min(100.0, ppg * 33.33);
    }

    // ===== Form calculations (based on last 5 matches) =====

    public static double calculateFormWinPercentage(Integer wins) {
        return (safeInt(wins) * 100.0) / 5.0;
    }

    public static double calculateFormLossPercentage(Integer losses) {
        return (safeInt(losses) * 100.0) / 5.0;
    }

    public static double calculateFormDrawPercentage(Integer draws) {
        return (safeInt(draws) * 100.0) / 5.0;
    }

    // ===== Score clamping =====

    public static double clampScore(double score) {
        return Math.min(100.0, Math.max(0.0, score));
    }

    public static double clampScore(double score, double min, double max) {
        return Math.min(max, Math.max(min, score));
    }
}
