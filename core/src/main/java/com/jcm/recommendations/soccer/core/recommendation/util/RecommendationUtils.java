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
        return value == null ? 50.0 : normalizePercentage(value);
    }

    public static double safePercentage(Double value, double defaultValue) {
        return value == null ? defaultValue : normalizePercentage(value);
    }

    /**
     * FootyStats sometimes emits rates as 0–1 (0.74) and sometimes as 0–100 (74).
     * Values in (0, 1] are treated as fractions. Exact 0 stays 0.
     */
    public static double normalizePercentage(double value) {
        if (value > 0.0 && value <= 1.0) {
            return value * 100.0;
        }
        return value;
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

    /**
     * Venue match count from W+D+L (home or away). Prefer this over matchesPlayed/2.
     */
    public static int calculateMatchesAtVenue(TeamSeasonStats stats, boolean isHome) {
        if (stats == null) {
            return 0;
        }
        if (isHome) {
            return safeInt(stats.getSeasonWinsHome())
                    + safeInt(stats.getSeasonDrawsHome())
                    + safeInt(stats.getSeasonLossesHome());
        }
        return safeInt(stats.getSeasonWinsAway())
                + safeInt(stats.getSeasonDrawsAway())
                + safeInt(stats.getSeasonLossesAway());
    }

    /**
     * Failed-to-score % at venue, using venue match count (not overall matchesPlayed).
     */
    public static double calculateVenueFailedToScorePercentage(TeamSeasonStats stats, boolean isHome) {
        int matches = calculateMatchesAtVenue(stats, isHome);
        if (matches == 0) {
            return 100.0;
        }
        int fts = isHome
                ? safeInt(stats.getSeasonFailedToScoreHome())
                : safeInt(stats.getSeasonFailedToScoreAway());
        return (fts * 100.0) / matches;
    }

    /**
     * Scored-in-match % at venue (= 100 - venue FTS %).
     */
    public static double calculateVenueScoredPercentage(TeamSeasonStats stats, boolean isHome) {
        return 100.0 - calculateVenueFailedToScorePercentage(stats, isHome);
    }

    public static double calculateVenueGoalsAvg(TeamSeasonStats stats, boolean isHome) {
        int matches = calculateMatchesAtVenue(stats, isHome);
        if (matches == 0) {
            return 0.0;
        }
        int goals = isHome ? safeInt(stats.getSeasonGoalsHome()) : safeInt(stats.getSeasonGoalsAway());
        return goals / (double) matches;
    }

    public static double calculateVenueConcededAvg(TeamSeasonStats stats, boolean isHome) {
        int matches = calculateMatchesAtVenue(stats, isHome);
        if (matches == 0) {
            return 0.0;
        }
        int conceded = isHome ? safeInt(stats.getSeasonConcededHome()) : safeInt(stats.getSeasonConcededAway());
        return conceded / (double) matches;
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

    // ===== Poisson goal probabilities =====

    /**
     * Probability (0-100) of at least {@code minGoals} goals given an expected total of
     * {@code expectedGoals}, under a Poisson distribution.
     *
     * <p>Prefer this over rescaling a goal average onto a 0-100 axis. A rescale has to be clamped
     * at the top, which ties every high-scoring fixture at exactly 100 and destroys the rank order
     * the Elite board depends on. A Poisson tail approaches 100 without reaching it, so the best
     * fixtures stay separable and the number means what it says.
     */
    public static double poissonAtLeast(double expectedGoals, int minGoals) {
        if (minGoals <= 0) {
            return 100.0;
        }
        if (expectedGoals <= 0) {
            return 0.0;
        }
        // P(N >= k) = 1 - sum of P(N = i) for i < k
        double term = Math.exp(-expectedGoals);
        double cumulativeBelow = term;
        for (int i = 1; i < minGoals; i++) {
            term *= expectedGoals / i;
            cumulativeBelow += term;
        }
        return clampScore((1.0 - cumulativeBelow) * 100.0);
    }

    /** Goal counts past this contribute nothing meaningful to a scoreline sum. */
    private static final int POISSON_MAX_GOALS = 12;

    /**
     * Probability (0-100) that both sides score the same number of goals, treating each side's
     * goals as independent Poisson draws.
     *
     * <p>Sums P(home = k) x P(away = k) over plausible scorelines, which is the direct statement of
     * "the match is level at the end". Proxies for draw likelihood - how close the teams are, how
     * few goals they score - are already implied by the two expectations, so deriving the
     * probability here avoids scoring those proxies separately and then having to guess how they
     * combine.
     */
    public static double poissonDrawProbability(double lambdaHome, double lambdaAway) {
        if (lambdaHome < 0 || lambdaAway < 0) {
            return 0.0;
        }

        double homeTerm = Math.exp(-lambdaHome);
        double awayTerm = Math.exp(-lambdaAway);
        double drawProbability = homeTerm * awayTerm;

        for (int k = 1; k <= POISSON_MAX_GOALS; k++) {
            homeTerm *= lambdaHome / k;
            awayTerm *= lambdaAway / k;
            drawProbability += homeTerm * awayTerm;
        }

        return clampScore(drawProbability * 100.0);
    }

    // ===== Market prices =====

    /**
     * Home, draw and away probabilities (0-100) with the bookmaker's margin removed.
     *
     * <p>Raw inverted odds sum to more than one, so using them directly overstates every outcome.
     * Returns null when any leg is missing or non-positive.
     */
    public static double[] fairOutcomeProbabilities(Double odds1, Double oddsX, Double odds2) {
        if (odds1 == null || oddsX == null || odds2 == null
                || odds1 <= 0 || oddsX <= 0 || odds2 <= 0) {
            return null;
        }

        double raw1 = 1.0 / odds1;
        double rawX = 1.0 / oddsX;
        double raw2 = 1.0 / odds2;
        double overround = raw1 + rawX + raw2;
        if (overround <= 0) {
            return null;
        }

        return new double[]{
                (raw1 / overround) * 100.0,
                (rawX / overround) * 100.0,
                (raw2 / overround) * 100.0
        };
    }
}
