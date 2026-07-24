package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class HalfGoalsRecommendationEngine implements RecommendationEngine {

    private static final double WEIGHT_API_O05HT = 0.20;
    private static final double WEIGHT_API_O15HT = 0.15;
    private static final double WEIGHT_TEAM_GOALS = 0.50;
    private static final double WEIGHT_BTTS_HT = 0.15;

    private static final double FIRST_HALF_SHARE = 0.45;
    private static final double SECOND_HALF_SHARE = 0.55;

    private static final double THRESHOLD_STRONG_O05 = 80.0;
    private static final double THRESHOLD_MODERATE_O05 = 65.0;
    private static final double THRESHOLD_STRONG_O15 = 70.0;
    private static final double THRESHOLD_MODERATE_O15 = 55.0;

    @Override
    public RecommendationType getType() {
        return RecommendationType.FIRST_HALF_GOALS;
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        log.debug("Analyzing Half Goals for fixture: fixtureId={}, {} vs {}", 
                context.getFixture().getId(),
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());

        double firstHalfScore = calculateFirstHalfScore(context);
        double secondHalfScore = calculateSecondHalfScore(context);

        Optional<Recommendation> firstHalfRec = createFirstHalfRecommendation(context, firstHalfScore);
        Optional<Recommendation> secondHalfRec = createSecondHalfRecommendation(context, secondHalfScore);

        if (firstHalfRec.isPresent() && secondHalfRec.isPresent()) {
            return firstHalfScore >= secondHalfScore ? firstHalfRec : secondHalfRec;
        }

        return firstHalfRec.isPresent() ? firstHalfRec : secondHalfRec;
    }

    private double calculateFirstHalfScore(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double homeGoalsAvg = calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed());
        double awayGoalsAvg = calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed());

        double estimated1HGoals = (homeGoalsAvg + awayGoalsAvg) * FIRST_HALF_SHARE;
        double goalsScore = normalizeGoalsToScore(estimated1HGoals);

        double apiO05Ht = 50.0;
        double apiO15Ht = 30.0;
        if (context.hasPotentials()) {
            if (context.getPotentials().getO05HtPotential() != null) {
                apiO05Ht = context.getPotentials().getO05HtPotential();
            }
            if (context.getPotentials().getO15HtPotential() != null) {
                apiO15Ht = context.getPotentials().getO15HtPotential();
            }
        }

        double bttsHt = calculateBttsHtScore(homeStats, awayStats);

        return (apiO05Ht * WEIGHT_API_O05HT)
                + (apiO15Ht * WEIGHT_API_O15HT)
                + (goalsScore * WEIGHT_TEAM_GOALS)
                + (bttsHt * WEIGHT_BTTS_HT);
    }

    private double calculateSecondHalfScore(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double homeGoalsAvg = calculateGoalsAvg(homeStats.getSeasonGoalsHome(), homeStats.getMatchesPlayed());
        double awayGoalsAvg = calculateGoalsAvg(awayStats.getSeasonGoalsAway(), awayStats.getMatchesPlayed());

        double estimated2HGoals = (homeGoalsAvg + awayGoalsAvg) * SECOND_HALF_SHARE;
        double goalsScore = normalizeGoalsToScore(estimated2HGoals);

        double intensityFactor = calculateLateGameIntensity(context);

        return goalsScore * intensityFactor;
    }

    private double calculateLateGameIntensity(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double factor = 1.0;

        if (homeStats.getPosition() != null && awayStats.getPosition() != null) {
            int diff = Math.abs(homeStats.getPosition() - awayStats.getPosition());
            if (diff <= 3) {
                factor = 1.15;
            }
        }

        return factor;
    }

    private Optional<Recommendation> createFirstHalfRecommendation(FixtureContext context, double score) {
        String market;
        ConfidenceLevel confidence;

        if (score >= THRESHOLD_STRONG_O15) {
            market = "Over 1.5 First Half Goals";
            confidence = ConfidenceLevel.STRONG;
        } else if (score >= THRESHOLD_MODERATE_O15) {
            market = "Over 1.5 First Half Goals";
            confidence = ConfidenceLevel.MODERATE;
        } else if (score >= THRESHOLD_STRONG_O05) {
            market = "Over 0.5 First Half Goals";
            confidence = ConfidenceLevel.STRONG;
        } else if (score >= THRESHOLD_MODERATE_O05) {
            market = "Over 0.5 First Half Goals";
            confidence = ConfidenceLevel.MODERATE;
        } else {
            return Optional.empty();
        }

        Map<String, Object> factors = new HashMap<>();
        factors.put("halfGoalsScore", score);
        factors.put("half", "First");
        if (context.hasPotentials()) {
            factors.put("apiO05HtPotential", context.getPotentials().getO05HtPotential());
            factors.put("apiO15HtPotential", context.getPotentials().getO15HtPotential());
        }

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
                .type(RecommendationType.FIRST_HALF_GOALS)
                .confidence(confidence)
                .score(score)
                .market(market)
                .odds(null)
                .description(buildDescription(context, confidence, score, market, "First"))
                .factors(factors)
                .generatedAt(Instant.now())
                .build();

        log.info("First Half Goals recommendation generated: fixtureId={}, score={}, confidence={}, market={}", 
                context.getFixture().getId(), String.format("%.1f", score), confidence, market);

        return Optional.of(recommendation);
    }

    private Optional<Recommendation> createSecondHalfRecommendation(FixtureContext context, double score) {
        ConfidenceLevel confidence;
        String market;

        if (score >= THRESHOLD_STRONG_O05) {
            market = "Over 0.5 Second Half Goals";
            confidence = ConfidenceLevel.STRONG;
        } else if (score >= THRESHOLD_MODERATE_O05) {
            market = "Over 0.5 Second Half Goals";
            confidence = ConfidenceLevel.MODERATE;
        } else {
            return Optional.empty();
        }

        Map<String, Object> factors = new HashMap<>();
        factors.put("halfGoalsScore", score);
        factors.put("half", "Second");
        factors.put("lateGameIntensity", calculateLateGameIntensity(context));

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
                .type(RecommendationType.SECOND_HALF_GOALS)
                .confidence(confidence)
                .score(score)
                .market(market)
                .odds(null)
                .description(buildDescription(context, confidence, score, market, "Second"))
                .factors(factors)
                .generatedAt(Instant.now())
                .build();

        log.info("Second Half Goals recommendation generated: fixtureId={}, score={}, confidence={}, market={}", 
                context.getFixture().getId(), String.format("%.1f", score), confidence, market);

        return Optional.of(recommendation);
    }

    private double calculateGoalsAvg(Integer goals, Integer matches) {
        if (matches == null || matches == 0 || goals == null) {
            return 1.0;
        }
        return goals / (double) matches;
    }

    private double normalizeGoalsToScore(double expectedGoals) {
        return Math.min(100.0, expectedGoals * 50.0);
    }

    private double calculateBttsHtScore(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        double homeBttsHt = safePercentage(homeStats.getSeasonBttsPercentageHome()) * 0.5;
        double awayBttsHt = safePercentage(awayStats.getSeasonBttsPercentageAway()) * 0.5;
        return (homeBttsHt + awayBttsHt) * 0.5;
    }

    private String buildDescription(FixtureContext context, ConfidenceLevel confidence, 
            double score, String market, String half) {
        return String.format("%s confidence %s recommendation (%.1f%% score) - %s vs %s",
                confidence.getDisplayName(),
                market,
                score,
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());
    }

    private double safePercentage(Double value) {
        return value != null ? value : 50.0;
    }
}
