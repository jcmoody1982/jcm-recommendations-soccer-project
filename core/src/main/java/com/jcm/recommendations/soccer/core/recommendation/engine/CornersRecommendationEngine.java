package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.core.recommendation.util.RecommendationFactory;
import com.jcm.recommendations.soccer.core.recommendation.util.MatchBriefCopy;
import com.jcm.recommendations.soccer.domain.FixturePotentials;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.*;

@Component
@Slf4j
public class CornersRecommendationEngine implements RecommendationEngine {

// Base weights when form data IS available (total = 1.0)
    private static final double WEIGHT_HOME_CORNERS_WON = 0.12;
    private static final double WEIGHT_AWAY_CORNERS_WON = 0.12;
    private static final double WEIGHT_HOME_CORNERS_CONCEDED = 0.08;
    private static final double WEIGHT_AWAY_CORNERS_CONCEDED = 0.08;
    private static final double WEIGHT_HOME_FORM_CORNERS = 0.12;
    private static final double WEIGHT_AWAY_FORM_CORNERS = 0.12;
    private static final double WEIGHT_API_O95_POTENTIAL = 0.10;
    private static final double WEIGHT_API_O105_POTENTIAL = 0.10;
    private static final double WEIGHT_PLAYING_STYLE = 0.08;
    private static final double WEIGHT_MATCH_CONTEXT = 0.08;

    // Weights when form data NOT available (redistribute to season + API)
    private static final double WEIGHT_HOME_CORNERS_NO_FORM = 0.18;
    private static final double WEIGHT_AWAY_CORNERS_NO_FORM = 0.18;
    private static final double WEIGHT_HOME_CONCEDED_NO_FORM = 0.12;
    private static final double WEIGHT_AWAY_CONCEDED_NO_FORM = 0.12;
    private static final double WEIGHT_API_O95_NO_FORM = 0.12;
    private static final double WEIGHT_API_O105_NO_FORM = 0.12;
    private static final double WEIGHT_PLAYING_STYLE_NO_FORM = 0.08;
    private static final double WEIGHT_MATCH_CONTEXT_NO_FORM = 0.08;

    // Thresholds
    private static final double THRESHOLD_STRONG_OVER = 12.0;
    private static final double THRESHOLD_MODERATE_OVER = 10.0;
    private static final double THRESHOLD_MODERATE_UNDER = 9.5;
    private static final double THRESHOLD_STRONG_UNDER = 8.0;

// API line thresholds for confidence determination
    private static final double API_LINE_STRONG_THRESHOLD = 65.0;
    private static final double API_LINE_MODERATE_THRESHOLD = 55.0;

    // API Potential thresholds for confidence boost
    private static final double API_O105_STRONG_THRESHOLD = 70.0;
    private static final double API_O95_MODERATE_THRESHOLD = 65.0;

    // Recent trend adjustment thresholds
    private static final double TREND_SIGNIFICANT_DIFF = 0.15;  // 15% difference
    private static final double TREND_BOOST_MULTIPLIER = 1.10;
    private static final double TREND_PENALTY_MULTIPLIER = 0.90;

    // Default corners average (league average)
    private static final double DEFAULT_CORNERS_AVG = 5.0;

    @Override
    public RecommendationType getType() {
        return RecommendationType.OVER_CORNERS;
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        log.debug("Analyzing Corners for fixture: fixtureId={}, {} vs {}", 
                context.getFixture().getId(),
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());

double expectedCorners = calculateExpectedCorners(context);
        
        // Get API potentials for confidence determination
        Double apiO85 = context.hasPotentials() ? context.getPotentials().getCornersO85Potential() : null;
        Double apiO95 = context.hasPotentials() ? context.getPotentials().getCornersO95Potential() : null;
        Double apiO105 = context.hasPotentials() ? context.getPotentials().getCornersO105Potential() : null;

        RecommendationType type;
        ConfidenceLevel confidence;
        String market;
        boolean apiBoostApplied = false;

        if (expectedCorners >= THRESHOLD_MODERATE_OVER) {
            type = RecommendationType.OVER_CORNERS;

            // Market line must sit below expected corners; API only boosts confidence.
            if (expectedCorners >= THRESHOLD_STRONG_OVER) {
                market = "Over 10.5 Corners";
                confidence = ConfidenceLevel.STRONG;
            } else {
                market = "Over 9.5 Corners";
                boolean apiBoost = isApiLineStrong(apiO95) || isApiLineStrong(apiO105);
                confidence = apiBoost ? ConfidenceLevel.STRONG : ConfidenceLevel.MODERATE;
                apiBoostApplied = apiBoost;
            }
        } else if (expectedCorners <= THRESHOLD_MODERATE_UNDER) {
            type = RecommendationType.UNDER_CORNERS;

            // Market line must sit above expected corners; API only boosts confidence.
            // Do not pick Under 8.5 when expected is still ~9 (e.g. weak O85 alone).
            if (expectedCorners <= THRESHOLD_STRONG_UNDER) {
                market = "Under 8.5 Corners";
                confidence = ConfidenceLevel.STRONG;
            } else {
                market = "Under 9.5 Corners";
                boolean apiBoost = isApiLineWeak(apiO85) || isApiLineWeak(apiO95);
                confidence = apiBoost ? ConfidenceLevel.STRONG : ConfidenceLevel.MODERATE;
                apiBoostApplied = apiBoost;
            }
        } else {
            return Optional.empty();
        }

        Map<String, Object> factors = buildFactors(context, expectedCorners, apiBoostApplied);

        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(type)
                .confidence(confidence)
                .score(expectedCorners)
                .market(market)
                .odds(null)
                .description(buildDescription(context, confidence, expectedCorners, market, apiBoostApplied))
                .factors(factors)
                .build();

        log.info("Corners recommendation generated: fixtureId={}, expectedCorners={}, type={}, confidence={}, market={}, apiBoost={}", 
                context.getFixture().getId(), String.format("%.1f", expectedCorners), type, confidence, market, apiBoostApplied);

        return Optional.of(recommendation);
    }

    @Override
    public boolean isApplicable(FixtureContext context) {
        return context.hasCompleteData() 
            && context.getHomeTeamStats() != null 
            && context.getAwayTeamStats() != null;
    }

    private double calculateExpectedCorners(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();
        boolean hasForm = context.hasRecentForm();

        // Season corners won (what each team wins at their venue)
        double homeCornersWon = safeDouble(homeStats.getCornersAvgHome(), DEFAULT_CORNERS_AVG);
        double awayCornersWon = safeDouble(awayStats.getCornersAvgAway(), DEFAULT_CORNERS_AVG);

        // Corners conceded (what each team concedes - use opponent's overall as proxy)
        double homeConceded = safeDouble(awayStats.getCornersAvgOverall(), DEFAULT_CORNERS_AVG);
        double awayConceded = safeDouble(homeStats.getCornersAvgOverall(), DEFAULT_CORNERS_AVG);

        // Form corners
        double homeFormCorners = homeCornersWon;
        double awayFormCorners = awayCornersWon;
        if (hasForm) {
            homeFormCorners = safeDouble(context.getHomeTeamForm().getCornersAvgHome(), homeCornersWon);
            awayFormCorners = safeDouble(context.getAwayTeamForm().getCornersAvgAway(), awayCornersWon);
        }

// Base expected corners: average of team corners + opponent conceded
        // For home team: (home corners won + what away team concedes) / 2
        // For away team: (away corners won + what home team concedes) / 2
        double homeExpected = (homeCornersWon + homeConceded) / 2.0;
        double awayExpected = (awayCornersWon + awayConceded) / 2.0;
        
        double baseExpected;
        if (hasForm) {
            // Blend season (60%) and form (40%)
            double homeFormExpected = (homeFormCorners + homeConceded) / 2.0;
            double awayFormExpected = (awayFormCorners + awayConceded) / 2.0;
            
            homeExpected = (homeExpected * 0.6) + (homeFormExpected * 0.4);
            awayExpected = (awayExpected * 0.6) + (awayFormExpected * 0.4);
        }
        
        baseExpected = homeExpected + awayExpected;

        // Apply API potential adjustment if available
        if (context.hasPotentials()) {
            double apiO95 = safeDouble(context.getPotentials().getCornersO95Potential(), 50.0);
            double apiO105 = safeDouble(context.getPotentials().getCornersO105Potential(), 30.0);
            
            // Weight API potentials into the calculation (20% influence)
            double apiAdjustment = calculateApiAdjustment(apiO95, apiO105);
            baseExpected = (baseExpected * 0.80) + (apiAdjustment * 0.20);
        }

        // Apply playing style multiplier
        double playingStyleMultiplier = calculatePlayingStyleMultiplier(homeStats, awayStats);
        baseExpected *= playingStyleMultiplier;

        // Apply match context multiplier (close positions = more competitive)
        double matchContextMultiplier = calculateMatchContextMultiplier(homeStats, awayStats);
        baseExpected *= matchContextMultiplier;

        // Apply recent trend adjustment
        double trendMultiplier = calculateTrendMultiplier(context, homeCornersWon, awayCornersWon);
        baseExpected *= trendMultiplier;

        return baseExpected;
    }

    private double calculateApiAdjustment(double apiO95, double apiO105) {
        // Convert API potentials to expected corners
        // If O95 potential is 70%, expect around 10 corners
        // If O105 potential is 70%, expect around 11 corners
        double o95Contribution = 9.5 + ((apiO95 - 50) / 50.0) * 2.0;  // 50% = 9.5, 100% = 11.5
        double o105Contribution = 10.5 + ((apiO105 - 50) / 50.0) * 2.0;  // 50% = 10.5, 100% = 12.5
        
        return (o95Contribution + o105Contribution) / 2.0;
    }

    private double calculatePlayingStyleMultiplier(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        // Attacking teams tend to win more corners
        double homeGoalsAvg = calculateVenueGoalsAvg(homeStats, true, 1.0);
        double awayGoalsAvg = calculateVenueGoalsAvg(awayStats, false, 1.0);

        double multiplier = 1.0;

        // Home attacking bonus
        if (homeGoalsAvg >= 2.0) {
            multiplier += 0.05;  // High attacking
        } else if (homeGoalsAvg < 1.0) {
            multiplier -= 0.05;  // Low attacking
        }

        // Away attacking bonus
        if (awayGoalsAvg >= 1.5) {
            multiplier += 0.05;  // High attacking for away
        } else if (awayGoalsAvg < 0.8) {
            multiplier -= 0.05;  // Low attacking
        }

        return multiplier;
    }

    private double calculateMatchContextMultiplier(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        double multiplier = 1.0;

        if (homeStats.getPosition() != null && awayStats.getPosition() != null) {
            int positionDiff = Math.abs(homeStats.getPosition() - awayStats.getPosition());
            
            if (positionDiff <= 3) {
                multiplier = 1.10;  // Close rivals - more competitive
            } else if (positionDiff <= 6) {
                multiplier = 1.05;  // Moderately competitive
            }
        }

        return multiplier;
    }

    private double calculateTrendMultiplier(FixtureContext context, double homeSeasonCorners, double awaySeasonCorners) {
        if (!context.hasRecentForm()) {
            return 1.0;
        }

        double homeFormCorners = safeDouble(context.getHomeTeamForm().getCornersAvgHome(), homeSeasonCorners);
        double awayFormCorners = safeDouble(context.getAwayTeamForm().getCornersAvgAway(), awaySeasonCorners);

        double seasonAvg = (homeSeasonCorners + awaySeasonCorners) / 2.0;
        double formAvg = (homeFormCorners + awayFormCorners) / 2.0;

        if (seasonAvg == 0) return 1.0;

        double diff = (formAvg - seasonAvg) / seasonAvg;

        if (diff >= TREND_SIGNIFICANT_DIFF) {
            return TREND_BOOST_MULTIPLIER;  // Form trending up
        } else if (diff <= -TREND_SIGNIFICANT_DIFF) {
            return TREND_PENALTY_MULTIPLIER;  // Form trending down
        }

        return 1.0;
    }

    private Map<String, Object> buildFactors(FixtureContext context, double expectedCorners, boolean apiBoostApplied) {
        Map<String, Object> factors = new HashMap<>();
        
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();
        boolean hasForm = context.hasRecentForm();

        // Expected corners
        factors.put("expectedCorners", expectedCorners);
        
        // Data availability
        factors.put("formDataAvailable", hasForm);
        
        // Season corners won
        factors.put("homeCornersWonAvg", safeDouble(homeStats.getCornersAvgHome(), DEFAULT_CORNERS_AVG));
        factors.put("awayCornersWonAvg", safeDouble(awayStats.getCornersAvgAway(), DEFAULT_CORNERS_AVG));
        
        // Corners conceded (opponent generating against this team)
        factors.put("homeConcededAvg", safeDouble(awayStats.getCornersAvgOverall(), DEFAULT_CORNERS_AVG));
        factors.put("awayConcededAvg", safeDouble(homeStats.getCornersAvgOverall(), DEFAULT_CORNERS_AVG));

// Form corners
        if (hasForm) {
            factors.put("homeFormCornersAvg", safeDouble(context.getHomeTeamForm().getCornersAvgHome(), DEFAULT_CORNERS_AVG));
            factors.put("awayFormCornersAvg", safeDouble(context.getAwayTeamForm().getCornersAvgAway(), DEFAULT_CORNERS_AVG));
            
            // Trend calculation
            double homeSeasonCorners = safeDouble(homeStats.getCornersAvgHome(), DEFAULT_CORNERS_AVG);
            double awaySeasonCorners = safeDouble(awayStats.getCornersAvgAway(), DEFAULT_CORNERS_AVG);
            double trendMultiplier = calculateTrendMultiplier(context, homeSeasonCorners, awaySeasonCorners);
            factors.put("trendMultiplier", trendMultiplier);
            factors.put("trendDirection", trendMultiplier > 1.0 ? "UP" : (trendMultiplier < 1.0 ? "DOWN" : "STABLE"));
        }

        // API Potentials
        if (context.hasPotentials()) {
            factors.put("apiCornersPotential", safeDouble(context.getPotentials().getCornersPotential(), 0.0));
            factors.put("apiCornersO85Potential", safeDouble(context.getPotentials().getCornersO85Potential(), 0.0));
            factors.put("apiCornersO95Potential", safeDouble(context.getPotentials().getCornersO95Potential(), 0.0));
            factors.put("apiCornersO105Potential", safeDouble(context.getPotentials().getCornersO105Potential(), 0.0));
        }
        
        // API boost tracking
        factors.put("apiConfidenceBoostApplied", apiBoostApplied);

        // Playing style factor
        factors.put("playingStyleMultiplier", calculatePlayingStyleMultiplier(homeStats, awayStats));
        
        // Match context
        factors.put("matchContextMultiplier", calculateMatchContextMultiplier(homeStats, awayStats));
        
        // Position info if available
        if (homeStats.getPosition() != null && awayStats.getPosition() != null) {
            factors.put("homePosition", homeStats.getPosition());
            factors.put("awayPosition", awayStats.getPosition());
            factors.put("positionDifference", Math.abs(homeStats.getPosition() - awayStats.getPosition()));
        }

        return factors;
    }

    private String buildDescription(FixtureContext context, ConfidenceLevel confidence, 
            double expectedCorners, String market, boolean apiBoostApplied) {
        StringBuilder colour = new StringBuilder();
        if (apiBoostApplied) {
            colour.append("Corner potential reading is strong on the analysis");
        }

        if (context.hasRecentForm()) {
            double homeSeasonCorners = safeDouble(context.getHomeTeamStats().getCornersAvgHome(), DEFAULT_CORNERS_AVG);
            double awaySeasonCorners = safeDouble(context.getAwayTeamStats().getCornersAvgAway(), DEFAULT_CORNERS_AVG);
            double trendMultiplier = calculateTrendMultiplier(context, homeSeasonCorners, awaySeasonCorners);
            if (trendMultiplier > 1.0) {
                if (!colour.isEmpty()) {
                    colour.append(". ");
                }
                colour.append("Corner mills trending up");
            } else if (trendMultiplier < 1.0) {
                if (!colour.isEmpty()) {
                    colour.append(". ");
                }
                colour.append("Corner mills trending down");
            }
        }

        return MatchBriefCopy.narrate(MatchBriefCopy.Brief.builder()
                .confidence(confidence)
                .selection(market)
                .context(context)
                .expected(expectedCorners, "expected corners")
                .colourNote(colour.isEmpty() ? null : colour.toString())
                .build());
    }

    private boolean isApiLineStrong(Double potential) {
        return potential != null && potential >= API_LINE_STRONG_THRESHOLD;
    }

    private boolean isApiLineModerate(Double potential) {
        return potential != null && potential >= API_LINE_MODERATE_THRESHOLD;
    }

    private boolean isApiLineWeak(Double potential) {
        return potential != null && potential < (100 - API_LINE_STRONG_THRESHOLD);
    }
}
