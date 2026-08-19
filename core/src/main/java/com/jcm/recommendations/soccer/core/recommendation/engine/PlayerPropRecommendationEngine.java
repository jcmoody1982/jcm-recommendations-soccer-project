package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.core.recommendation.util.RecommendationFactory;
import com.jcm.recommendations.soccer.domain.PlayerSeasonStats;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.calculateGoalsAvg;
import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.clampScore;
import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.normalizeGoals;
import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.safeInt;

/**
 * Shared fixture-level player prop engine. Line-specific rates and labels come from
 * {@link PropSpec} (UC-040 to score, UC-041 to assist).
 */
@Slf4j
public abstract class PlayerPropRecommendationEngine implements RecommendationEngine {

    private static final double WEIGHT_RATE = 0.55;
    private static final double WEIGHT_MINUTES = 0.20;
    private static final double WEIGHT_OPPONENT = 0.25;
    private static final double THRESHOLD_STRONG = 72.0;
    private static final double THRESHOLD_MODERATE = 58.0;
    private static final int MIN_APPEARANCES = 5;
    private static final int MIN_MINUTES = 270;
    private static final int MIN_MINUTES_PER_MATCH = 45;
    private static final double MINUTES_FOR_FULL_RELIABILITY = 1200.0;

    protected record PropSpec(
            RecommendationType type,
            String marketVerb,
            double minPer90,
            double elitePer90
    ) {}

    protected abstract PropSpec spec();

    protected abstract double per90(PlayerSeasonStats player, boolean isHome);

    @Override
    public RecommendationType getType() {
        return spec().type();
    }

    @Override
    public boolean isApplicable(FixtureContext context) {
        return context.hasCompleteData() && context.hasPlayerStats();
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        PropSpec spec = spec();
        List<Candidate> candidates = new ArrayList<>();
        considerPlayers(context, true, spec, candidates);
        considerPlayers(context, false, spec, candidates);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        Candidate best = candidates.stream()
                .max(Comparator.comparingDouble(Candidate::score))
                .orElse(null);
        if (best == null) {
            return Optional.empty();
        }

        ConfidenceLevel confidence = determineConfidence(best.score);
        if (confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        String playerName = displayName(best.player);
        String market = playerName + " " + spec.marketVerb();
        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(spec.type())
                .confidence(confidence)
                .score(best.score)
                .market(market)
                .odds(null)
                .description(RecommendationFactory.buildStandardDescription(
                        confidence, market, best.score, "score", context))
                .factors(buildFactors(best, spec, playerName))
                .build();

        log.info("{} recommendation generated: fixtureId={}, player={}, score={}, confidence={}",
                spec.marketVerb(),
                context.getFixture().getId(),
                playerName,
                String.format("%.1f", best.score),
                confidence);

        return Optional.of(recommendation);
    }

    private void considerPlayers(
            FixtureContext context,
            boolean isHome,
            PropSpec spec,
            List<Candidate> candidates) {
        List<PlayerSeasonStats> players = isHome ? context.getHomePlayers() : context.getAwayPlayers();
        if (players == null) {
            return;
        }
        TeamSeasonStats opponentStats = isHome ? context.getAwayTeamStats() : context.getHomeTeamStats();
        String teamName = isHome ? context.getHomeTeam().getName() : context.getAwayTeam().getName();
        double opponentConcededAvg = opponentConcededAvg(opponentStats, isHome);

        for (PlayerSeasonStats player : players) {
            if (!passesFilter(player, spec, isHome)) {
                continue;
            }
            double rate = per90(player, isHome);
            double rateScore = Math.min(100.0, (rate / spec.elitePer90()) * 100.0);
            double minutesScore = Math.min(100.0, safeInt(player.getMinutesPlayedOverall()) / MINUTES_FOR_FULL_RELIABILITY * 100.0);
            double opponentScore = normalizeGoals(opponentConcededAvg);
            double score = clampScore(
                    (rateScore * WEIGHT_RATE)
                            + (minutesScore * WEIGHT_MINUTES)
                            + (opponentScore * WEIGHT_OPPONENT)
                            + rankBoost(player));
            candidates.add(new Candidate(player, isHome, teamName, rate, opponentConcededAvg, score));
        }
    }

    private boolean passesFilter(PlayerSeasonStats player, PropSpec spec, boolean isHome) {
        if (player == null || player.getPlayerId() == null) {
            return false;
        }
        if (isGoalkeeper(player.getPosition())) {
            return false;
        }
        int appearances = safeInt(player.getAppearancesOverall());
        int minutes = safeInt(player.getMinutesPlayedOverall());
        if (appearances < MIN_APPEARANCES && minutes < MIN_MINUTES) {
            return false;
        }
        if (safeInt(player.getMinPerMatch(), 90) < MIN_MINUTES_PER_MATCH) {
            return false;
        }
        return per90(player, isHome) >= spec.minPer90();
    }

    private static double opponentConcededAvg(TeamSeasonStats opponent, boolean playerIsHome) {
        if (opponent == null) {
            return 1.0;
        }
        if (playerIsHome) {
            return calculateGoalsAvg(opponent.getSeasonConcededAway(), opponent.getMatchesPlayed(), 1.0);
        }
        return calculateGoalsAvg(opponent.getSeasonConcededHome(), opponent.getMatchesPlayed(), 1.0);
    }

    private static double rankBoost(PlayerSeasonStats player) {
        Integer rank = player.getRankInClubTopScorer();
        if (rank == null) {
            return 0.0;
        }
        if (rank == 1) {
            return 8.0;
        }
        if (rank == 2) {
            return 4.0;
        }
        return 0.0;
    }

    private static ConfidenceLevel determineConfidence(double score) {
        if (score >= THRESHOLD_STRONG) {
            return ConfidenceLevel.STRONG;
        }
        if (score >= THRESHOLD_MODERATE) {
            return ConfidenceLevel.MODERATE;
        }
        return ConfidenceLevel.WEAK;
    }

    private static Map<String, Object> buildFactors(Candidate best, PropSpec spec, String playerName) {
        Map<String, Object> factors = new HashMap<>();
        factors.put("playerId", best.player.getPlayerId());
        factors.put("playerName", playerName);
        factors.put("teamName", best.teamName);
        factors.put("isHome", best.isHome);
        factors.put("per90", best.per90);
        factors.put("appearances", safeInt(best.player.getAppearancesOverall()));
        factors.put("minutes", safeInt(best.player.getMinutesPlayedOverall()));
        factors.put("opponentConcededAvg", best.opponentConcededAvg);
        if (best.player.getRankInClubTopScorer() != null) {
            factors.put("rankInClubTopScorer", best.player.getRankInClubTopScorer());
        }
        factors.put("marketVerb", spec.marketVerb());
        factors.put("calculatedScore", best.score);
        return factors;
    }

    static double venuePer90(Integer events, Integer minutes, double overallFallback) {
        int mins = safeInt(minutes);
        if (mins < 90) {
            return overallFallback;
        }
        return safeInt(events) * 90.0 / mins;
    }

    static boolean isGoalkeeper(String position) {
        return position != null && position.toLowerCase().contains("goal");
    }

    static String displayName(PlayerSeasonStats player) {
        if (player.getKnownAs() != null && !player.getKnownAs().isBlank()) {
            return player.getKnownAs().trim();
        }
        if (player.getFullName() != null && !player.getFullName().isBlank()) {
            return player.getFullName().trim();
        }
        return "Player";
    }

    private record Candidate(
            PlayerSeasonStats player,
            boolean isHome,
            String teamName,
            double per90,
            double opponentConcededAvg,
            double score
    ) {}
}
