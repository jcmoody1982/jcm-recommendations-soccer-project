package com.jcm.recommendations.soccer.core.recommendation.util;

import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationUtilsTest {

    @Nested
    @DisplayName("Safe value accessors")
    class SafeValueAccessors {

        @Test
        @DisplayName("safeInt returns 0 for null")
        void safeInt_returnsZeroForNull() {
            assertEquals(0, RecommendationUtils.safeInt(null));
        }

        @Test
        @DisplayName("safeInt returns value when not null")
        void safeInt_returnsValueWhenNotNull() {
            assertEquals(42, RecommendationUtils.safeInt(42));
        }

        @Test
        @DisplayName("safeInt with default returns default for null")
        void safeInt_withDefault_returnsDefaultForNull() {
            assertEquals(10, RecommendationUtils.safeInt(null, 10));
        }

        @Test
        @DisplayName("safeDouble returns 0.0 for null")
        void safeDouble_returnsZeroForNull() {
            assertEquals(0.0, RecommendationUtils.safeDouble(null));
        }

        @Test
        @DisplayName("safeDouble returns value when not null")
        void safeDouble_returnsValueWhenNotNull() {
            assertEquals(3.14, RecommendationUtils.safeDouble(3.14));
        }

        @Test
        @DisplayName("safeDouble with default returns default for null")
        void safeDouble_withDefault_returnsDefaultForNull() {
            assertEquals(5.5, RecommendationUtils.safeDouble(null, 5.5));
        }

        @Test
        @DisplayName("safePercentage returns 50.0 for null")
        void safePercentage_returnsDefaultForNull() {
            assertEquals(50.0, RecommendationUtils.safePercentage(null));
        }

        @Test
        @DisplayName("safePercentage returns value when not null")
        void safePercentage_returnsValueWhenNotNull() {
            assertEquals(75.5, RecommendationUtils.safePercentage(75.5));
        }

        @Test
        @DisplayName("safePercentage scales 0-1 rates to 0-100")
        void safePercentage_scalesUnitIntervalToPercent() {
            assertEquals(74.0, RecommendationUtils.safePercentage(0.74), 0.0001);
            assertEquals(100.0, RecommendationUtils.safePercentage(1.0), 0.0001);
            assertEquals(0.0, RecommendationUtils.safePercentage(0.0), 0.0001);
        }
    }

    @Nested
    @DisplayName("Goals calculations")
    class GoalsCalculations {

        @Test
        @DisplayName("calculateGoalsAvg returns 0 for null matches")
        void calculateGoalsAvg_returnsZeroForNullMatches() {
            assertEquals(0.0, RecommendationUtils.calculateGoalsAvg(10, null));
        }

        @Test
        @DisplayName("calculateGoalsAvg returns 0 for zero matches")
        void calculateGoalsAvg_returnsZeroForZeroMatches() {
            assertEquals(0.0, RecommendationUtils.calculateGoalsAvg(10, 0));
        }

        @Test
        @DisplayName("calculateGoalsAvg calculates correctly")
        void calculateGoalsAvg_calculatesCorrectly() {
            assertEquals(2.5, RecommendationUtils.calculateGoalsAvg(25, 10));
        }

        @Test
        @DisplayName("calculateGoalsAvg handles null goals")
        void calculateGoalsAvg_handlesNullGoals() {
            assertEquals(0.0, RecommendationUtils.calculateGoalsAvg(null, 10));
        }

        @Test
        @DisplayName("normalizeGoals caps at 100")
        void normalizeGoals_capsAt100() {
            double result = RecommendationUtils.normalizeGoals(5.0);
            assertTrue(result <= 100.0);
        }

        @Test
        @DisplayName("normalizeGoals converts correctly")
        void normalizeGoals_convertsCorrectly() {
            assertEquals(33.33, RecommendationUtils.normalizeGoals(1.0), 0.01);
        }

        @Test
        @DisplayName("inverseNormalizeGoals returns high value for low goals")
        void inverseNormalizeGoals_returnsHighForLowGoals() {
            double result = RecommendationUtils.inverseNormalizeGoals(0.5);
            assertTrue(result > 80.0);
        }

        @Test
        @DisplayName("inverseNormalizeGoals floors at 0")
        void inverseNormalizeGoals_floorsAtZero() {
            double result = RecommendationUtils.inverseNormalizeGoals(5.0);
            assertEquals(0.0, result);
        }
    }

    @Nested
    @DisplayName("Win/Loss/Draw percentage calculations")
    class PercentageCalculations {

        @Test
        @DisplayName("calculateWinPercentage returns default for null stats")
        void calculateWinPercentage_returnsDefaultForNullStats() {
            assertEquals(33.3, RecommendationUtils.calculateWinPercentage(null, true));
        }

        @Test
        @DisplayName("calculateWinPercentage returns default for null matches played")
        void calculateWinPercentage_returnsDefaultForNullMatchesPlayed() {
            TeamSeasonStats stats = new TeamSeasonStats();
            stats.setMatchesPlayed(null);
            assertEquals(33.3, RecommendationUtils.calculateWinPercentage(stats, true));
        }

        @Test
        @DisplayName("calculateWinPercentage calculates home wins correctly")
        void calculateWinPercentage_calculatesHomeWinsCorrectly() {
            TeamSeasonStats stats = new TeamSeasonStats();
            stats.setMatchesPlayed(10);
            stats.setSeasonWinsHome(6);
            assertEquals(60.0, RecommendationUtils.calculateWinPercentage(stats, true));
        }

        @Test
        @DisplayName("calculateWinPercentage calculates away wins correctly")
        void calculateWinPercentage_calculatesAwayWinsCorrectly() {
            TeamSeasonStats stats = new TeamSeasonStats();
            stats.setMatchesPlayed(10);
            stats.setSeasonWinsAway(3);
            assertEquals(30.0, RecommendationUtils.calculateWinPercentage(stats, false));
        }

        @Test
        @DisplayName("calculateDrawPercentage calculates correctly")
        void calculateDrawPercentage_calculatesCorrectly() {
            TeamSeasonStats stats = new TeamSeasonStats();
            stats.setMatchesPlayed(10);
            stats.setSeasonDrawsHome(4);
            assertEquals(40.0, RecommendationUtils.calculateDrawPercentage(stats, true));
        }
    }

    @Nested
    @DisplayName("Clean sheet calculations")
    class CleanSheetCalculations {

        @Test
        @DisplayName("calculateCleanSheetPercentage returns default for null stats")
        void calculateCleanSheetPercentage_returnsDefaultForNullStats() {
            assertEquals(30.0, RecommendationUtils.calculateCleanSheetPercentage(null, true));
        }

        @Test
        @DisplayName("calculateCleanSheetPercentage calculates home correctly")
        void calculateCleanSheetPercentage_calculatesHomeCorrectly() {
            TeamSeasonStats stats = new TeamSeasonStats();
            stats.setMatchesPlayed(10);
            stats.setSeasonCleanSheetsHome(5);
            assertEquals(50.0, RecommendationUtils.calculateCleanSheetPercentage(stats, true));
        }

        @Test
        @DisplayName("calculateCleanSheetPercentageOverall works correctly")
        void calculateCleanSheetPercentageOverall_worksCorrectly() {
            TeamSeasonStats stats = new TeamSeasonStats();
            stats.setMatchesPlayed(20);
            stats.setSeasonCleanSheetsOverall(8);
            assertEquals(40.0, RecommendationUtils.calculateCleanSheetPercentageOverall(stats));
        }
    }

    @Nested
    @DisplayName("Failed to score calculations")
    class FailedToScoreCalculations {

        @Test
        @DisplayName("calculateFailedToScorePercentage returns default for null")
        void calculateFailedToScorePercentage_returnsDefaultForNull() {
            assertEquals(20.0, RecommendationUtils.calculateFailedToScorePercentage(null, true));
        }

        @Test
        @DisplayName("calculateFailedToScorePercentage calculates correctly")
        void calculateFailedToScorePercentage_calculatesCorrectly() {
            TeamSeasonStats stats = new TeamSeasonStats();
            stats.setMatchesPlayed(10);
            stats.setSeasonFailedToScoreHome(3);
            assertEquals(30.0, RecommendationUtils.calculateFailedToScorePercentage(stats, true));
        }
    }

    @Nested
    @DisplayName("Form calculations")
    class FormCalculations {

        @Test
        @DisplayName("calculateFormWinPercentage uses 5 matches as base")
        void calculateFormWinPercentage_usesFiveMatchesBase() {
            assertEquals(60.0, RecommendationUtils.calculateFormWinPercentage(3));
        }

        @Test
        @DisplayName("calculateFormWinPercentage handles null")
        void calculateFormWinPercentage_handlesNull() {
            assertEquals(0.0, RecommendationUtils.calculateFormWinPercentage(null));
        }
    }

    @Nested
    @DisplayName("Score clamping")
    class ScoreClamping {

        @Test
        @DisplayName("clampScore caps at 100")
        void clampScore_capsAt100() {
            assertEquals(100.0, RecommendationUtils.clampScore(150.0));
        }

        @Test
        @DisplayName("clampScore floors at 0")
        void clampScore_floorsAtZero() {
            assertEquals(0.0, RecommendationUtils.clampScore(-50.0));
        }

        @Test
        @DisplayName("clampScore leaves valid values unchanged")
        void clampScore_leavesValidUnchanged() {
            assertEquals(75.0, RecommendationUtils.clampScore(75.0));
        }

        @Test
        @DisplayName("clampScore with custom range works")
        void clampScore_withCustomRange_works() {
            assertEquals(50.0, RecommendationUtils.clampScore(30.0, 50.0, 100.0));
            assertEquals(80.0, RecommendationUtils.clampScore(90.0, 50.0, 80.0));
        }
    }

    @Nested
    @DisplayName("PPG normalization")
    class PpgNormalization {

        @Test
        @DisplayName("normalizePpg converts correctly")
        void normalizePpg_convertsCorrectly() {
            assertEquals(66.66, RecommendationUtils.normalizePpg(2.0), 0.01);
        }

        @Test
        @DisplayName("normalizePpg caps at 100")
        void normalizePpg_capsAt100() {
            assertEquals(100.0, RecommendationUtils.normalizePpg(4.0));
        }
    }

    @Nested
    @DisplayName("Conceded calculations")
    class ConcededCalculations {

        @Test
        @DisplayName("calculateConcededAvg returns default for null")
        void calculateConcededAvg_returnsDefaultForNull() {
            assertEquals(1.0, RecommendationUtils.calculateConcededAvg(null, true));
        }

        @Test
        @DisplayName("calculateConcededAvg calculates correctly")
        void calculateConcededAvg_calculatesCorrectly() {
            TeamSeasonStats stats = new TeamSeasonStats();
            stats.setMatchesPlayed(10);
            stats.setSeasonConcededHome(12);
            assertEquals(1.2, RecommendationUtils.calculateConcededAvg(stats, true));
        }
    }

    @Nested
    @DisplayName("Scored percentage")
    class ScoredPercentage {

        @Test
        @DisplayName("calculateScoredPercentage returns default for null")
        void calculateScoredPercentage_returnsDefaultForNull() {
            assertEquals(50.0, RecommendationUtils.calculateScoredPercentage(null));
        }

        @Test
        @DisplayName("calculateScoredPercentage calculates correctly")
        void calculateScoredPercentage_calculatesCorrectly() {
            TeamSeasonStats stats = new TeamSeasonStats();
            stats.setMatchesPlayed(10);
            stats.setSeasonFailedToScoreOverall(2);
            assertEquals(80.0, RecommendationUtils.calculateScoredPercentage(stats));
        }
    }
}
