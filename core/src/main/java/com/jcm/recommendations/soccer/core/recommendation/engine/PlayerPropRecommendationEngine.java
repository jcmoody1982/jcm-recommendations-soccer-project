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

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.safeInt;

/**
 * Shared fixture-level player prop engine (UC-040 to score, UC-041 to assist). Both run in shadow
 * mode: picks are snapshotted and graded but kept off the boards, Results and Elite until the
 * admin shadow report shows the published probability holds up.
 *
 * <p>The player's expected events in this fixture are anchored on the team rather than on the
 * player alone: {@code lambda = playerRatePer90 x (teamExpectedGoals / teamSeasonGoalsPerMatch)
 * x expectedMinutes / 90}. The published probability is then
 * {@code availability x (1 - exp(-lambda))}, where availability is the share of the team's matches
 * the player has appeared in, because a player who does not play settles as a loss here.
 *
 * <p>Replaces the 2026-09 model, which hit ~28% while claiming 35-45%: it preferred home-only or
 * away-only rates but shrank them by whole-season minutes, so the picks that cleared the bar were
 * mostly noisy venue splits, and a flat 0.75 dampener stood in for any fixture context.
 */
@Slf4j
public abstract class PlayerPropRecommendationEngine implements RecommendationEngine {

    /** Season minutes before a player is considered; below this a per-90 rate is mostly noise. */
    static final int MIN_MINUTES = 600;

    /** Regular starters only: rotation and impact subs are hard to price without line-ups. */
    static final int MIN_MINUTES_PER_MATCH = 70;

    /** Pseudo-minutes of prior evidence blended into every per-90 rate (ten full matches). */
    static final int SHRINKAGE_PSEUDO_MINUTES = 900;

    /** How far this fixture can move the player's rate away from his season average. */
    static final double FIXTURE_FACTOR_MIN = 0.60;
    static final double FIXTURE_FACTOR_MAX = 1.60;

    private static final double FULL_MATCH_MINUTES = 90.0;

    /**
     * @param priorPer90 baseline open-play rate that every player is shrunk toward
     * @param trackingFloor probability at or above which the fixture's best candidate is snapshotted
     * @param publishBar probability the candidate would need to be published once out of shadow
     */
    protected record PropSpec(
            RecommendationType type,
            String marketVerb,
            double priorPer90,
            double trackingFloor,
            double publishBar
    ) {}

    protected abstract PropSpec spec();

    /** Open-play events this season (goals excluding penalties, or assists). */
    protected abstract int openPlayEvents(PlayerSeasonStats player);

    /** Penalty events this season, credited at face value rather than shrunk toward the prior. */
    protected abstract int penaltyEvents(PlayerSeasonStats player);

    record Candidate(
            PlayerSeasonStats player,
            boolean isHome,
            String playerName,
            String teamName,
            double ratePer90,
            double teamExpectedGoals,
            double fixtureFactor,
            double expectedMinutes,
            double availability,
            double expectedEvents,
            double score
    ) {}

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
        Optional<Candidate> best = Stream.concat(candidates(context, true), candidates(context, false))
                .max(Comparator.comparingDouble(Candidate::score));
        if (best.isEmpty() || best.get().score() < spec.trackingFloor()) {
            return Optional.empty();
        }

        Candidate pick = best.get();
        String market = pick.playerName() + " " + spec.marketVerb();
        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(spec.type())
                .confidence(ConfidenceLevel.MODERATE)
                .score(pick.score())
                .market(market)
                .odds(null)
                .description(RecommendationFactory.buildStandardDescription(
                        ConfidenceLevel.MODERATE, market, pick.score(), "score", context))
                .factors(buildFactors(pick, spec))
                .build();

        log.debug("{} shadow pick: fixtureId={}, player={}, score={}",
                spec.marketVerb(), context.getFixture().getId(), pick.playerName(),
                String.format("%.1f", pick.score()));

        return Optional.of(recommendation);
    }

    Stream<Candidate> candidates(FixtureContext context, boolean isHome) {
        List<PlayerSeasonStats> players = isHome ? context.getHomePlayers() : context.getAwayPlayers();
        if (players == null || players.isEmpty()) {
            return Stream.empty();
        }
        TeamSeasonStats team = isHome ? context.getHomeTeamStats() : context.getAwayTeamStats();
        String teamName = isHome ? context.getHomeTeam().getName() : context.getAwayTeam().getName();
        double teamExpectedGoals = TeamGoalsModel.expectedGoals(context, isHome);
        double fixtureFactor = fixtureFactor(teamExpectedGoals, TeamGoalsModel.seasonGoalsPerMatch(team));
        int teamMatches = safeInt(team.getMatchesPlayed());

        return players.stream()
                .filter(this::passesFilter)
                .map(player -> {
                    double rate = ratePer90(player);
                    double minutes = expectedMinutes(player);
                    double availability = availability(player, teamMatches);
                    double lambda = rate * fixtureFactor * (minutes / FULL_MATCH_MINUTES);
                    double score = availability * probabilityOfAtLeastOne(lambda);
                    return new Candidate(player, isHome, displayName(player), teamName, rate,
                            teamExpectedGoals, fixtureFactor, minutes, availability, lambda, score);
                });
    }

    private boolean passesFilter(PlayerSeasonStats player) {
        return player != null
                && player.getPlayerId() != null
                && displayName(player) != null
                && !isGoalkeeper(player.getPosition())
                && safeInt(player.getMinutesPlayedOverall()) >= MIN_MINUTES
                && safeInt(player.getMinPerMatch()) >= MIN_MINUTES_PER_MATCH;
    }

    /** Shrunk open-play rate plus the penalty rate, per 90 minutes. */
    double ratePer90(PlayerSeasonStats player) {
        int minutes = safeInt(player.getMinutesPlayedOverall());
        double openPlay = shrinkPer90(openPlayEvents(player), minutes, spec().priorPer90());
        double penalties = minutes > 0 ? penaltyEvents(player) * FULL_MATCH_MINUTES / minutes : 0.0;
        return openPlay + penalties;
    }

    /**
     * Empirical Bayes shrinkage: {@code (events + prior x pseudo/90) / (minutes + pseudo) x 90}.
     * A season regular keeps most of his own rate; a hot start over 600 minutes does not.
     */
    static double shrinkPer90(int events, int minutes, double priorPer90) {
        double pseudoEvents = priorPer90 * SHRINKAGE_PSEUDO_MINUTES / FULL_MATCH_MINUTES;
        return (Math.max(0, events) + pseudoEvents) * FULL_MATCH_MINUTES
                / (Math.max(0, minutes) + SHRINKAGE_PSEUDO_MINUTES);
    }

    /** This fixture's expected team goals relative to the side's season average, clamped. */
    static double fixtureFactor(double teamExpectedGoals, Double seasonGoalsPerMatch) {
        if (seasonGoalsPerMatch == null || seasonGoalsPerMatch <= 0 || teamExpectedGoals <= 0) {
            return 1.0;
        }
        double factor = teamExpectedGoals / seasonGoalsPerMatch;
        return Math.min(FIXTURE_FACTOR_MAX, Math.max(FIXTURE_FACTOR_MIN, factor));
    }

    static double expectedMinutes(PlayerSeasonStats player) {
        return Math.min(FULL_MATCH_MINUTES, safeInt(player.getMinPerMatch()));
    }

    /** Share of the team's matches the player has appeared in; unknown counts as available. */
    static double availability(PlayerSeasonStats player, int teamMatches) {
        int appearances = safeInt(player.getAppearancesOverall());
        if (teamMatches <= 0 || appearances <= 0) {
            return 1.0;
        }
        return Math.min(1.0, appearances / (double) teamMatches);
    }

    static double probabilityOfAtLeastOne(double expectedEvents) {
        if (expectedEvents <= 0) {
            return 0.0;
        }
        return 100.0 * (1.0 - Math.exp(-expectedEvents));
    }

    static boolean isGoalkeeper(String position) {
        return position != null && position.toLowerCase().contains("goal");
    }

    /**
     * Prefer FootyStats display names, then legal name parts. Returns null when nothing usable
     * is present so the engine can withhold a nameless "Player to score" tip.
     */
    static String displayName(PlayerSeasonStats player) {
        if (player == null) {
            return null;
        }
        if (player.getKnownAs() != null && !player.getKnownAs().isBlank()) {
            return player.getKnownAs().trim();
        }
        if (player.getFullName() != null && !player.getFullName().isBlank()) {
            return player.getFullName().trim();
        }
        return joinNameParts(player.getFirstName(), player.getLastName());
    }

    private static String joinNameParts(String firstName, String lastName) {
        String first = firstName != null ? firstName.trim() : "";
        String last = lastName != null ? lastName.trim() : "";
        if (first.isEmpty() && last.isEmpty()) {
            return null;
        }
        if (first.isEmpty()) {
            return last;
        }
        if (last.isEmpty()) {
            return first;
        }
        return first + " " + last;
    }

    /** Season count, falling back to the feed's per-90 figure when the raw count is missing. */
    static int seasonCount(Integer count, Double per90, Integer minutes) {
        if (count != null) {
            return Math.max(0, count);
        }
        if (per90 == null || minutes == null || minutes <= 0) {
            return 0;
        }
        return (int) Math.round(per90 * minutes / FULL_MATCH_MINUTES);
    }

    private static Map<String, Object> buildFactors(Candidate pick, PropSpec spec) {
        Map<String, Object> factors = new HashMap<>();
        factors.put("playerId", pick.player().getPlayerId());
        factors.put("playerName", pick.playerName());
        factors.put("teamName", pick.teamName());
        factors.put("isHome", pick.isHome());
        factors.put("ratePer90", pick.ratePer90());
        factors.put("teamExpectedGoals", pick.teamExpectedGoals());
        factors.put("fixtureFactor", pick.fixtureFactor());
        factors.put("expectedMinutes", pick.expectedMinutes());
        factors.put("availability", pick.availability());
        factors.put("expectedEvents", pick.expectedEvents());
        factors.put("appearances", safeInt(pick.player().getAppearancesOverall()));
        factors.put("minutes", safeInt(pick.player().getMinutesPlayedOverall()));
        factors.put("marketVerb", spec.marketVerb());
        factors.put("shadow", true);
        factors.put("meetsPublishBar", pick.score() >= spec.publishBar());
        factors.put("calculatedScore", pick.score());
        return factors;
    }
}
