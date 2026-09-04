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

import com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.*;

/**
 * UC-024: Double Chance Recommendations
 *
 * Identifies fixtures where backing two outcomes (Home/Draw or Draw/Away)
 * offers strong probability with reduced risk.
 *
 * Uses: win rates, PPG, league position, unbeaten rates (fortress/road warrior),
 * recent form, xG data, and value vs odds calculation.
 *
 * <p>Calibration notes. Three things made this board overstate itself:
 * <ul>
 *   <li>The old STRONG gate required the model to beat the market by five points or more, so it
 *       promoted exactly the fixtures where the model disagreed most with the price. Because the
 *       model ran hot, that was adverse selection and STRONG landed below MODERATE.</li>
 *   <li>Fortress and road-warrior boosts were applied <em>after</em> the three outcome
 *       probabilities were normalised, which broke the distribution and inflated borderline picks
 *       into the eighties — the worst-performing band on the board.</li>
 *   <li>Venue win and draw rates were taken raw, so a team with three home games could show a 67%
 *       home win rate.</li>
 * </ul>
 * The score is now an even blend of the model and the de-vigged market price, the boosts are
 * applied before normalisation, and venue rates are shrunk toward league baselines.
 */
@Component
@Slf4j
public class DoubleChanceRecommendationEngine implements RecommendationEngine {

    // Probability thresholds, rebased for the market-blended score
    private static final double THRESHOLD_STRONG_1X = 78.0;
    private static final double THRESHOLD_MODERATE_1X = 66.0;
    private static final double THRESHOLD_STRONG_X2 = 74.0;
    private static final double THRESHOLD_MODERATE_X2 = 62.0;

    /** Weight on the engine's own estimate; the rest goes to the de-vigged market price. */
    private static final double MODEL_WEIGHT = 0.50;

    /** Matches of prior evidence blended into every venue rate. */
    private static final int SHRINKAGE_PSEUDO_MATCHES = 4;
    private static final double PRIOR_HOME_WIN_PCT = 45.0;
    private static final double PRIOR_AWAY_WIN_PCT = 28.0;
    private static final double PRIOR_DRAW_PCT = 25.0;

    /** Applied to a side's win probability before normalisation, not to the combined total. */
    private static final double CHARACTERISTIC_BOOST = 1.06;

    // Value threshold
    private static final double MIN_VALUE_PERCENT = 5.0;

    /**
     * Shortest Double Chance price the board will carry. Ranking by probability pushes heavy
     * favourites into 1X at ~1.05, which is not a stakeable return.
     */
    private static final double MIN_PRICE = 1.20;

    // Fortress/Road Warrior thresholds
    private static final double FORTRESS_UNBEATEN_THRESHOLD = 60.0;
    private static final double ROAD_WARRIOR_UNBEATEN_THRESHOLD = 50.0;
    private static final double POOR_TRAVELER_WIN_RATE = 25.0;
    private static final double WEAK_HOME_WIN_RATE = 40.0;
    private static final double STRONG_AWAY_WIN_RATE = 35.0;

    // Position thresholds
    private static final int POSITION_QUALITY_GAP = 5;
    private static final int POSITION_MAJOR_GAP = 10;

    // Weight factors
    private static final double WEIGHT_WIN_PCT = 0.35;
    private static final double WEIGHT_PPG = 0.20;
    private static final double WEIGHT_POSITION = 0.15;
    private static final double WEIGHT_FORM = 0.15;
    private static final double WEIGHT_XG = 0.15;

    // Weights when xG not available
    private static final double WEIGHT_WIN_PCT_NO_XG = 0.40;
    private static final double WEIGHT_PPG_NO_XG = 0.25;
    private static final double WEIGHT_POSITION_NO_XG = 0.18;
    private static final double WEIGHT_FORM_NO_XG = 0.17;

    @Override
    public RecommendationType getType() {
        return RecommendationType.DOUBLE_CHANCE;
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        log.debug("Analyzing Double Chance for fixture: fixtureId={}, {} vs {}",
                context.getFixture().getId(),
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());

        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();
        boolean hasXgData = hasXgData(homeStats, awayStats);

        // Calculate individual probabilities
        double homeWinProb = calculateWinProbability(context, true, hasXgData);
        double awayWinProb = calculateWinProbability(context, false, hasXgData);
        double drawProb = calculateDrawProbability(context, hasXgData);

        // Calculate fortress/road warrior factors
        boolean isFortress = checkFortress(homeStats);
        boolean isPoorTraveler = checkPoorTraveler(awayStats);
        boolean isRoadWarrior = checkRoadWarrior(awayStats);
        boolean isWeakHome = checkWeakHome(homeStats);

        // Boost the side's own win chance before normalising, so the three outcomes stay a
        // coherent distribution and the combined total cannot be inflated past what they imply.
        if (isFortress || isPoorTraveler) {
            homeWinProb *= CHARACTERISTIC_BOOST;
        }
        if (isRoadWarrior || isWeakHome) {
            awayWinProb *= CHARACTERISTIC_BOOST;
        }

        // Normalize to sum to 100
        double total = homeWinProb + drawProb + awayWinProb;
        if (total > 0) {
            homeWinProb = (homeWinProb / total) * 100;
            drawProb = (drawProb / total) * 100;
            awayWinProb = (awayWinProb / total) * 100;
        }

        // Calculate double chance probabilities
        double modelHomeDraw = homeWinProb + drawProb;
        double modelDrawAway = drawProb + awayWinProb;

        // Blend against the de-vigged market. The price is the best-calibrated estimate available,
        // and blending also removes the incentive to reward disagreement with it.
        Double fair1X = fairImplied1X(context);
        Double fairX2 = fairImpliedX2(context);
        double homeDrawProb = blendWithMarket(modelHomeDraw, fair1X);
        double drawAwayProb = blendWithMarket(modelDrawAway, fairX2);

        // Calculate value vs odds
        double value1X = 0.0;
        double valueX2 = 0.0;
        Double implied1X = null;
        Double impliedX2 = null;

        if (context.hasOdds()) {
            implied1X = calculateImplied1X(context);
            impliedX2 = calculateImpliedX2(context);

            if (fair1X != null) {
                value1X = homeDrawProb - fair1X;
            }
            if (fairX2 != null) {
                valueX2 = drawAwayProb - fairX2;
            }
        }

        // Determine best market
        String market;
        double bestProb;
        double bestValue;
        ConfidenceLevel confidence;

        boolean prefer1X = homeDrawProb >= drawAwayProb;

        // STRONG requires a market-blended estimate. Without a price the score is the unchecked
        // model output, which is precisely the situation that used to publish false certainty.
        boolean marketInformed1X = fair1X != null;
        boolean marketInformedX2 = fairX2 != null;

        boolean meets1XStrong = homeDrawProb >= THRESHOLD_STRONG_1X && marketInformed1X;
        boolean meets1XModerate = homeDrawProb >= THRESHOLD_MODERATE_1X;
        boolean meetsX2Strong = drawAwayProb >= THRESHOLD_STRONG_X2 && marketInformedX2;
        boolean meetsX2Moderate = drawAwayProb >= THRESHOLD_MODERATE_X2;

        if (prefer1X && meets1XModerate) {
            market = "Home/Draw (1X)";
            bestProb = homeDrawProb;
            bestValue = value1X;
            confidence = meets1XStrong ? ConfidenceLevel.STRONG : ConfidenceLevel.MODERATE;
        } else if (meetsX2Moderate) {
            market = "Draw/Away (X2)";
            bestProb = drawAwayProb;
            bestValue = valueX2;
            confidence = meetsX2Strong ? ConfidenceLevel.STRONG : ConfidenceLevel.MODERATE;
        } else if (meets1XModerate) {
            market = "Home/Draw (1X)";
            bestProb = homeDrawProb;
            bestValue = value1X;
            confidence = meets1XStrong ? ConfidenceLevel.STRONG : ConfidenceLevel.MODERATE;
        } else {
            return Optional.empty();
        }

        // Synthesize double-chance price from 1+X or X+2 match-result odds.
        Double odds = null;
        if (context.hasOdds()) {
            if (market.contains("1X")) {
                odds = calculate1XOdds(context);
            } else {
                odds = calculateX2Odds(context);
            }
        }
        if (odds == null || odds < MIN_PRICE) {
            log.debug("Double Chance rejected on price: fixtureId={}, market={}, odds={}",
                    context.getFixture().getId(), market, odds);
            return Optional.empty();
        }

        Map<String, Object> factors = buildFactors(context, homeWinProb, drawProb, awayWinProb,
                homeDrawProb, drawAwayProb, value1X, valueX2, implied1X, impliedX2,
                isFortress, isPoorTraveler, isRoadWarrior, isWeakHome, hasXgData);

        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(RecommendationType.DOUBLE_CHANCE)
                .confidence(confidence)
                .score(bestProb)
                .market(market)
                .odds(odds)
                .description(buildDescription(context, market, bestProb, bestValue, confidence, factors))
                .factors(factors)
                .build();

        log.info("Double Chance recommendation: fixtureId={}, market={}, probability={}, value={}, confidence={}",
                context.getFixture().getId(), market, String.format("%.1f", bestProb), 
                String.format("%.1f", bestValue), confidence);

        return Optional.of(recommendation);
    }

private boolean hasXgData(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        return homeStats.getXgForAvgOverall() != null && awayStats.getXgForAvgOverall() != null;
    }

    private double calculateWinProbability(FixtureContext context, boolean isHome, boolean hasXgData) {
        TeamSeasonStats stats = isHome ? context.getHomeTeamStats() : context.getAwayTeamStats();
        TeamSeasonStats opponentStats = isHome ? context.getAwayTeamStats() : context.getHomeTeamStats();
        
        if (calculateMatchesAtVenue(stats, isHome) == 0) {
            return 33.3;
        }

        // Win percentage component (venue-specific denominator, shrunk toward the league baseline)
        double winPct = shrunkWinPercentage(stats, isHome);

        // PPG component (normalized to 0-100)
        double ppg = isHome ? safeDouble(stats.getPpgHome()) : safeDouble(stats.getPpgAway());
        double ppgScore = Math.min(100.0, (ppg / 3.0) * 100);

        // Position component
        double positionScore = calculatePositionScore(stats, opponentStats, isHome);

        // Form component
        double formScore = calculateFormScore(context, isHome);

        double probability;
        if (hasXgData) {
            // xG component
            double xgScore = calculateXgScore(stats, opponentStats, isHome);
            
            probability = (winPct * WEIGHT_WIN_PCT)
                    + (ppgScore * WEIGHT_PPG)
                    + (positionScore * WEIGHT_POSITION)
                    + (formScore * WEIGHT_FORM)
                    + (xgScore * WEIGHT_XG);
        } else {
            probability = (winPct * WEIGHT_WIN_PCT_NO_XG)
                    + (ppgScore * WEIGHT_PPG_NO_XG)
                    + (positionScore * WEIGHT_POSITION_NO_XG)
                    + (formScore * WEIGHT_FORM_NO_XG);
        }

        return probability;
    }

    private double calculatePositionScore(TeamSeasonStats stats, TeamSeasonStats opponentStats, boolean isHome) {
        if (stats.getPosition() == null || opponentStats.getPosition() == null) {
            return 50.0;
        }

        int pos = stats.getPosition();
        int oppPos = opponentStats.getPosition();
        int gap = oppPos - pos; // Positive = we are higher ranked

        if (isHome) {
            // Home team gets bonus for being higher ranked
            if (gap >= POSITION_MAJOR_GAP) {
                return 85.0;
            } else if (gap >= POSITION_QUALITY_GAP) {
                return 70.0;
            } else if (gap > 0) {
                return 60.0;
            } else if (gap > -POSITION_QUALITY_GAP) {
                return 45.0;
            } else {
                return 30.0;
            }
        } else {
            // Away team needs bigger gap for same boost
            if (gap >= POSITION_MAJOR_GAP) {
                return 80.0;
            } else if (gap >= POSITION_QUALITY_GAP) {
                return 65.0;
            } else if (gap > 0) {
                return 55.0;
            } else {
                return 35.0;
            }
        }
    }

    private double calculateFormScore(FixtureContext context, boolean isHome) {
        TeamRecentForm form = isHome ? context.getHomeTeamForm() : context.getAwayTeamForm();
        
        if (form == null) {
            return 50.0;
        }

        int wins = isHome ? safeInt(form.getWinsHome()) : safeInt(form.getWinsAway());
        int losses = isHome ? safeInt(form.getLossesHome()) : safeInt(form.getLossesAway());
        
        // Last 5: wins - losses gives form indicator
        int formDiff = wins - losses;
        
        if (formDiff >= 3) {
            return 90.0; // 4-0 or 5-0, 4-1
        } else if (formDiff >= 2) {
            return 75.0; // 3-1, 4-2
        } else if (formDiff >= 1) {
            return 60.0;
        } else if (formDiff == 0) {
            return 50.0;
        } else if (formDiff >= -2) {
            return 35.0;
        } else {
            return 20.0;
        }
    }

    private double calculateXgScore(TeamSeasonStats stats, TeamSeasonStats opponentStats, boolean isHome) {
        double xgFor = isHome ? safeDouble(stats.getXgForAvgHome()) : safeDouble(stats.getXgForAvgAway());
        double xgAgainst = isHome ? safeDouble(opponentStats.getXgAgainstAvgAway()) : safeDouble(opponentStats.getXgAgainstAvgHome());

        // Expected goals we'd score vs what opponent concedes
        double expectedScoring = (xgFor + xgAgainst) / 2.0;
        
        // Normalize to 0-100
        return Math.min(100.0, (expectedScoring / 2.5) * 100);
    }

    private double calculateDrawProbability(FixtureContext context, boolean hasXgData) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double homeDrawPct = shrunkDrawPercentage(homeStats, true);
        double awayDrawPct = shrunkDrawPercentage(awayStats, false);
        double baseDrawProb = (homeDrawPct + awayDrawPct) / 2;

        // Similarity bonus based on PPG
        double ppgDiff = Math.abs(safeDouble(homeStats.getPpgOverall()) - safeDouble(awayStats.getPpgOverall()));
        double similarityBonus = ppgDiff < 0.3 ? 10.0 : ppgDiff < 0.5 ? 5.0 : 0.0;

// xG similarity bonus
        if (hasXgData) {
            double xgDiff = Math.abs(safeDouble(homeStats.getXgForAvgOverall()) - safeDouble(awayStats.getXgForAvgOverall()));
            if (xgDiff < 0.2) {
                similarityBonus += 8.0;
            } else if (xgDiff < 0.4) {
                similarityBonus += 4.0;
            }
        }

        // Position similarity
        if (homeStats.getPosition() != null && awayStats.getPosition() != null) {
            int posDiff = Math.abs(homeStats.getPosition() - awayStats.getPosition());
            if (posDiff <= 3) {
                similarityBonus += 5.0;
            }
        }

        return baseDrawProb + similarityBonus;
    }

    private double calculateDrawPercentage(TeamSeasonStats stats, boolean isHome) {
        int matchesAtVenue = calculateMatchesAtVenue(stats, isHome);
        if (matchesAtVenue == 0) {
            return 25.0;
        }
        int draws = isHome ? safeInt(stats.getSeasonDrawsHome()) : safeInt(stats.getSeasonDrawsAway());
        return (draws * 100.0) / matchesAtVenue;
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

    private boolean checkFortress(TeamSeasonStats stats) {
        double homeLossPct = calculateLossPercentage(stats, true);
        return homeLossPct <= (100 - FORTRESS_UNBEATEN_THRESHOLD); // < 40% loss = > 60% unbeaten
    }

    private boolean checkPoorTraveler(TeamSeasonStats stats) {
        double awayWinPct = calculateWinPercentage(stats, false);
        return awayWinPct < POOR_TRAVELER_WIN_RATE;
    }

    private boolean checkRoadWarrior(TeamSeasonStats stats) {
        double awayWinPct = calculateWinPercentage(stats, false);
        double awayLossPct = calculateLossPercentage(stats, false);
        return awayWinPct >= STRONG_AWAY_WIN_RATE && awayLossPct < (100 - ROAD_WARRIOR_UNBEATEN_THRESHOLD);
    }

    private boolean checkWeakHome(TeamSeasonStats stats) {
        double homeWinPct = calculateWinPercentage(stats, true);
        return homeWinPct < WEAK_HOME_WIN_RATE;
    }

    /**
     * Blend the model estimate with the market. Returns the model unchanged when no price exists,
     * which is why an unpriced fixture can never reach STRONG.
     */
    static double blendWithMarket(double modelProbability, Double fairMarketProbability) {
        if (fairMarketProbability == null) {
            return modelProbability;
        }
        return (MODEL_WEIGHT * modelProbability) + ((1.0 - MODEL_WEIGHT) * fairMarketProbability);
    }

    /**
     * De-vigged 1X probability. The raw implied prices sum to more than 100% because they carry
     * the bookmaker's margin, so comparing a model probability against them understates value.
     */
    private Double fairImplied1X(FixtureContext context) {
        double[] fair = fairOutcomeProbabilities(context);
        return fair == null ? null : fair[0] + fair[1];
    }

    /** De-vigged X2 probability. See {@link #fairImplied1X}. */
    private Double fairImpliedX2(FixtureContext context) {
        double[] fair = fairOutcomeProbabilities(context);
        return fair == null ? null : fair[1] + fair[2];
    }

    /** Home, draw and away probabilities with the bookmaker's margin removed. */
    private double[] fairOutcomeProbabilities(FixtureContext context) {
        if (!context.hasOdds()) {
            return null;
        }
        return RecommendationUtils.fairOutcomeProbabilities(
                context.getOdds().getOddsFt1(),
                context.getOdds().getOddsFtX(),
                context.getOdds().getOddsFt2());
    }

    /**
     * Empirical Bayes shrinkage toward a league baseline. Venue records are thin early in a
     * season, and an unshrunk three-game sample was reading as a real signal.
     */
    static double shrinkRate(double observedPct, int matchesAtVenue, double priorPct) {
        if (matchesAtVenue <= 0) {
            return priorPct;
        }
        double weight = matchesAtVenue / (double) (matchesAtVenue + SHRINKAGE_PSEUDO_MATCHES);
        return (weight * observedPct) + ((1.0 - weight) * priorPct);
    }

    private double shrunkWinPercentage(TeamSeasonStats stats, boolean isHome) {
        return shrinkRate(
                calculateWinPercentage(stats, isHome),
                calculateMatchesAtVenue(stats, isHome),
                isHome ? PRIOR_HOME_WIN_PCT : PRIOR_AWAY_WIN_PCT);
    }

    private double shrunkDrawPercentage(TeamSeasonStats stats, boolean isHome) {
        return shrinkRate(
                calculateDrawPercentage(stats, isHome),
                calculateMatchesAtVenue(stats, isHome),
                PRIOR_DRAW_PCT);
    }

    private Double calculateImplied1X(FixtureContext context) {
        if (!context.hasOdds()) return null;
        
        Double odds1 = context.getOdds().getOddsFt1();
        Double oddsX = context.getOdds().getOddsFtX();
        
        if (odds1 == null || oddsX == null || odds1 <= 0 || oddsX <= 0) return null;
        
        double implied1 = (1.0 / odds1) * 100;
        double impliedX = (1.0 / oddsX) * 100;
        
        return implied1 + impliedX;
    }

    private Double calculateImpliedX2(FixtureContext context) {
        if (!context.hasOdds()) return null;
        
        Double oddsX = context.getOdds().getOddsFtX();
        Double odds2 = context.getOdds().getOddsFt2();
        
        if (oddsX == null || odds2 == null || oddsX <= 0 || odds2 <= 0) return null;
        
        double impliedX = (1.0 / oddsX) * 100;
        double implied2 = (1.0 / odds2) * 100;
        
        return impliedX + implied2;
    }

    private Double calculate1XOdds(FixtureContext context) {
        Double implied = calculateImplied1X(context);
        if (implied == null || implied <= 0) return null;
        return 100.0 / implied;
    }

    private Double calculateX2Odds(FixtureContext context) {
        Double implied = calculateImpliedX2(context);
        if (implied == null || implied <= 0) return null;
        return 100.0 / implied;
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private Map<String, Object> buildFactors(FixtureContext context, 
            double homeWin, double draw, double awayWin,
            double homeDrawProb, double drawAwayProb,
            double value1X, double valueX2,
            Double implied1X, Double impliedX2,
            boolean isFortress, boolean isPoorTraveler, boolean isRoadWarrior, boolean isWeakHome,
            boolean hasXgData) {
        
        Map<String, Object> factors = new HashMap<>();
        
        // Core probabilities
        factors.put("homeWinProbability", homeWin);
        factors.put("drawProbability", draw);
        factors.put("awayWinProbability", awayWin);
        factors.put("homeDrawCombined", homeDrawProb);
        factors.put("drawAwayCombined", drawAwayProb);

        // Value vs odds. `implied` carries the bookmaker margin and drives the published price;
        // `fairImplied` has it removed and is what the score is blended against.
        Double fair1X = fairImplied1X(context);
        Double fairX2 = fairImpliedX2(context);
        if (implied1X != null) {
            factors.put("implied1X", implied1X);
            factors.put("value1X", value1X);
            factors.put("combined1XOdds", 100.0 / implied1X);
        }
        if (impliedX2 != null) {
            factors.put("impliedX2", impliedX2);
            factors.put("valueX2", valueX2);
            factors.put("combinedX2Odds", 100.0 / impliedX2);
        }
        if (fair1X != null) {
            factors.put("fairImplied1X", fair1X);
        }
        if (fairX2 != null) {
            factors.put("fairImpliedX2", fairX2);
        }
        factors.put("marketBlended", fair1X != null || fairX2 != null);

        // Team characteristics
        factors.put("homeFortress", isFortress);
        factors.put("awayPoorTraveler", isPoorTraveler);
        factors.put("awayRoadWarrior", isRoadWarrior);
        factors.put("homeWeakAtHome", isWeakHome);

        // Position data
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();
        
        if (homeStats.getPosition() != null && awayStats.getPosition() != null) {
            factors.put("homePosition", homeStats.getPosition());
            factors.put("awayPosition", awayStats.getPosition());
            factors.put("positionGap", awayStats.getPosition() - homeStats.getPosition());
        }

        // Win/loss rates
        factors.put("homeWinRateHome", calculateWinPercentage(homeStats, true));
        factors.put("homeLossRateHome", calculateLossPercentage(homeStats, true));
        factors.put("awayWinRateAway", calculateWinPercentage(awayStats, false));
        factors.put("awayLossRateAway", calculateLossPercentage(awayStats, false));

        // Form data
        if (context.hasRecentForm()) {
            TeamRecentForm homeForm = context.getHomeTeamForm();
            TeamRecentForm awayForm = context.getAwayTeamForm();
            factors.put("homeLast5Wins", safeInt(homeForm.getWinsHome()));
            factors.put("homeLast5Losses", safeInt(homeForm.getLossesHome()));
            factors.put("awayLast5Wins", safeInt(awayForm.getWinsAway()));
            factors.put("awayLast5Losses", safeInt(awayForm.getLossesAway()));
        }

        // xG data
        factors.put("xgDataAvailable", hasXgData);
        if (hasXgData) {
            factors.put("homeXgFor", safeDouble(homeStats.getXgForAvgHome()));
            factors.put("awayXgFor", safeDouble(awayStats.getXgForAvgAway()));
            factors.put("homeXgAgainst", safeDouble(homeStats.getXgAgainstAvgHome()));
            factors.put("awayXgAgainst", safeDouble(awayStats.getXgAgainstAvgAway()));
        }

        // Positive indicators and risk flags
        List<String> positiveIndicators = new ArrayList<>();
        List<String> riskFlags = new ArrayList<>();

        if (isFortress) {
            positiveIndicators.add("Home team is a fortress (rarely loses at home)");
        }
        if (isPoorTraveler) {
            positiveIndicators.add("Away team struggles on the road");
        }
        if (isRoadWarrior) {
            positiveIndicators.add("Away team is a road warrior (strong away record)");
        }
        if (isWeakHome) {
            positiveIndicators.add("Home team weak at home");
        }
        if (value1X >= MIN_VALUE_PERCENT) {
            positiveIndicators.add(String.format("1X offers %.1f%% value vs odds", value1X));
        }
        if (valueX2 >= MIN_VALUE_PERCENT) {
            positiveIndicators.add(String.format("X2 offers %.1f%% value vs odds", valueX2));
        }
        if (homeStats.getPosition() != null && awayStats.getPosition() != null) {
            int gap = awayStats.getPosition() - homeStats.getPosition();
            if (gap >= POSITION_MAJOR_GAP) {
                positiveIndicators.add("Home team significantly higher ranked");
            } else if (gap <= -POSITION_MAJOR_GAP) {
                positiveIndicators.add("Away team significantly higher ranked");
            }
        }

        if (!hasXgData) {
            riskFlags.add("No xG data available for validation");
        }
        if (value1X < 0 && homeDrawProb >= THRESHOLD_MODERATE_1X) {
            riskFlags.add("1X probability high but negative value vs odds");
        }
        if (valueX2 < 0 && drawAwayProb >= THRESHOLD_MODERATE_X2) {
            riskFlags.add("X2 probability high but negative value vs odds");
        }
        if (isFortress && isRoadWarrior) {
            riskFlags.add("Both teams have strong home/away characteristics - unpredictable");
        }

        factors.put("positiveIndicators", positiveIndicators);
        factors.put("riskFlags", riskFlags);

        return factors;
    }

    private String buildDescription(FixtureContext context, String market, double probability,
            double value, ConfidenceLevel confidence, Map<String, Object> factors) {
        String colourNote = null;
        if (Boolean.TRUE.equals(factors.get("homeFortress"))) {
            colourNote = "Home form has been fortress-like of late";
        } else if (Boolean.TRUE.equals(factors.get("awayPoorTraveler"))) {
            colourNote = "The away side have travelled poorly this season";
        } else if (Boolean.TRUE.equals(factors.get("awayRoadWarrior"))) {
            colourNote = "The away team have been strong on the road";
        } else if (Boolean.TRUE.equals(factors.get("homeWeakAtHome"))) {
            colourNote = "Home form has been fragile in front of their own crowd";
        }

        return MatchBriefCopy.narrate(MatchBriefCopy.Brief.builder()
                .confidence(confidence)
                .selection(market)
                .context(context)
                .probabilityPct(probability)
                .valuePct(Math.abs(value) > 0.1 ? value : null)
                .colourNote(colourNote)
                .build());
    }
}
