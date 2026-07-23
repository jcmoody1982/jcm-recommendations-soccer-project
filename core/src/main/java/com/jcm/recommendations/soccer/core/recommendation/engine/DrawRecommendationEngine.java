package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.domain.RefereeStats;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class DrawRecommendationEngine implements RecommendationEngine {

    private static final double WEIGHT_HOME_DRAW_SEASON = 0.12;
    private static final double WEIGHT_AWAY_DRAW_SEASON = 0.12;
    private static final double WEIGHT_HOME_DRAW_FORM = 0.10;
    private static final double WEIGHT_AWAY_DRAW_FORM = 0.10;
    private static final double WEIGHT_EVENLY_MATCHED = 0.20;
    private static final double WEIGHT_LOW_SCORING = 0.15;
    private static final double WEIGHT_REFEREE = 0.11;
    private static final double WEIGHT_IMPLIED_ODDS = 0.10;

    private static final double THRESHOLD_STRONG = 35.0;
    private static final double THRESHOLD_MODERATE = 28.0;
    private static final double MIN_ODDS_VALUE = 3.20;

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

        double score = calculateDrawScore(context);
        ConfidenceLevel confidence = determineConfidence(score, context);

        if (confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        Map<String, Object> factors = buildFactors(context, score);

        Recommendation recommendation = Recommendation.builder()
                .fixtureId(context.getFixture().getId())
                .homeTeamId(context.getHomeTeam().getId())
                .awayTeamId(context.getAwayTeam().getId())
                .homeTeamName(context.getHomeTeam().getName())
                .awayTeamName(context.getAwayTeam().getName())
                .matchDateUnix(context.getFixture().getDateUnix())
                .leagueId(context.getLeague() != null ? context.getLeague().getCurrentSeasonId() : null)
                .leagueName(context.getLeague() != null ? context.getLeague().getName() : null)
                .leagueImage(context.getLeague() != null ? context.getLeague().getImage() : null)
                .type(RecommendationType.DRAW)
                .confidence(confidence)
                .score(score)
                .market("Draw")
                .description(buildDescription(context, confidence, score))
                .factors(factors)
                .generatedAt(Instant.now())
                .build();

        log.info("Draw recommendation generated: fixtureId={}, score={}, confidence={}", 
                context.getFixture().getId(), String.format("%.1f", score), confidence);

        return Optional.of(recommendation);
    }

    private double calculateDrawScore(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double homeDrawSeason = calculateDrawPercentage(homeStats, true);
        double awayDrawSeason = calculateDrawPercentage(awayStats, false);

        double homeDrawForm = homeDrawSeason;
        double awayDrawForm = awayDrawSeason;
        if (context.hasRecentForm()) {
            homeDrawForm = (safeInt(context.getHomeTeamForm().getDrawsHome()) * 100.0) / 5.0;
            awayDrawForm = (safeInt(context.getAwayTeamForm().getDrawsAway()) * 100.0) / 5.0;
        }

        double evenlyMatched = calculateEvenlyMatchedScore(homeStats, awayStats);
        double lowScoring = calculateLowScoringScore(homeStats, awayStats);
        double refereeFactor = calculateRefereeFactor(context);

        double impliedOdds = 25.0;
        if (context.hasOdds() && context.getOdds().getOddsFtX() != null) {
            impliedOdds = (1.0 / context.getOdds().getOddsFtX()) * 100;
        }

        double score = (homeDrawSeason * WEIGHT_HOME_DRAW_SEASON)
                + (awayDrawSeason * WEIGHT_AWAY_DRAW_SEASON)
                + (homeDrawForm * WEIGHT_HOME_DRAW_FORM)
                + (awayDrawForm * WEIGHT_AWAY_DRAW_FORM)
                + (evenlyMatched * WEIGHT_EVENLY_MATCHED)
                + (lowScoring * WEIGHT_LOW_SCORING)
                + (refereeFactor * WEIGHT_REFEREE)
                + (impliedOdds * WEIGHT_IMPLIED_ODDS);

        score = applyMatchContextFactor(score, context);

        return score;
    }

    private double calculateDrawPercentage(TeamSeasonStats stats, boolean isHome) {
        if (stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 25.0;
        }
        int draws = isHome ? safeInt(stats.getSeasonDrawsHome()) : safeInt(stats.getSeasonDrawsAway());
        return (draws * 100.0) / stats.getMatchesPlayed();
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
        double homeGoalsAvg = calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed());
        double awayGoalsAvg = calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed());

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

    private double applyMatchContextFactor(double score, FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        if (homeStats.getPosition() != null && awayStats.getPosition() != null) {
            boolean bothMidTable = homeStats.getPosition() >= 8 && homeStats.getPosition() <= 14
                    && awayStats.getPosition() >= 8 && awayStats.getPosition() <= 14;
            
            if (bothMidTable) {
                return score * 1.15;
            }
        }

        return score;
    }

    private double calculateGoalsAvg(Integer goals, Integer matches) {
        if (matches == null || matches == 0 || goals == null) {
            return 1.0;
        }
        return goals / (double) matches;
    }

    private ConfidenceLevel determineConfidence(double score, FixtureContext context) {
        boolean hasValueOdds = false;
        if (context.hasOdds() && context.getOdds().getOddsFtX() != null) {
            hasValueOdds = context.getOdds().getOddsFtX() >= MIN_ODDS_VALUE;
        }

        if (score >= THRESHOLD_STRONG && hasValueOdds) {
            return ConfidenceLevel.STRONG;
        } else if (score >= THRESHOLD_MODERATE) {
            return ConfidenceLevel.MODERATE;
        }
        return ConfidenceLevel.WEAK;
    }

    private Map<String, Object> buildFactors(FixtureContext context, double score) {
        Map<String, Object> factors = new HashMap<>();
        
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        factors.put("drawScore", score);
        factors.put("homeDrawPct", calculateDrawPercentage(homeStats, true));
        factors.put("awayDrawPct", calculateDrawPercentage(awayStats, false));
        factors.put("evenlyMatchedScore", calculateEvenlyMatchedScore(homeStats, awayStats));
        factors.put("lowScoringScore", calculateLowScoringScore(homeStats, awayStats));

        if (context.hasRefereeStats()) {
            factors.put("refereeDrawPct", safeDouble(context.getRefereeStats().getDrawsPer()));
        }

        if (context.hasOdds() && context.getOdds().getOddsFtX() != null) {
            factors.put("drawOdds", context.getOdds().getOddsFtX());
            factors.put("impliedProbability", (1.0 / context.getOdds().getOddsFtX()) * 100);
        }

        return factors;
    }

    private String buildDescription(FixtureContext context, ConfidenceLevel confidence, double score) {
        boolean drawSpecialists = false;
        double homeDrawPct = calculateDrawPercentage(context.getHomeTeamStats(), true);
        double awayDrawPct = calculateDrawPercentage(context.getAwayTeamStats(), false);
        if (homeDrawPct > 30 && awayDrawPct > 30) {
            drawSpecialists = true;
        }

        String suffix = drawSpecialists ? " - Draw specialists meeting" : "";
        
        return String.format("%s confidence Draw recommendation (%.1f%% score)%s - %s vs %s",
                confidence.getDisplayName(),
                score,
                suffix,
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private double safeDouble(Double value) {
        return value != null ? value : 0.0;
    }
}
