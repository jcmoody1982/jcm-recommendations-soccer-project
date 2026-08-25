package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.core.recommendation.util.RecommendationFactory;
import com.jcm.recommendations.soccer.core.recommendation.util.VegasTipsterCopy;
import com.jcm.recommendations.soccer.domain.RefereeStats;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.*;

/**
 * UC-019: Draw Recommendations
 * 
 * Identifies fixtures likely to end in a draw based on:
 * - Team draw percentages (season and form)
 * - Evenly matched indicator (PPG + position proximity)
 * - Low-scoring potential (goals scored + defensive strength)
 * - xG similarity factor
 * - Referee draw tendency and cards correlation
 * - Draw specialist detection
 * - Recent draw form count
 * - Match context adjustments
 */
@Component
@Slf4j
public class DrawRecommendationEngine implements RecommendationEngine {

    // Base weights (sum to 1.0)
    private static final double WEIGHT_HOME_DRAW_SEASON = 0.10;
    private static final double WEIGHT_AWAY_DRAW_SEASON = 0.10;
    private static final double WEIGHT_HOME_DRAW_FORM = 0.08;
    private static final double WEIGHT_AWAY_DRAW_FORM = 0.08;
    private static final double WEIGHT_EVENLY_MATCHED = 0.18;
    private static final double WEIGHT_LOW_SCORING = 0.12;
    private static final double WEIGHT_XG_SIMILARITY = 0.10;
    private static final double WEIGHT_REFEREE = 0.09;
    private static final double WEIGHT_IMPLIED_ODDS = 0.08;
    private static final double WEIGHT_DEFENSIVE_STRENGTH = 0.07;

    // Weights when xG not available (redistribute 0.10 xG weight)
    private static final double WEIGHT_HOME_DRAW_SEASON_NO_XG = 0.12;
    private static final double WEIGHT_AWAY_DRAW_SEASON_NO_XG = 0.12;
    private static final double WEIGHT_HOME_DRAW_FORM_NO_XG = 0.09;
    private static final double WEIGHT_AWAY_DRAW_FORM_NO_XG = 0.09;
    private static final double WEIGHT_EVENLY_MATCHED_NO_XG = 0.20;
    private static final double WEIGHT_LOW_SCORING_NO_XG = 0.14;
    private static final double WEIGHT_REFEREE_NO_XG = 0.09;
    private static final double WEIGHT_IMPLIED_ODDS_NO_XG = 0.08;
    private static final double WEIGHT_DEFENSIVE_STRENGTH_NO_XG = 0.07;

    // Thresholds — Draw is capped at MODERATE until score calibration improves hit rate
    private static final double THRESHOLD_MODERATE = 28.0;

    // Draw specialist thresholds
    private static final double DRAW_SPECIALIST_HIGH = 35.0;
    private static final double DRAW_SPECIALIST_ABOVE_AVG = 28.0;
    private static final double DRAW_SPECIALIST_LOW = 20.0;

    // xG similarity thresholds
    private static final double XG_VERY_SIMILAR = 0.2;
    private static final double XG_SIMILAR = 0.4;
    private static final double XG_MODERATE_GAP = 0.6;

    // Referee cards thresholds
    private static final double REFEREE_LOW_CARDS = 3.0;
    private static final double REFEREE_HIGH_CARDS = 4.0;

    @Override
    public RecommendationType getType() {
        return RecommendationType.DRAW;
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        log.debug("Analyzing Draw for fixture: fixtureId={}, {} vs {}", 
                context.getFixture().getId(),
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());

        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();
        boolean hasXgData = hasXgData(homeStats, awayStats);

        double score = calculateDrawScore(context, hasXgData);
        
        // Apply multipliers
        double drawSpecialistMultiplier = calculateDrawSpecialistMultiplier(homeStats, awayStats);
        double recentDrawFormMultiplier = calculateRecentDrawFormMultiplier(context);
        double refereeCarsMultiplier = calculateRefereeCardsMultiplier(context);
        double matchContextMultiplier = calculateMatchContextMultiplier(context);

        score = score * drawSpecialistMultiplier * recentDrawFormMultiplier 
                * refereeCarsMultiplier * matchContextMultiplier;

        ConfidenceLevel confidence = determineConfidence(score, context);

        if (confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        Double odds = context.hasOdds() ? context.getOdds().getOddsFtX() : null;
        Map<String, Object> factors = buildFactors(context, score, hasXgData, 
                drawSpecialistMultiplier, recentDrawFormMultiplier, 
                refereeCarsMultiplier, matchContextMultiplier);

        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(RecommendationType.DRAW)
                .confidence(confidence)
                .score(score)
                .market("Draw")
                .odds(odds)
                .description(buildDescription(context, confidence, score, factors))
                .factors(factors)
                .build();

        log.info("Draw recommendation generated: fixtureId={}, score={}, confidence={}", 
                context.getFixture().getId(), String.format("%.1f", score), confidence);

        return Optional.of(recommendation);
    }

    private boolean hasXgData(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        return homeStats.getXgForAvgOverall() != null && awayStats.getXgForAvgOverall() != null;
    }

    private double calculateDrawScore(FixtureContext context, boolean hasXgData) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double homeDrawSeason = calculateDrawPercentage(homeStats, true);
        double awayDrawSeason = calculateDrawPercentage(awayStats, false);

        double homeDrawForm = homeDrawSeason;
        double awayDrawForm = awayDrawSeason;
        if (context.hasRecentForm()) {
            homeDrawForm = calculateFormDrawPercentage(context.getHomeTeamForm().getDrawsHome());
            awayDrawForm = calculateFormDrawPercentage(context.getAwayTeamForm().getDrawsAway());
        }

        double evenlyMatched = calculateEvenlyMatchedScore(homeStats, awayStats);
        double lowScoring = calculateLowScoringScore(homeStats, awayStats);
        double defensiveStrength = calculateDefensiveStrengthScore(homeStats, awayStats);
        double refereeFactor = calculateRefereeFactor(context);

        double impliedOdds = 25.0;
        if (context.hasOdds() && context.getOdds().getOddsFtX() != null && context.getOdds().getOddsFtX() > 0) {
            impliedOdds = (1.0 / context.getOdds().getOddsFtX()) * 100;
        }

        double score;
        if (hasXgData) {
            double xgSimilarity = calculateXgSimilarityScore(homeStats, awayStats);
            
            score = (homeDrawSeason * WEIGHT_HOME_DRAW_SEASON)
                    + (awayDrawSeason * WEIGHT_AWAY_DRAW_SEASON)
                    + (homeDrawForm * WEIGHT_HOME_DRAW_FORM)
                    + (awayDrawForm * WEIGHT_AWAY_DRAW_FORM)
                    + (evenlyMatched * WEIGHT_EVENLY_MATCHED)
                    + (lowScoring * WEIGHT_LOW_SCORING)
                    + (xgSimilarity * WEIGHT_XG_SIMILARITY)
                    + (refereeFactor * WEIGHT_REFEREE)
                    + (impliedOdds * WEIGHT_IMPLIED_ODDS)
                    + (defensiveStrength * WEIGHT_DEFENSIVE_STRENGTH);
        } else {
            log.debug("No xG data available, using redistributed weights for fixture: {}", 
                    context.getFixture().getId());
            
            score = (homeDrawSeason * WEIGHT_HOME_DRAW_SEASON_NO_XG)
                    + (awayDrawSeason * WEIGHT_AWAY_DRAW_SEASON_NO_XG)
                    + (homeDrawForm * WEIGHT_HOME_DRAW_FORM_NO_XG)
                    + (awayDrawForm * WEIGHT_AWAY_DRAW_FORM_NO_XG)
                    + (evenlyMatched * WEIGHT_EVENLY_MATCHED_NO_XG)
                    + (lowScoring * WEIGHT_LOW_SCORING_NO_XG)
                    + (refereeFactor * WEIGHT_REFEREE_NO_XG)
                    + (impliedOdds * WEIGHT_IMPLIED_ODDS_NO_XG)
                    + (defensiveStrength * WEIGHT_DEFENSIVE_STRENGTH_NO_XG);
        }

        return score;
    }

    private double calculateEvenlyMatchedScore(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        double ppgDiff = Math.abs(safeDouble(homeStats.getPpgOverall()) - safeDouble(awayStats.getPpgOverall()));
        double ppgScore;
        if (ppgDiff < 0.3) {
            ppgScore = 100.0;
        } else if (ppgDiff < 0.5) {
            ppgScore = 80.0;
        } else if (ppgDiff < 0.8) {
            ppgScore = 60.0;
        } else {
            ppgScore = 40.0;
        }

        double positionScore = 50.0;
        if (homeStats.getPosition() != null && awayStats.getPosition() != null) {
            int posDiff = Math.abs(homeStats.getPosition() - awayStats.getPosition());
            if (posDiff <= 3) {
                positionScore = 100.0;
            } else if (posDiff <= 6) {
                positionScore = 75.0;
            } else if (posDiff <= 10) {
                positionScore = 50.0;
            } else {
                positionScore = 25.0;
            }
        }

        return (ppgScore + positionScore) / 2.0;
    }

    private double calculateLowScoringScore(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        double homeGoalsAvg = calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 1.0);
        double awayGoalsAvg = calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 1.0);

        double combinedAvg = (homeGoalsAvg + awayGoalsAvg) / 2.0;

        if (combinedAvg < 1.2) {
            return 100.0;
        } else if (combinedAvg < 1.5) {
            return 75.0;
        } else if (combinedAvg < 1.8) {
            return 50.0;
        } else {
            return 25.0;
        }
    }

    private double calculateDefensiveStrengthScore(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        double homeConcededAvg = calculateGoalsAvg(homeStats.getSeasonConcededHome(), homeStats.getMatchesPlayed(), 1.0);
        double awayConcededAvg = calculateGoalsAvg(awayStats.getSeasonConcededAway(), awayStats.getMatchesPlayed(), 1.0);

        boolean homeTight = homeConcededAvg < 1.0;
        boolean awayTight = awayConcededAvg < 1.0;
        boolean homeLeaky = homeConcededAvg > 1.5;
        boolean awayLeaky = awayConcededAvg > 1.5;

        if (homeTight && awayTight) {
            return 100.0; // Both defensive = tight game, draw likely
        } else if (homeTight || awayTight) {
            return 75.0;
        } else if (homeLeaky && awayLeaky) {
            return 25.0; // Both concede a lot = goals likely, not draw
        } else {
            return 50.0;
        }
    }

    private double calculateXgSimilarityScore(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        double homeXg = safeDouble(homeStats.getXgForAvgOverall());
        double awayXg = safeDouble(awayStats.getXgForAvgOverall());
        double xgDiff = Math.abs(homeXg - awayXg);

        if (xgDiff < XG_VERY_SIMILAR) {
            return 100.0;
        } else if (xgDiff < XG_SIMILAR) {
            return 75.0;
        } else if (xgDiff < XG_MODERATE_GAP) {
            return 50.0;
        } else {
            return 25.0;
        }
    }

    private double calculateDrawSpecialistMultiplier(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        double homeDrawPct = calculateDrawPercentage(homeStats, true);
        double awayDrawPct = calculateDrawPercentage(awayStats, false);

        double homeMultiplier = getDrawSpecialistMultiplier(homeDrawPct);
        double awayMultiplier = getDrawSpecialistMultiplier(awayDrawPct);

        return (homeMultiplier + awayMultiplier) / 2.0;
    }

    private double getDrawSpecialistMultiplier(double drawPct) {
        if (drawPct >= DRAW_SPECIALIST_HIGH) {
            return 1.20;
        } else if (drawPct >= DRAW_SPECIALIST_ABOVE_AVG) {
            return 1.10;
        } else if (drawPct >= DRAW_SPECIALIST_LOW) {
            return 1.0;
        } else {
            return 0.85;
        }
    }

    private double calculateRecentDrawFormMultiplier(FixtureContext context) {
        if (!context.hasRecentForm()) {
            return 1.0;
        }

        int homeDraws = safeInt(context.getHomeTeamForm().getDrawsHome());
        int awayDraws = safeInt(context.getAwayTeamForm().getDrawsAway());
        int combinedDraws = homeDraws + awayDraws;

        // Check for very draw-heavy form (3+ draws per team in last 5)
        if (homeDraws >= 3 || awayDraws >= 3) {
            return 1.25;
        } else if (combinedDraws >= 4) {
            return 1.15;
        } else if (homeDraws >= 2 || awayDraws >= 2) {
            return 1.10;
        } else if (homeDraws == 0 && awayDraws == 0) {
            return 0.85;
        }
        return 1.0;
    }

    private double calculateRefereeCardsMultiplier(FixtureContext context) {
        if (!context.hasRefereeStats()) {
            return 1.0;
        }

        RefereeStats refStats = context.getRefereeStats();
        Double cardsPerMatch = refStats.getCardsPerMatchOverall();
        
        if (cardsPerMatch == null) {
            // Try to calculate from totals
            Integer yellowCards = refStats.getYellowCardsOverall();
            Integer appearances = refStats.getAppearancesOverall();
            if (yellowCards != null && appearances != null && appearances > 0) {
                cardsPerMatch = (double) yellowCards / appearances;
            }
        }

        if (cardsPerMatch == null) {
            return 1.0;
        }

        if (cardsPerMatch < REFEREE_LOW_CARDS) {
            return 1.10; // Controlled games, draw likely
        } else if (cardsPerMatch > REFEREE_HIGH_CARDS) {
            return 0.95; // Chaotic, less predictable
        }
        return 1.0;
    }

    private double calculateRefereeFactor(FixtureContext context) {
        if (!context.hasRefereeStats()) {
            return 50.0;
        }

        RefereeStats refStats = context.getRefereeStats();

        double drawPct = safeDouble(refStats.getDrawsPer());
        double drawScore;
        if (drawPct > 30) {
            drawScore = 100.0;
        } else if (drawPct > 25) {
            drawScore = 75.0;
        } else if (drawPct > 20) {
            drawScore = 50.0;
        } else {
            drawScore = 25.0;
        }

        double reliability = 1.0;
        if (refStats.getAppearancesOverall() != null) {
            if (refStats.getAppearancesOverall() < 5) {
                reliability = 0.5;
            } else if (refStats.getAppearancesOverall() < 10) {
                reliability = 0.8;
            }
        }

        return drawScore * reliability;
    }

    private double calculateMatchContextMultiplier(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double multiplier = 1.0;

        if (homeStats.getPosition() != null && awayStats.getPosition() != null) {
            int homePos = homeStats.getPosition();
            int awayPos = awayStats.getPosition();

            // Both mid-table with nothing to play for
            boolean bothMidTable = homePos >= 8 && homePos <= 14 && awayPos >= 8 && awayPos <= 14;
            if (bothMidTable) {
                multiplier *= 1.15;
            }

            // One team desperate (relegation), other safe - less likely draw
            boolean oneDesperateOtherSafe = 
                    (homePos >= 17 && awayPos <= 10) || (awayPos >= 17 && homePos <= 10);
            if (oneDesperateOtherSafe) {
                multiplier *= 0.85;
            }

            // Both teams need points equally (similar positions)
            int posDiff = Math.abs(homePos - awayPos);
            if (posDiff <= 2) {
                multiplier *= 1.05;
            }
        }

        return multiplier;
    }

    ConfidenceLevel determineConfidence(double score, FixtureContext context) {
        if (score >= THRESHOLD_MODERATE) {
            return ConfidenceLevel.MODERATE;
        }
        return ConfidenceLevel.WEAK;
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private Map<String, Object> buildFactors(FixtureContext context, double score, boolean hasXgData,
            double drawSpecialistMultiplier, double recentDrawFormMultiplier,
            double refereeCardsMultiplier, double matchContextMultiplier) {
        Map<String, Object> factors = new HashMap<>();
        
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        // Core metrics
        factors.put("drawScore", score);
        factors.put("homeDrawPctSeason", calculateDrawPercentage(homeStats, true));
        factors.put("awayDrawPctSeason", calculateDrawPercentage(awayStats, false));

        // Form draw percentages
        if (context.hasRecentForm()) {
            factors.put("homeDrawsLast5", safeInt(context.getHomeTeamForm().getDrawsHome()));
            factors.put("awayDrawsLast5", safeInt(context.getAwayTeamForm().getDrawsAway()));
        }

        // Evenly matched details
        double ppgDiff = Math.abs(safeDouble(homeStats.getPpgOverall()) - safeDouble(awayStats.getPpgOverall()));
        factors.put("ppgDifference", ppgDiff);
        factors.put("evenlyMatchedScore", calculateEvenlyMatchedScore(homeStats, awayStats));

        if (homeStats.getPosition() != null && awayStats.getPosition() != null) {
            factors.put("homePosition", homeStats.getPosition());
            factors.put("awayPosition", awayStats.getPosition());
            factors.put("positionDifference", Math.abs(homeStats.getPosition() - awayStats.getPosition()));
        }

        // Low scoring / defensive strength
        double homeGoalsAvg = calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed(), 1.0);
        double awayGoalsAvg = calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed(), 1.0);
        factors.put("homeGoalsAvg", homeGoalsAvg);
        factors.put("awayGoalsAvg", awayGoalsAvg);
        factors.put("lowScoringScore", calculateLowScoringScore(homeStats, awayStats));

        double homeConcededAvg = calculateGoalsAvg(homeStats.getSeasonConcededHome(), homeStats.getMatchesPlayed(), 1.0);
        double awayConcededAvg = calculateGoalsAvg(awayStats.getSeasonConcededAway(), awayStats.getMatchesPlayed(), 1.0);
        factors.put("homeConcededAvg", homeConcededAvg);
        factors.put("awayConcededAvg", awayConcededAvg);
        factors.put("defensiveStrengthScore", calculateDefensiveStrengthScore(homeStats, awayStats));

        // xG similarity
        factors.put("xgDataAvailable", hasXgData);
        if (hasXgData) {
            double homeXg = safeDouble(homeStats.getXgForAvgOverall());
            double awayXg = safeDouble(awayStats.getXgForAvgOverall());
            factors.put("homeXgAvg", homeXg);
            factors.put("awayXgAvg", awayXg);
            factors.put("xgDifference", Math.abs(homeXg - awayXg));
            factors.put("xgSimilarityScore", calculateXgSimilarityScore(homeStats, awayStats));
        }

        // Multipliers
        factors.put("drawSpecialistMultiplier", drawSpecialistMultiplier);
        factors.put("recentDrawFormMultiplier", recentDrawFormMultiplier);
        factors.put("refereeCardsMultiplier", refereeCardsMultiplier);
        factors.put("matchContextMultiplier", matchContextMultiplier);

        // Referee stats
        if (context.hasRefereeStats()) {
            RefereeStats refStats = context.getRefereeStats();
            factors.put("refereeDrawPct", safeDouble(refStats.getDrawsPer()));
            factors.put("refereeAppearances", refStats.getAppearancesOverall());
            if (refStats.getCardsPerMatchOverall() != null) {
                factors.put("refereeCardsPerMatch", refStats.getCardsPerMatchOverall());
            }
        }

        // Odds
        if (context.hasOdds() && context.getOdds().getOddsFtX() != null && context.getOdds().getOddsFtX() > 0) {
            double impliedProb = (1.0 / context.getOdds().getOddsFtX()) * 100;
            factors.put("drawOdds", context.getOdds().getOddsFtX());
            factors.put("impliedProbability", impliedProb);
            factors.put("valueVsOdds", score - impliedProb);
        }

        // Positive indicators and risk flags
        List<String> positiveIndicators = new ArrayList<>();
        List<String> riskFlags = new ArrayList<>();

        if (ppgDiff < 0.3) {
            positiveIndicators.add("Very evenly matched teams (PPG diff < 0.3)");
        }
        if (calculateDrawPercentage(homeStats, true) >= DRAW_SPECIALIST_HIGH 
                || calculateDrawPercentage(awayStats, false) >= DRAW_SPECIALIST_HIGH) {
            positiveIndicators.add("Draw specialist team(s) involved");
        }
        if (hasXgData && Math.abs(safeDouble(homeStats.getXgForAvgOverall()) - safeDouble(awayStats.getXgForAvgOverall())) < XG_VERY_SIMILAR) {
            positiveIndicators.add("Very similar xG profiles");
        }
        if (homeConcededAvg < 1.0 && awayConcededAvg < 1.0) {
            positiveIndicators.add("Both teams defensively strong");
        }
        if (context.hasRecentForm()) {
            int homeDraws = safeInt(context.getHomeTeamForm().getDrawsHome());
            int awayDraws = safeInt(context.getAwayTeamForm().getDrawsAway());
            if (homeDraws >= 2 || awayDraws >= 2) {
                positiveIndicators.add("Recent draw-heavy form");
            }
        }
        if (context.hasRefereeStats() && safeDouble(context.getRefereeStats().getDrawsPer()) > 30) {
            positiveIndicators.add("Draw-friendly referee");
        }

        if (!hasXgData) {
            riskFlags.add("No xG data available for validation");
        }
        if (homeGoalsAvg > 1.8 && awayGoalsAvg > 1.8) {
            riskFlags.add("Both teams high-scoring - goals more likely than draw");
        }
        if (homeConcededAvg > 1.5 && awayConcededAvg > 1.5) {
            riskFlags.add("Both teams defensively weak - goals likely");
        }
        if (context.hasRecentForm()) {
            int homeDraws = safeInt(context.getHomeTeamForm().getDrawsHome());
            int awayDraws = safeInt(context.getAwayTeamForm().getDrawsAway());
            if (homeDraws == 0 && awayDraws == 0) {
                riskFlags.add("Neither team has drawn recently (decisive form)");
            }
        }
        if (homeStats.getPosition() != null && awayStats.getPosition() != null) {
            if ((homeStats.getPosition() >= 17 && awayStats.getPosition() <= 10) 
                    || (awayStats.getPosition() >= 17 && homeStats.getPosition() <= 10)) {
                riskFlags.add("Mismatch in stakes - one team desperate");
            }
        }

        factors.put("positiveIndicators", positiveIndicators);
        factors.put("riskFlags", riskFlags);

        return factors;
    }

    private String buildDescription(FixtureContext context, ConfidenceLevel confidence, 
            double score, Map<String, Object> factors) {
        StringBuilder colour = new StringBuilder();

        double homeDrawPct = calculateDrawPercentage(context.getHomeTeamStats(), true);
        double awayDrawPct = calculateDrawPercentage(context.getAwayTeamStats(), false);
        if (homeDrawPct >= 30 && awayDrawPct >= 30) {
            colour.append("Draw specialists meeting under the neon");
        }

        @SuppressWarnings("unchecked")
        List<String> positiveIndicators = (List<String>) factors.get("positiveIndicators");
        if (positiveIndicators != null && positiveIndicators.stream().anyMatch(s -> s.contains("xG"))) {
            if (!colour.isEmpty()) {
                colour.append(". ");
            }
            colour.append("Similar xG profiles — stalemate perfume in the air");
        }

        return VegasTipsterCopy.narrate(VegasTipsterCopy.Brief.builder()
                .confidence(confidence)
                .selection("Draw")
                .context(context)
                .probabilityPct(score)
                .colourNote(colour.isEmpty() ? null : colour.toString())
                .build());
    }
}
