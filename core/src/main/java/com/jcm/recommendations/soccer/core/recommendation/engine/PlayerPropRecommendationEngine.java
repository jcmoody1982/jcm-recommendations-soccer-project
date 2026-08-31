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
import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.safeInt;

/**
 * Shared fixture-level player prop engine. Line-specific rates, priors and thresholds come from
 * {@link PropSpec} (UC-040 to score, UC-041 to assist).
 *
 * <p>The published score is the modelled probability that the player records at least one event
 * in this fixture, from a Poisson draw on their expected event count:
 * {@code P(>=1) = 1 - exp(-lambda)}. Lambda is the player's shrunk per-90 rate scaled by the
 * minutes they are expected to play and by how leaky the opponent is.
 *
 * <p>This replaces an earlier weighted index that combined rate, minutes and opponent strength on
 * a 0-100 scale. That index answered "how good is this player?" rather than "will he do it in this
 * match?", so it published scores far above what the market can produce: at the elite per-90 rates
 * below, a full ninety minutes is only a 42% chance of a goal and a 33% chance of an assist, yet
 * the old moderate threshold alone was 58. Every pick it published overstated its own ceiling.
 */
@Slf4j
public abstract class PlayerPropRecommendationEngine implements RecommendationEngine {

    private static final int MIN_APPEARANCES = 5;
    private static final int MIN_MINUTES = 270;
    private static final int MIN_MINUTES_PER_MATCH = 45;

    /**
     * Pseudo-minutes of prior evidence blended into every per-90 rate. Five matches' worth, so a
     * player with 300 minutes is held close to the baseline while a season regular is trusted.
     */
    private static final int SHRINKAGE_PSEUDO_MINUTES = 450;

    /** Typical goals conceded per match, used to turn opponent leakiness into a multiplier. */
    private static final double LEAGUE_AVG_CONCEDED = 1.35;
    private static final double OPPONENT_FACTOR_MIN = 0.75;
    private static final double OPPONENT_FACTOR_MAX = 1.35;

    /** Assumed minutes when a player has no recorded per-match average. */
    private static final double DEFAULT_EXPECTED_MINUTES = 70.0;
    private static final double FULL_MATCH_MINUTES = 90.0;

    /** A club's primary scorer takes a larger share of the team's chances than a squad player. */
    private static final double RANK_ONE_MULTIPLIER = 1.10;
    private static final double RANK_TWO_MULTIPLIER = 1.05;

    /**
     * @param minPer90 rate a player must clear to be considered at all
     * @param elitePer90 rate treated as the top of the realistic range for this market
     * @param priorPer90 baseline rate that thin samples are shrunk toward
     * @param thresholdStrong published probability at or above which the pick is STRONG
     * @param thresholdModerate published probability at or above which the pick is MODERATE
     */
    protected record PropSpec(
            RecommendationType type,
            String marketVerb,
            double minPer90,
            double elitePer90,
            double priorPer90,
            double thresholdStrong,
            double thresholdModerate
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

        ConfidenceLevel confidence = determineConfidence(best.score, spec);
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
        double opponentFactor = opponentFactor(opponentConcededAvg);

        for (PlayerSeasonStats player : players) {
            if (!passesFilter(player, spec, isHome)) {
                continue;
            }
            double rate = per90(player, isHome);
            double shrunkRate = shrinkPer90(rate, safeInt(player.getMinutesPlayedOverall()), spec.priorPer90());
            double expectedMinutes = expectedMinutes(player);
            double expectedEvents = shrunkRate
                    * (expectedMinutes / FULL_MATCH_MINUTES)
                    * opponentFactor
                    * rankMultiplier(player);
            double score = probabilityOfAtLeastOne(expectedEvents);
            candidates.add(new Candidate(
                    player, isHome, teamName, rate, shrunkRate, expectedMinutes,
                    opponentConcededAvg, expectedEvents, score));
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

    /**
     * Poisson probability of at least one event given an expected count. Naturally bounded below
     * 100 without a clamp, so candidates stay rank-ordered right at the top of the range.
     */
    static double probabilityOfAtLeastOne(double expectedEvents) {
        if (expectedEvents <= 0) {
            return 0.0;
        }
        return 100.0 * (1.0 - Math.exp(-expectedEvents));
    }

    /**
     * Empirical Bayes shrinkage on a per-90 rate. Without this a player with 300 minutes and three
     * goals reads as 0.90 per 90 and beats a proven starter, which is how the old scoring kept
     * selecting small-sample outliers.
     */
    static double shrinkPer90(double observedPer90, int minutesPlayed, double priorPer90) {
        if (minutesPlayed <= 0) {
            return priorPer90;
        }
        double weight = minutesPlayed / (double) (minutesPlayed + SHRINKAGE_PSEUDO_MINUTES);
        return (weight * observedPer90) + ((1.0 - weight) * priorPer90);
    }

    /** Minutes the player is expected to be on the pitch, from their season per-match average. */
    static double expectedMinutes(PlayerSeasonStats player) {
        int perMatch = safeInt(player.getMinPerMatch());
        if (perMatch <= 0) {
            return DEFAULT_EXPECTED_MINUTES;
        }
        return Math.min(FULL_MATCH_MINUTES, perMatch);
    }

    /** Opponent leakiness as a multiplier around 1.0, so it scales lambda rather than rescaling it. */
    static double opponentFactor(double opponentConcededAvg) {
        if (opponentConcededAvg <= 0) {
            return 1.0;
        }
        double factor = opponentConcededAvg / LEAGUE_AVG_CONCEDED;
        return Math.min(OPPONENT_FACTOR_MAX, Math.max(OPPONENT_FACTOR_MIN, factor));
    }

    static double rankMultiplier(PlayerSeasonStats player) {
        Integer rank = player.getRankInClubTopScorer();
        if (rank == null) {
            return 1.0;
        }
        if (rank == 1) {
            return RANK_ONE_MULTIPLIER;
        }
        if (rank == 2) {
            return RANK_TWO_MULTIPLIER;
        }
        return 1.0;
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

    private static ConfidenceLevel determineConfidence(double score, PropSpec spec) {
        if (score >= spec.thresholdStrong()) {
            return ConfidenceLevel.STRONG;
        }
        if (score >= spec.thresholdModerate()) {
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
        factors.put("shrunkPer90", best.shrunkPer90);
        factors.put("expectedMinutes", best.expectedMinutes);
        factors.put("expectedEvents", best.expectedEvents);
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
            double shrunkPer90,
            double expectedMinutes,
            double opponentConcededAvg,
            double expectedEvents,
            double score
    ) {}
}
