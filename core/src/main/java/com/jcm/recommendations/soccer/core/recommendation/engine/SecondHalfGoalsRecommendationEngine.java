package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.core.recommendation.util.RecommendationFactory;
import com.jcm.recommendations.soccer.core.recommendation.util.MatchBriefCopy;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.*;

/**
 * UC-016: Second Half Goals Recommendations
 *
 * Predicts likelihood of goals in the second half (Over 0.5 2H, Over 1.5 2H).
 *
 * Season goals and xG (×0.55 proxy for the 2H share) feed a single expected-second-half-goals
 * figure, which a Poisson tail turns into the probability of the line being marketed. The score
 * published is therefore the probability of that specific line, so Over 1.5 2H is scored as
 * P(2+ second-half goals) rather than sharing a scale with Over 0.5 2H.
 */
@Component
@Slf4j
public class SecondHalfGoalsRecommendationEngine implements RecommendationEngine {

    // Second half share of total goals (slightly higher than 1H)
    private static final double SECOND_HALF_RATIO = 0.55;

    /**
     * The two lines this engine can market. The published score is the probability of the line
     * actually being recommended, so each line carries its own thresholds: a second half averages
     * about 1.45 goals, putting Over 0.5 in the 68-86% band and Over 1.5 in the 33-60% band. There
     * is no single scale on which both lines can be read.
     */
    private enum Line {
        OVER_05(1, "Over 0.5 2H Goals", 80.0, 72.0),
        OVER_15(2, "Over 1.5 2H Goals", 54.0, 46.0);

        private final int goalsNeeded;
        private final String market;
        private final double thresholdStrong;
        private final double thresholdModerate;

        Line(int goalsNeeded, String market, double thresholdStrong, double thresholdModerate) {
            this.goalsNeeded = goalsNeeded;
            this.market = market;
            this.thresholdStrong = thresholdStrong;
            this.thresholdModerate = thresholdModerate;
        }
    }

    /**
     * Bounds on the combined expected-goals adjustment, so the signals below corroborate each
     * other rather than compound.
     */
    private static final double ADJUST_MIN = 0.85;
    private static final double ADJUST_MAX = 1.20;

    /**
     * Over 1.5 2H needs a clear volume outlier. The provider publishes no second-half potentials,
     * so unlike the first-half engine there is no independent read to corroborate the line and the
     * expectation has to carry the decision alone.
     */
    private static final double OVER_15_MIN_EXPECTED_GOALS = 1.8;

    // xG-based combined rating bands (reported as match colour)
    private static final double XG_HIGH_THRESHOLD = 3.0;
    private static final double XG_ABOVE_AVG_THRESHOLD = 2.5;
    private static final double XG_LOW_THRESHOLD = 2.0;

    // Goal spread: clean sheets say how goals are distributed, which the averages alone do not
    private static final double GOALS_SPREAD_CS_THRESHOLD = 25.0;
    private static final double GOALS_SPREAD_FACTOR = 1.10;
    private static final double GOALS_CONCENTRATED_CS_THRESHOLD = 40.0;
    private static final double GOALS_CONCENTRATED_FACTOR = 0.90;

    // Late game intensity thresholds (based on cards per game)
    private static final double HIGH_INTENSITY_CARDS_THRESHOLD = 4.0;
    private static final double HIGH_INTENSITY_FACTOR = 1.10;
    private static final double LOW_INTENSITY_CARDS_THRESHOLD = 2.5;
    private static final double LOW_INTENSITY_FACTOR = 0.95;

    // Profile bands reported as match colour
    private static final double STRONG_FINISHER_GOALS = 3.0;
    private static final double BALANCED_FINISHER_GOALS = 2.5;
    private static final double FRONT_LOADED_GOALS = 2.0;
    private static final double VULNERABLE_LATE_GOALS = 2.5;
    private static final double SOLID_LATE_GOALS = 1.5;

    // Filter: minimum expected 2H goals to generate a recommendation
    private static final double FILTER_MIN_2H_GOALS = 0.9;

    /** The chain from raw stats through to the published probability, kept together for reporting. */
    private record Estimate(
            double baseExpected2HGoals,
            double adjustment,
            double expected2HGoals,
            Line line,
            double score) {}

    @Override
    public RecommendationType getType() {
        return RecommendationType.SECOND_HALF_GOALS;
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        log.debug("Analyzing Second Half Goals for fixture: fixtureId={}, {} vs {}", 
                context.getFixture().getId(),
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());

        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        // Calculate expected 2H goals (use ratio of full-time expected)
        double baseExpectedGoals2H = calculateExpected2HGoals(homeStats, awayStats);

        if (baseExpectedGoals2H < FILTER_MIN_2H_GOALS) {
            log.debug("Fixture failed Second Half Goals filter: fixtureId={}, expected2HGoals={}", 
                    context.getFixture().getId(), baseExpectedGoals2H);
            return Optional.empty();
        }

        // The signals below describe how many goals to expect, so they adjust the expectation
        // rather than the published probability. Poisson then bounds how far they can move it.
        double adjustment = calculateExpectedGoalsAdjustment(homeStats, awayStats);
        double expectedGoals2H = baseExpectedGoals2H * adjustment;

        Line line = selectLine(expectedGoals2H);
        double score = clampScore(poissonAtLeast(expectedGoals2H, line.goalsNeeded));

        ConfidenceLevel confidence = determineConfidence(score, line);

        if (confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        Estimate estimate = new Estimate(baseExpectedGoals2H, adjustment, expectedGoals2H, line, score);
        Map<String, Object> factors = buildFactors(context, estimate, homeStats, awayStats);

        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(RecommendationType.SECOND_HALF_GOALS)
                .confidence(confidence)
                .score(score)
                .market(line.market)
                .odds(null)  // No 2H-specific odds in our data model
                .description(buildDescription(context, confidence, expectedGoals2H, line.market))
                .factors(factors)
                .build();

        log.info("Second Half Goals recommendation generated: fixtureId={}, expected2HGoals={}, score={}, confidence={}, market={}", 
                context.getFixture().getId(), String.format("%.2f", expectedGoals2H), 
                String.format("%.1f", score), confidence, line.market);

        return Optional.of(recommendation);
    }

    private double calculateExpected2HGoals(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        double homeScoredAvg = halfScoredAvg(homeStats, true, SECOND_HALF_RATIO);
        double awayScoredAvg = halfScoredAvg(awayStats, false, SECOND_HALF_RATIO);
        double homeConcededAvg = halfConcededAvg(homeStats, true, SECOND_HALF_RATIO);
        double awayConcededAvg = halfConcededAvg(awayStats, false, SECOND_HALF_RATIO);

        double actualExpected = (homeScoredAvg + awayScoredAvg + homeConcededAvg + awayConcededAvg) / 2.0;

        Double homeXgFor = homeStats.getXgForAvgHome();
        Double awayXgFor = awayStats.getXgForAvgAway();
        Double homeXgAgainst = homeStats.getXgAgainstAvgHome();
        Double awayXgAgainst = awayStats.getXgAgainstAvgAway();

        if (homeXgFor != null && awayXgFor != null && homeXgAgainst != null && awayXgAgainst != null) {
            double xgExpected = (homeXgFor + awayXgFor + homeXgAgainst + awayXgAgainst) / 2.0 * SECOND_HALF_RATIO;
            return (actualExpected * 0.6) + (xgExpected * 0.4);
        }

        return actualExpected;
    }

    private Line selectLine(double expectedGoals2H) {
        return expectedGoals2H >= OVER_15_MIN_EXPECTED_GOALS ? Line.OVER_15 : Line.OVER_05;
    }

    /**
     * Combined multiplier on expected second-half goals. The scored and conceded averages and the
     * xG level are inputs to the expectation itself, so only signals holding information beyond
     * goal volume appear here: how goals are spread across matches, and match intensity.
     */
    private double calculateExpectedGoalsAdjustment(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        double adjustment = goalSpreadFactor(homeStats, awayStats) * intensityFactor(homeStats, awayStats);

        return Math.max(ADJUST_MIN, Math.min(ADJUST_MAX, adjustment));
    }

    /**
     * Two sides can share a conceded average while keeping very different numbers of clean sheets.
     * Where clean sheets are rare the goals are spread across more matches, so any given half is
     * likelier to contain one.
     */
    private double goalSpreadFactor(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        // An absent clean sheet count reads as zero clean sheets, which is the strongest possible
        // reading of this signal. Treat missing data as no information instead.
        if (!hasCleanSheetData(homeStats, true) || !hasCleanSheetData(awayStats, false)) {
            return 1.0;
        }

        double avgCsPct = averageCleanSheetPct(homeStats, awayStats);

        if (avgCsPct < GOALS_SPREAD_CS_THRESHOLD) {
            log.debug("Goals spread widely (few clean sheets): avgCS%={}", avgCsPct);
            return GOALS_SPREAD_FACTOR;
        }
        if (avgCsPct > GOALS_CONCENTRATED_CS_THRESHOLD) {
            log.debug("Goals concentrated (frequent clean sheets): avgCS%={}", avgCsPct);
            return GOALS_CONCENTRATED_FACTOR;
        }

        return 1.0;
    }

    private double intensityFactor(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        double combinedCards = combinedCardsAvg(homeStats, awayStats);

        if (combinedCards >= HIGH_INTENSITY_CARDS_THRESHOLD) {
            log.debug("High intensity matchup (cards): combinedCards={}", combinedCards);
            return HIGH_INTENSITY_FACTOR;
        }
        if (combinedCards < LOW_INTENSITY_CARDS_THRESHOLD) {
            log.debug("Low intensity matchup (cards): combinedCards={}", combinedCards);
            return LOW_INTENSITY_FACTOR;
        }

        return 1.0;
    }

    private static boolean hasCleanSheetData(TeamSeasonStats stats, boolean home) {
        return (home ? stats.getSeasonCleanSheetsHome() : stats.getSeasonCleanSheetsAway()) != null;
    }

    private static double averageCleanSheetPct(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        return (calculateCleanSheetPercentage(homeStats, true)
                + calculateCleanSheetPercentage(awayStats, false)) / 2.0;
    }

    private static double combinedCardsAvg(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        return safeDouble(homeStats.getCardsAvgHome(), 2.0) + safeDouble(awayStats.getCardsAvgAway(), 2.0);
    }

    private boolean hasXgData(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        return homeStats.getXgForAvgHome() != null 
                && awayStats.getXgForAvgAway() != null
                && homeStats.getXgAgainstAvgHome() != null 
                && awayStats.getXgAgainstAvgAway() != null;
    }

    private double calculateLateGameIntensity(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        // Use cards as proxy for late game intensity
        // More cards typically correlates with more open, attacking play in 2H
        double homeCards = safeDouble(homeStats.getCardsAvgHome(), 2.0);
        double awayCards = safeDouble(awayStats.getCardsAvgAway(), 2.0);
        double combinedCards = homeCards + awayCards;
        
        // Normalize to percentage scale (typical range 3-6 cards combined)
        return Math.min(100.0, (combinedCards / 6.0) * 100.0);
    }

    private double calculateFitnessIndicator(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        // Teams that score more than they concede tend to have better fitness (can push late)
        // Use goal difference as a proxy for fitness/stamina
        double homeGoalDiff = calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 0.0) 
                - calculateGoalsAvg(homeStats.getSeasonConcededHome(), homeStats.getMatchesPlayed(), 0.0);
        double awayGoalDiff = calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 0.0)
                - calculateGoalsAvg(awayStats.getSeasonConcededAway(), awayStats.getMatchesPlayed(), 0.0);
        
        // Normalize to percentage (range -2 to +2 typical)
        double avgGoalDiff = (homeGoalDiff + awayGoalDiff) / 2.0;
        return Math.min(100.0, Math.max(0.0, 50.0 + (avgGoalDiff * 25.0)));
    }

    private double calculateMatchSituationFactor(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        // Close matches (high draw %, competitive teams) tend to have more 2H goals
        // As teams push for a result in the second half
        double homeDrawPct = calculateDrawPercentage(homeStats, true);
        double awayDrawPct = calculateDrawPercentage(awayStats, false);
        double avgDrawPct = (homeDrawPct + awayDrawPct) / 2.0;
        
        // Also consider if teams are attacking (high goals avg = more open play)
        double homeGoalsAvg = calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 1.0);
        double awayGoalsAvg = calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 1.0);
        double combinedGoalsIndicator = Math.min(100.0, (homeGoalsAvg + awayGoalsAvg) * 30.0);
        
        // Blend draw likelihood with attacking intent
        return (avgDrawPct * 0.4) + (combinedGoalsIndicator * 0.6);
    }

    private ConfidenceLevel determineConfidence(double score, Line line) {
        if (score >= line.thresholdStrong) {
            return ConfidenceLevel.STRONG;
        } else if (score >= line.thresholdModerate) {
            return ConfidenceLevel.MODERATE;
        }
        return ConfidenceLevel.WEAK;
    }

    private Map<String, Object> buildFactors(FixtureContext context, Estimate estimate,
            TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        Map<String, Object> factors = new HashMap<>();

        double expected2HGoals = estimate.expected2HGoals();
        Line line = estimate.line();

        // Expected goals
        factors.put("expected2HGoals", expected2HGoals);
        factors.put("baseExpected2HGoals", estimate.baseExpected2HGoals());
        factors.put("expectedGoalsAdjustment", estimate.adjustment());
        factors.put("secondHalfRatioUsed", SECOND_HALF_RATIO);
        factors.put("halfStatsFromApi", hasHalfGoalStats(homeStats, awayStats));

        // How the published probability was arrived at
        factors.put("line", line.market);
        factors.put("goalsNeeded", line.goalsNeeded);
        factors.put("poissonProbability", poissonAtLeast(expected2HGoals, line.goalsNeeded));

        // Full-time expected for reference
        factors.put("expectedFullTimeGoals", expected2HGoals / SECOND_HALF_RATIO);
        
        double home2HScoredProxy = halfScoredAvg(homeStats, true, SECOND_HALF_RATIO);
        double away2HScoredProxy = halfScoredAvg(awayStats, false, SECOND_HALF_RATIO);
        double home2HConcededProxy = halfConcededAvg(homeStats, true, SECOND_HALF_RATIO);
        double away2HConcededProxy = halfConcededAvg(awayStats, false, SECOND_HALF_RATIO);
        
        factors.put("home2HScoredProxyAvg", home2HScoredProxy);
        factors.put("away2HScoredProxyAvg", away2HScoredProxy);
        factors.put("home2HConcededProxyAvg", home2HConcededProxy);
        factors.put("away2HConcededProxyAvg", away2HConcededProxy);

        // Late game intensity
        double homeCards = safeDouble(homeStats.getCardsAvgHome(), 2.0);
        double awayCards = safeDouble(awayStats.getCardsAvgAway(), 2.0);
        factors.put("homeCardsAvg", homeCards);
        factors.put("awayCardsAvg", awayCards);
        factors.put("combinedCardsAvg", homeCards + awayCards);
        factors.put("lateGameIntensityScore", calculateLateGameIntensity(homeStats, awayStats));
        
        // Fitness indicator
        double homeGoalDiff = calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 0.0) 
                - calculateGoalsAvg(homeStats.getSeasonConcededHome(), homeStats.getMatchesPlayed(), 0.0);
        double awayGoalDiff = calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 0.0)
                - calculateGoalsAvg(awayStats.getSeasonConcededAway(), awayStats.getMatchesPlayed(), 0.0);
        factors.put("homeGoalDifferencePerGame", homeGoalDiff);
        factors.put("awayGoalDifferencePerGame", awayGoalDiff);
        factors.put("fitnessIndicatorScore", calculateFitnessIndicator(homeStats, awayStats));
        
        // Match situation
        factors.put("homeDrawPct", calculateDrawPercentage(homeStats, true));
        factors.put("awayDrawPct", calculateDrawPercentage(awayStats, false));
        factors.put("matchSituationScore", calculateMatchSituationFactor(homeStats, awayStats));

        // xG data
        boolean xgAvailable = hasXgData(homeStats, awayStats);
        factors.put("xgDataAvailable", xgAvailable);
        
        if (xgAvailable) {
            double homeXg = safeDouble(homeStats.getXgForAvgHome());
            double awayXg = safeDouble(awayStats.getXgForAvgAway());
            double homeXga = safeDouble(homeStats.getXgAgainstAvgHome());
            double awayXga = safeDouble(awayStats.getXgAgainstAvgAway());
            
            factors.put("homeXgForAvgHome", homeXg);
            factors.put("awayXgForAvgAway", awayXg);
            factors.put("homeXgAgainstAvgHome", homeXga);
            factors.put("awayXgAgainstAvgAway", awayXga);
            factors.put("combinedXg", homeXg + awayXg);
            factors.put("home2HXgProxy", homeXg * SECOND_HALF_RATIO);
            factors.put("away2HXgProxy", awayXg * SECOND_HALF_RATIO);
            
            // xG rating
            double combinedXg = homeXg + awayXg;
            String xgRating;
            if (combinedXg > XG_HIGH_THRESHOLD) {
                xgRating = "High-scoring potential";
            } else if (combinedXg >= XG_ABOVE_AVG_THRESHOLD) {
                xgRating = "Above average";
            } else if (combinedXg < XG_LOW_THRESHOLD) {
                xgRating = "Low-scoring potential";
            } else {
                xgRating = "Average";
            }
            factors.put("xgRating", xgRating);
        }

        // Late scorer/finisher profile analysis
        List<String> positiveIndicators = new ArrayList<>();
        List<String> riskFlags = new ArrayList<>();
        
        double homeGoalsAvg = calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 1.0);
        double awayGoalsAvg = calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 1.0);
        double combinedGoals = homeGoalsAvg + awayGoalsAvg;
        
        double homeCsPct = calculateCleanSheetPercentage(homeStats, true);
        double awayCsPct = calculateCleanSheetPercentage(awayStats, false);
        double avgCsPct = (homeCsPct + awayCsPct) / 2.0;
        
        factors.put("combinedGoalsAvg", combinedGoals);
        factors.put("avgCleanSheetPct", avgCsPct);
        
        if (combinedGoals >= STRONG_FINISHER_GOALS && avgCsPct < GOALS_SPREAD_CS_THRESHOLD) {
            factors.put("finisherProfile", "Strong finisher");
            positiveIndicators.add("Strong late scoring profile");
        } else if (combinedGoals >= BALANCED_FINISHER_GOALS) {
            factors.put("finisherProfile", "Balanced");
        } else if (combinedGoals < FRONT_LOADED_GOALS) {
            factors.put("finisherProfile", "Front-loaded");
            riskFlags.add("Teams tend to score early");
        }
        
        // Late conceder analysis
        double homeConcededAvg = calculateGoalsAvg(homeStats.getSeasonConcededHome(), homeStats.getMatchesPlayed(), 1.0);
        double awayConcededAvg = calculateGoalsAvg(awayStats.getSeasonConcededAway(), awayStats.getMatchesPlayed(), 1.0);
        double combinedConceded = homeConcededAvg + awayConcededAvg;
        factors.put("combinedConcededAvg", combinedConceded);
        
        if (combinedConceded >= VULNERABLE_LATE_GOALS && avgCsPct < GOALS_SPREAD_CS_THRESHOLD) {
            factors.put("lateConcedeProfile", "Vulnerable late");
            positiveIndicators.add("Both teams vulnerable late");
        } else if (combinedConceded < SOLID_LATE_GOALS && avgCsPct > GOALS_CONCENTRATED_CS_THRESHOLD) {
            factors.put("lateConcedeProfile", "Strong late defense");
            riskFlags.add("Both teams solid defensively late");
        }
        
        // Intensity analysis
        double combinedCards = homeCards + awayCards;
        if (combinedCards >= HIGH_INTENSITY_CARDS_THRESHOLD) {
            factors.put("intensityProfile", "High intensity");
            positiveIndicators.add("High-intensity matchup (more late goals likely)");
        } else if (combinedCards < LOW_INTENSITY_CARDS_THRESHOLD) {
            factors.put("intensityProfile", "Low intensity");
            riskFlags.add("Low-intensity matchup");
        } else {
            factors.put("intensityProfile", "Average");
        }
        
        factors.put("positiveIndicators", positiveIndicators);
        factors.put("riskFlags", riskFlags);
        factors.put("calculatedScore", estimate.score());
        
        return factors;
    }

    private String buildDescription(FixtureContext context, ConfidenceLevel confidence, 
            double expected2HGoals, String market) {
        StringBuilder colour = new StringBuilder();

        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        if (hasXgData(homeStats, awayStats)) {
            double combinedXg = safeDouble(homeStats.getXgForAvgHome()) + safeDouble(awayStats.getXgForAvgAway());
            if (combinedXg > XG_HIGH_THRESHOLD) {
                colour.append(String.format("High combined xG (%.2f)", combinedXg));
            }
        }

        double homeCards = safeDouble(homeStats.getCardsAvgHome(), 2.0);
        double awayCards = safeDouble(awayStats.getCardsAvgAway(), 2.0);
        if (homeCards + awayCards >= HIGH_INTENSITY_CARDS_THRESHOLD) {
            if (!colour.isEmpty()) {
                colour.append(". ");
            }
            colour.append("High-intensity matchup expected after the break");
        }

        return MatchBriefCopy.narrate(MatchBriefCopy.Brief.builder()
                .confidence(confidence)
                .selection(market)
                .context(context)
                .expected(expected2HGoals, "expected 2H goals")
                .colourNote(colour.isEmpty() ? "Second half goals potential after the interval" : colour.toString())
                .build());
    }

    private static double halfScoredAvg(TeamSeasonStats stats, boolean home, double fallbackRatio) {
        Double halfAvg = home ? stats.getScoredAvg2hHome() : stats.getScoredAvg2hAway();
        if (halfAvg != null) {
            return halfAvg;
        }
        Integer goals = home ? stats.getSeasonGoalsHome() : stats.getSeasonGoalsAway();
        return calculateGoalsAvg(goals, stats.getMatchesPlayed(), 1.0) * fallbackRatio;
    }

    private static double halfConcededAvg(TeamSeasonStats stats, boolean home, double fallbackRatio) {
        Double halfAvg = home ? stats.getConcededAvg2hHome() : stats.getConcededAvg2hAway();
        if (halfAvg != null) {
            return halfAvg;
        }
        Integer conceded = home ? stats.getSeasonConcededHome() : stats.getSeasonConcededAway();
        return calculateGoalsAvg(conceded, stats.getMatchesPlayed(), 1.0) * fallbackRatio;
    }

    private static boolean hasHalfGoalStats(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        return homeStats.getScoredAvg2hHome() != null
                && awayStats.getScoredAvg2hAway() != null
                && homeStats.getConcededAvg2hHome() != null
                && awayStats.getConcededAvg2hAway() != null;
    }
}
