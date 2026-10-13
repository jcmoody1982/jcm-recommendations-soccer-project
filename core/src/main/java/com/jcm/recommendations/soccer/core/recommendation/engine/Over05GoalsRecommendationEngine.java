package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.core.recommendation.util.RecommendationFactory;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.*;

/**
 * UC-042: Team Over 0.5 Goals — the named team scores at least once.
 *
 * <p>Each side's chance of scoring is modelled directly: a Poisson estimate on the team's expected
 * goals (its attack against the opponent's defence, at this venue) blended with the empirical
 * rates at which the team scores and the opponent concedes. Both home and away sides are
 * considered; when both qualify the likelier one is published.
 *
 * <p>This replaces mirroring Match Result tips priced above 2.00, which published 11 picks in
 * 2026-09-13..10-11: Match Result is home-only and its 60% market blend almost never clears 55 for
 * a team priced above evens, so the board's volume was set by a different market entirely.
 *
 * <p>There is no team-goals price in the feed, so every pick is unpriced and capped at MODERATE.
 */
@Component
@Slf4j
public class Over05GoalsRecommendationEngine implements RecommendationEngine {

    private static final String MARKET_SUFFIX = "Over 0.5 Goals";

    /** Split between the Poisson estimate and the empirical scoring rates. */
    private static final double WEIGHT_POISSON = 0.50;

    // Contributions to the team's expected goals, renormalised over whichever inputs are present.
    private static final double WEIGHT_LAMBDA_SEASON = 0.50;
    private static final double WEIGHT_LAMBDA_XG = 0.30;
    private static final double WEIGHT_LAMBDA_FORM = 0.20;

    /** Typical rate at which a side scores at this venue; empirical rates are shrunk toward it. */
    static final double PRIOR_HOME_SCORES_PCT = 75.0;
    static final double PRIOR_AWAY_SCORES_PCT = 67.0;
    private static final double SHRINKAGE_PSEUDO_MATCHES = 6.0;

    /** Both sides need this many matches at the venue in question before a pick is made. */
    static final int MIN_VENUE_MATCHES = 3;

    /** Set above the home base rate so the board is not simply every home side. */
    static final double THRESHOLD_MODERATE = 80.0;

    /** Soft-cap so a one-goal line is never presented as a certainty. */
    static final double CEILING_SQUASH_START = 85.0;
    static final double MAX_REALISTIC_PROBABILITY = 92.0;

    record TeamEstimate(
            boolean home,
            String teamName,
            double expectedGoals,
            double poissonPct,
            double teamScoresPct,
            double opponentConcedesPct,
            double score) {}

    @Override
    public RecommendationType getType() {
        return RecommendationType.OVER_05_GOALS;
    }

    @Override
    public boolean isApplicable(FixtureContext context) {
        return context.hasCompleteData()
                && calculateMatchesAtVenue(context.getHomeTeamStats(), true) >= MIN_VENUE_MATCHES
                && calculateMatchesAtVenue(context.getAwayTeamStats(), false) >= MIN_VENUE_MATCHES;
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        TeamEstimate home = estimate(context, true);
        TeamEstimate away = estimate(context, false);
        TeamEstimate best = home.score() >= away.score() ? home : away;
        if (best.score() < THRESHOLD_MODERATE) {
            return Optional.empty();
        }

        String market = best.teamName() + " " + MARKET_SUFFIX;
        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(RecommendationType.OVER_05_GOALS)
                .confidence(ConfidenceLevel.MODERATE)
                .score(best.score())
                .market(market)
                .odds(null)
                .description(RecommendationFactory.buildExpectedValueDescription(
                        ConfidenceLevel.MODERATE, market, best.expectedGoals(), "expected team goals", context))
                .factors(buildFactors(best))
                .build();

        log.info("Team Over 0.5 recommendation generated: fixtureId={}, market={}, expectedGoals={}, score={}",
                context.getFixture().getId(),
                market,
                String.format("%.2f", best.expectedGoals()),
                String.format("%.1f", best.score()));

        return Optional.of(recommendation);
    }

    TeamEstimate estimate(FixtureContext context, boolean home) {
        TeamSeasonStats team = home ? context.getHomeTeamStats() : context.getAwayTeamStats();
        TeamSeasonStats opponent = home ? context.getAwayTeamStats() : context.getHomeTeamStats();
        TeamRecentForm teamForm = context.hasRecentForm()
                ? (home ? context.getHomeTeamForm() : context.getAwayTeamForm())
                : null;
        TeamRecentForm opponentForm = context.hasRecentForm()
                ? (home ? context.getAwayTeamForm() : context.getHomeTeamForm())
                : null;

        double expectedGoals = expectedTeamGoals(team, opponent, teamForm, opponentForm, home);
        double poissonPct = poissonAtLeast(expectedGoals, 1);

        double prior = home ? PRIOR_HOME_SCORES_PCT : PRIOR_AWAY_SCORES_PCT;
        double teamScoresPct = shrink(
                calculateVenueScoredPercentage(team, home), calculateMatchesAtVenue(team, home), prior);
        double opponentConcedesPct = shrink(
                100.0 - calculateCleanSheetPercentage(opponent, !home), calculateMatchesAtVenue(opponent, !home), prior);
        double empiricalPct = (teamScoresPct + opponentConcedesPct) / 2.0;

        double raw = clampScore((poissonPct * WEIGHT_POISSON) + (empiricalPct * (1.0 - WEIGHT_POISSON)));
        String teamName = home ? context.getHomeTeam().getName() : context.getAwayTeam().getName();
        return new TeamEstimate(home, teamName, expectedGoals, poissonPct, teamScoresPct, opponentConcedesPct,
                applyRealisticCeiling(raw));
    }

    /**
     * The team's goals at this venue against this opponent: its scoring rate averaged with what the
     * opponent concedes at the reverse venue, from season goals, xG and recent form.
     */
    private double expectedTeamGoals(TeamSeasonStats team, TeamSeasonStats opponent,
            TeamRecentForm teamForm, TeamRecentForm opponentForm, boolean home) {
        double seasonScored = calculateVenueGoalsAvg(team, home, 1.0);
        double seasonConceded = calculateVenueConcededAvg(opponent, !home, 1.0);
        double weightedSum = ((seasonScored + seasonConceded) / 2.0) * WEIGHT_LAMBDA_SEASON;
        double weightUsed = WEIGHT_LAMBDA_SEASON;

        Double xgFor = home ? team.getXgForAvgHome() : team.getXgForAvgAway();
        Double xgAgainst = home ? opponent.getXgAgainstAvgAway() : opponent.getXgAgainstAvgHome();
        if (xgFor != null && xgAgainst != null) {
            weightedSum += ((xgFor + xgAgainst) / 2.0) * WEIGHT_LAMBDA_XG;
            weightUsed += WEIGHT_LAMBDA_XG;
        }

        if (teamForm != null && opponentForm != null) {
            double formScored = safeDouble(home ? teamForm.getScoredAvgHome() : teamForm.getScoredAvgAway(), seasonScored);
            double formConceded = safeDouble(
                    home ? opponentForm.getConcededAvgAway() : opponentForm.getConcededAvgHome(), seasonConceded);
            weightedSum += ((formScored + formConceded) / 2.0) * WEIGHT_LAMBDA_FORM;
            weightUsed += WEIGHT_LAMBDA_FORM;
        }

        return weightedSum / weightUsed;
    }

    private static double shrink(double observedPct, int sampleSize, double priorPct) {
        if (sampleSize <= 0) {
            return priorPct;
        }
        return ((observedPct * sampleSize) + (priorPct * SHRINKAGE_PSEUDO_MATCHES))
                / (sampleSize + SHRINKAGE_PSEUDO_MATCHES);
    }

    static double applyRealisticCeiling(double rawScore) {
        if (rawScore <= CEILING_SQUASH_START) {
            return rawScore;
        }
        double headroom = MAX_REALISTIC_PROBABILITY - CEILING_SQUASH_START;
        double excess = rawScore - CEILING_SQUASH_START;
        return CEILING_SQUASH_START + headroom * (1.0 - Math.exp(-excess / headroom));
    }

    private Map<String, Object> buildFactors(TeamEstimate estimate) {
        Map<String, Object> factors = new HashMap<>();
        factors.put("team", estimate.teamName());
        factors.put("venue", estimate.home() ? "Home" : "Away");
        factors.put("teamExpectedGoals", estimate.expectedGoals());
        factors.put("poissonProbability", estimate.poissonPct());
        factors.put("teamScoresPct", estimate.teamScoresPct());
        factors.put("opponentConcedesPct", estimate.opponentConcedesPct());
        factors.put("unpricedDemotion", true);
        factors.put("calculatedScore", estimate.score());
        return factors;
    }
}
