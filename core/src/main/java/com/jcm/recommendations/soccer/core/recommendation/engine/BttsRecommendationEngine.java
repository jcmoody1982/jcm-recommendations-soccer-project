package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.core.recommendation.util.RecommendationFactory;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
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
 * UC-005: BTTS Yes recommendations.
 *
 * P0–P2 improvements:
 * - Venue goals/conceded use W+D+L match counts (not matchesPlayed/2)
 * - Filters are venue-aware (home side at home, away side away)
 * - Missing BTTS % / API potential omitted and weights renormalized (no fake 50s)
 * - Boosts are graded, include xGA matchup, and capped in combination
 *
 * Calibration: every observed rate is shrunk toward a league prior by its sample
 * size, the two one-sided scoring rates are combined multiplicatively (BTTS needs
 * both, so averaging them overstated it), and the total is squashed below
 * {@link #MAX_REALISTIC_PROBABILITY} instead of being clamped at 100.
 */
@Component
@Slf4j
public class BttsRecommendationEngine implements RecommendationEngine {

    // Preferred weights when all signals present (renormalized when some are missing)
    private static final double WEIGHT_HOME_BTTS_SEASON = 0.15;
    private static final double WEIGHT_AWAY_BTTS_SEASON = 0.15;
    private static final double WEIGHT_HOME_BTTS_FORM = 0.20;
    private static final double WEIGHT_AWAY_BTTS_FORM = 0.20;
    private static final double WEIGHT_BOTH_TEAMS_SCORE = 0.20;
    private static final double WEIGHT_API_POTENTIAL = 0.10;

    /**
     * Observed rates are pulled toward a prior by this many pseudo-matches, so a
     * 5-from-5 run reads as "likely" rather than as a literal 100%.
     */
    private static final double SHRINKAGE_PSEUDO_MATCHES = 6.0;
    /** League-typical share of matches where both teams score. */
    private static final double PRIOR_BTTS_RATE = 50.0;
    /** League-typical share of matches where a given team scores at least once. */
    private static final double PRIOR_TEAM_SCORES_RATE = 74.0;

    /**
     * BTTS needs two independent things to happen, so even the most lopsided
     * fixture tops out well short of certainty. Scores approach this
     * asymptotically instead of being clamped at 100.
     */
    private static final double MAX_REALISTIC_PROBABILITY = 85.0;
    private static final double CEILING_SQUASH_START = 75.0;

    // Goals context boost (graded, max amount)
    private static final double GOALS_BOOST_HOME_THRESHOLD = 1.5;
    private static final double GOALS_BOOST_AWAY_THRESHOLD = 1.0;
    private static final double GOALS_BOOST_AMOUNT = 5.0;

    // Defensive leakiness boost (graded, max amount)
    private static final double LEAKY_DEFENSE_HOME_THRESHOLD = 1.2;
    private static final double LEAKY_DEFENSE_AWAY_THRESHOLD = 1.0;
    private static final double LEAKY_DEFENSE_BOOST_AMOUNT = 4.0;

    // xG matchup boost (graded, max amount) — uses xG for + opponent xGA
    private static final double XG_COMBINED_START = 2.0;
    private static final double XG_COMBINED_FULL = 3.0;
    private static final double XG_BOOST_AMOUNT = 3.0;

    private static final double MAX_COMBINED_BOOST = 8.0;

    // Rebased onto the shrunk, ceiling-capped scale (previously 80/65 on an inflated scale)
    private static final double THRESHOLD_STRONG = 72.0;
    private static final double THRESHOLD_MODERATE = 62.0;

    private static final double FILTER_MIN_SCORED_PERCENTAGE = 50.0;
    private static final double FILTER_MAX_FTS_PERCENTAGE = 40.0;
    private static final int FILTER_MIN_VENUE_MATCHES = 3;

    private static final int FORM_MIN_SAMPLE = 3;

    @Override
    public RecommendationType getType() {
        return RecommendationType.BTTS;
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        log.debug("Analyzing BTTS for fixture: fixtureId={}, {} vs {}",
                context.getFixture().getId(),
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());

        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        if (!passesFilters(homeStats, awayStats)) {
            log.debug("Fixture failed BTTS filters: fixtureId={}", context.getFixture().getId());
            return Optional.empty();
        }

        ScoreBreakdown breakdown = calculateScore(context);
        double score = breakdown.score();
        ConfidenceLevel confidence = determineConfidence(score);

        if (confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        Map<String, Object> factors = buildFactors(context, breakdown);
        Double odds = context.hasOdds() ? context.getOdds().getOddsBttsYes() : null;

        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(RecommendationType.BTTS)
                .confidence(confidence)
                .score(score)
                .market("BTTS Yes")
                .odds(odds)
                .description(buildDescription(context, confidence, score))
                .factors(factors)
                .build();

        log.info("BTTS recommendation generated: fixtureId={}, score={}, confidence={}",
                context.getFixture().getId(), String.format("%.1f", score), confidence);

        return Optional.of(recommendation);
    }

    @Override
    public boolean isApplicable(FixtureContext context) {
        return context.hasCompleteData()
                && context.getHomeTeamStats() != null
                && context.getAwayTeamStats() != null;
    }

    private boolean passesFilters(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        int homeVenueMatches = calculateMatchesAtVenue(homeStats, true);
        int awayVenueMatches = calculateMatchesAtVenue(awayStats, false);

        double homeScoredPct;
        double awayScoredPct;
        double homeFtsPct;
        double awayFtsPct;

        if (homeVenueMatches >= FILTER_MIN_VENUE_MATCHES) {
            homeFtsPct = calculateVenueFailedToScorePercentage(homeStats, true);
            homeScoredPct = calculateVenueScoredPercentage(homeStats, true);
        } else {
            // Thin venue sample — fall back to overall
            homeFtsPct = calculateFailedToScorePercentageOverall(homeStats);
            homeScoredPct = calculateScoredPercentage(homeStats);
        }

        if (awayVenueMatches >= FILTER_MIN_VENUE_MATCHES) {
            awayFtsPct = calculateVenueFailedToScorePercentage(awayStats, false);
            awayScoredPct = calculateVenueScoredPercentage(awayStats, false);
        } else {
            awayFtsPct = calculateFailedToScorePercentageOverall(awayStats);
            awayScoredPct = calculateScoredPercentage(awayStats);
        }

        if (homeScoredPct < FILTER_MIN_SCORED_PERCENTAGE || awayScoredPct < FILTER_MIN_SCORED_PERCENTAGE) {
            return false;
        }

        return homeFtsPct <= FILTER_MAX_FTS_PERCENTAGE && awayFtsPct <= FILTER_MAX_FTS_PERCENTAGE;
    }

    private ScoreBreakdown calculateScore(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        List<WeightedSignal> signals = new ArrayList<>();

        Double homeBttsSeason = homeStats.getSeasonBttsPercentageHome();
        Double awayBttsSeason = awayStats.getSeasonBttsPercentageAway();
        if (homeBttsSeason != null) {
            double shrunk = shrink(homeBttsSeason, venueOrOverallSample(homeStats, true), PRIOR_BTTS_RATE);
            signals.add(new WeightedSignal("homeBttsSeason", shrunk, WEIGHT_HOME_BTTS_SEASON));
        }
        if (awayBttsSeason != null) {
            double shrunk = shrink(awayBttsSeason, venueOrOverallSample(awayStats, false), PRIOR_BTTS_RATE);
            signals.add(new WeightedSignal("awayBttsSeason", shrunk, WEIGHT_AWAY_BTTS_SEASON));
        }

        // Both teams scoring is a conjunction, so combine the two one-sided rates
        // multiplicatively rather than averaging them.
        double bothScoreEstimate = bothTeamsScoreEstimate(homeStats, awayStats);
        signals.add(new WeightedSignal("bothTeamsScore", bothScoreEstimate, WEIGHT_BOTH_TEAMS_SCORE));

        int homeFormSample = 0;
        int awayFormSample = 0;
        Double homeBttsFormUsed = null;
        Double awayBttsFormUsed = null;

        if (context.hasRecentForm()) {
            TeamRecentForm homeForm = context.getHomeTeamForm();
            TeamRecentForm awayForm = context.getAwayTeamForm();
            homeFormSample = formSampleSize(homeForm, true);
            awayFormSample = formSampleSize(awayForm, false);

            Double homeFormPct = homeForm != null ? homeForm.getBttsPercentageHome() : null;
            Double awayFormPct = awayForm != null ? awayForm.getBttsPercentageAway() : null;

            if (homeFormPct != null && homeFormSample == 0) {
                // Percentage present without W/D/L counts — assume the smallest
                // credible sample rather than trusting it as a full one.
                homeFormSample = FORM_MIN_SAMPLE;
            }
            if (homeFormPct != null && homeFormSample >= FORM_MIN_SAMPLE) {
                homeBttsFormUsed = shrinkForm(homeFormPct, homeFormSample, homeBttsSeason);
                signals.add(new WeightedSignal("homeBttsForm", homeBttsFormUsed, WEIGHT_HOME_BTTS_FORM));
            }

            if (awayFormPct != null && awayFormSample == 0) {
                awayFormSample = FORM_MIN_SAMPLE;
            }
            if (awayFormPct != null && awayFormSample >= FORM_MIN_SAMPLE) {
                awayBttsFormUsed = shrinkForm(awayFormPct, awayFormSample, awayBttsSeason);
                signals.add(new WeightedSignal("awayBttsForm", awayBttsFormUsed, WEIGHT_AWAY_BTTS_FORM));
            }
        }

        Double apiPotential = null;
        if (context.hasPotentials() && context.getPotentials().getBttsPotential() != null) {
            apiPotential = context.getPotentials().getBttsPotential();
            signals.add(new WeightedSignal("apiPotential", apiPotential, WEIGHT_API_POTENTIAL));
        }

        double baseScore = renormalizedAverage(signals);

        double goalsBoost = calculateGoalsBoost(homeStats, awayStats);
        double leakyDefenseBoost = calculateLeakyDefenseBoost(homeStats, awayStats);
        double xgBoost = calculateXgBoost(homeStats, awayStats);
        double rawCombinedBoost = goalsBoost + leakyDefenseBoost + xgBoost;
        double appliedBoost = Math.min(MAX_COMBINED_BOOST, rawCombinedBoost);

        double rawScore = clampScore(baseScore + appliedBoost);
        double score = applyRealisticCeiling(rawScore);

        return new ScoreBreakdown(
                score,
                baseScore,
                goalsBoost,
                leakyDefenseBoost,
                xgBoost,
                appliedBoost,
                rawCombinedBoost > MAX_COMBINED_BOOST,
                homeFormSample,
                awayFormSample,
                homeBttsFormUsed,
                awayBttsFormUsed,
                apiPotential,
                signals.size(),
                bothScoreEstimate,
                rawScore);
    }

    /**
     * Pulls an observed rate toward {@code priorPct} by {@link #SHRINKAGE_PSEUDO_MATCHES}
     * matches, so short runs cannot assert near-certainty.
     */
    private double shrink(double observedPct, int sampleSize, double priorPct) {
        if (sampleSize <= 0) {
            return priorPct;
        }
        return ((observedPct * sampleSize) + (priorPct * SHRINKAGE_PSEUDO_MATCHES))
                / (sampleSize + SHRINKAGE_PSEUDO_MATCHES);
    }

    /** Recent form is shrunk toward the season rate when known, else the league prior. */
    private double shrinkForm(double formPct, int sampleSize, Double seasonPct) {
        return shrink(formPct, sampleSize, seasonPct != null ? seasonPct : PRIOR_BTTS_RATE);
    }

    /**
     * P(both score) from each side's shrunk scoring rate. Multiplicative because
     * the two events are separate requirements, not interchangeable evidence.
     */
    private double bothTeamsScoreEstimate(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        double homeScores = shrink(
                100.0 - venueOrOverallFts(homeStats, true),
                venueOrOverallSample(homeStats, true),
                PRIOR_TEAM_SCORES_RATE);
        double awayScores = shrink(
                100.0 - venueOrOverallFts(awayStats, false),
                venueOrOverallSample(awayStats, false),
                PRIOR_TEAM_SCORES_RATE);
        return (homeScores / 100.0) * awayScores;
    }

    /**
     * Compresses everything above {@link #CEILING_SQUASH_START} into the gap below
     * {@link #MAX_REALISTIC_PROBABILITY}. Ranking is preserved and the ceiling is
     * never actually reached, so BTTS can no longer be presented as a certainty.
     */
    private double applyRealisticCeiling(double rawScore) {
        if (rawScore <= CEILING_SQUASH_START) {
            return rawScore;
        }
        double headroom = MAX_REALISTIC_PROBABILITY - CEILING_SQUASH_START;
        double excess = rawScore - CEILING_SQUASH_START;
        return CEILING_SQUASH_START + headroom * (1.0 - Math.exp(-excess / headroom));
    }

    private int venueOrOverallSample(TeamSeasonStats stats, boolean isHome) {
        int venueMatches = calculateMatchesAtVenue(stats, isHome);
        if (venueMatches >= FILTER_MIN_VENUE_MATCHES) {
            return venueMatches;
        }
        return stats != null ? safeInt(stats.getMatchesPlayed()) : 0;
    }

    private double venueOrOverallFts(TeamSeasonStats stats, boolean isHome) {
        int venueMatches = calculateMatchesAtVenue(stats, isHome);
        if (venueMatches >= FILTER_MIN_VENUE_MATCHES) {
            return calculateVenueFailedToScorePercentage(stats, isHome);
        }
        return calculateFailedToScorePercentageOverall(stats);
    }

    private double renormalizedAverage(List<WeightedSignal> signals) {
        if (signals.isEmpty()) {
            return 0.0;
        }
        double weightSum = signals.stream().mapToDouble(WeightedSignal::weight).sum();
        if (weightSum <= 0) {
            return 0.0;
        }
        double total = 0.0;
        for (WeightedSignal signal : signals) {
            total += signal.value() * (signal.weight() / weightSum);
        }
        return total;
    }

    private int formSampleSize(TeamRecentForm form, boolean isHome) {
        if (form == null) {
            return 0;
        }
        int wins = isHome ? safeInt(form.getWinsHome()) : safeInt(form.getWinsAway());
        int draws = isHome ? safeInt(form.getDrawsHome()) : safeInt(form.getDrawsAway());
        int losses = isHome ? safeInt(form.getLossesHome()) : safeInt(form.getLossesAway());
        return wins + draws + losses;
    }

    /**
     * Graded strength in [0,1]: 0 below (threshold - 0.25), 1 at (threshold + 0.5).
     */
    private double gradedStrength(double avg, double threshold) {
        double start = threshold - 0.25;
        double end = threshold + 0.5;
        if (avg <= start) {
            return 0.0;
        }
        if (avg >= end) {
            return 1.0;
        }
        return (avg - start) / (end - start);
    }

    private double calculateGoalsBoost(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        double homeGoalsAvg = calculateVenueGoalsAvg(homeStats, true);
        double awayGoalsAvg = calculateVenueGoalsAvg(awayStats, false);
        if (homeGoalsAvg <= 0 && awayGoalsAvg <= 0) {
            return 0.0;
        }
        double homeStrength = gradedStrength(homeGoalsAvg, GOALS_BOOST_HOME_THRESHOLD);
        double awayStrength = gradedStrength(awayGoalsAvg, GOALS_BOOST_AWAY_THRESHOLD);
        // Both must contribute — geometric mean softens single-sided cliffs
        double combined = Math.sqrt(homeStrength * awayStrength);
        return GOALS_BOOST_AMOUNT * combined;
    }

    private double calculateLeakyDefenseBoost(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        double homeConcededAvg = calculateVenueConcededAvg(homeStats, true);
        double awayConcededAvg = calculateVenueConcededAvg(awayStats, false);
        double homeStrength = gradedStrength(homeConcededAvg, LEAKY_DEFENSE_HOME_THRESHOLD);
        double awayStrength = gradedStrength(awayConcededAvg, LEAKY_DEFENSE_AWAY_THRESHOLD);
        double combined = Math.sqrt(homeStrength * awayStrength);
        return LEAKY_DEFENSE_BOOST_AMOUNT * combined;
    }

    private double calculateXgBoost(TeamSeasonStats homeStats, TeamSeasonStats awayStats) {
        Double homeXgFor = homeStats != null ? homeStats.getXgForAvgHome() : null;
        Double awayXgFor = awayStats != null ? awayStats.getXgForAvgAway() : null;
        if (homeXgFor == null || awayXgFor == null) {
            return 0.0;
        }

        Double awayXga = awayStats.getXgAgainstAvgAway();
        Double homeXga = homeStats.getXgAgainstAvgHome();

        double homeAttack;
        double awayAttack;
        if (awayXga != null && homeXga != null) {
            // Matchup: attack xG blended with opponent xGA
            homeAttack = (homeXgFor + awayXga) / 2.0;
            awayAttack = (awayXgFor + homeXga) / 2.0;
        } else {
            homeAttack = homeXgFor;
            awayAttack = awayXgFor;
        }

        double combined = homeAttack + awayAttack;
        if (combined <= XG_COMBINED_START) {
            return 0.0;
        }
        if (combined >= XG_COMBINED_FULL) {
            return XG_BOOST_AMOUNT;
        }
        double t = (combined - XG_COMBINED_START) / (XG_COMBINED_FULL - XG_COMBINED_START);
        return XG_BOOST_AMOUNT * t;
    }

    private ConfidenceLevel determineConfidence(double score) {
        if (score >= THRESHOLD_STRONG) {
            return ConfidenceLevel.STRONG;
        } else if (score >= THRESHOLD_MODERATE) {
            return ConfidenceLevel.MODERATE;
        }
        return ConfidenceLevel.WEAK;
    }

    private Map<String, Object> buildFactors(FixtureContext context, ScoreBreakdown breakdown) {
        Map<String, Object> factors = new HashMap<>();

        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        if (homeStats.getSeasonBttsPercentageHome() != null) {
            factors.put("homeBttsSeasonPct", homeStats.getSeasonBttsPercentageHome());
        }
        if (awayStats.getSeasonBttsPercentageAway() != null) {
            factors.put("awayBttsSeasonPct", awayStats.getSeasonBttsPercentageAway());
        }

        factors.put("homeFailedToScorePct", venueOrOverallFts(homeStats, true));
        factors.put("awayFailedToScorePct", venueOrOverallFts(awayStats, false));
        factors.put("homeVenueMatches", calculateMatchesAtVenue(homeStats, true));
        factors.put("awayVenueMatches", calculateMatchesAtVenue(awayStats, false));
        factors.put("homeVenueScoredPct", calculateVenueScoredPercentage(homeStats, true));
        factors.put("awayVenueScoredPct", calculateVenueScoredPercentage(awayStats, false));
        factors.put("filtersVenueAware", true);

        factors.put("formDataAvailable", context.hasRecentForm());
        factors.put("homeFormSampleSize", breakdown.homeFormSample());
        factors.put("awayFormSampleSize", breakdown.awayFormSample());
        if (breakdown.homeBttsFormUsed() != null) {
            factors.put("homeBttsFormPct", breakdown.homeBttsFormUsed());
        }
        if (breakdown.awayBttsFormUsed() != null) {
            factors.put("awayBttsFormPct", breakdown.awayBttsFormUsed());
        }

        if (breakdown.apiPotential() != null) {
            factors.put("apiPotential", breakdown.apiPotential());
        }
        factors.put("signalsUsed", breakdown.signalsUsed());
        factors.put("missingDataRenormalized", true);

        double homeGoalsAvg = calculateVenueGoalsAvg(homeStats, true);
        double awayGoalsAvg = calculateVenueGoalsAvg(awayStats, false);
        factors.put("homeGoalsAvgHome", homeGoalsAvg);
        factors.put("awayGoalsAvgAway", awayGoalsAvg);

        factors.put("goalsBoostApplied", breakdown.goalsBoost() > 0.01);
        if (breakdown.goalsBoost() > 0.01) {
            factors.put("goalsBoostAmount", breakdown.goalsBoost());
        }

        double homeConcededAvg = calculateVenueConcededAvg(homeStats, true);
        double awayConcededAvg = calculateVenueConcededAvg(awayStats, false);
        factors.put("homeConcededAvgHome", homeConcededAvg);
        factors.put("awayConcededAvgAway", awayConcededAvg);

        factors.put("leakyDefenseBoostApplied", breakdown.leakyDefenseBoost() > 0.01);
        if (breakdown.leakyDefenseBoost() > 0.01) {
            factors.put("leakyDefenseBoostAmount", breakdown.leakyDefenseBoost());
        }

        Double homeXgFor = homeStats.getXgForAvgHome();
        Double awayXgFor = awayStats.getXgForAvgAway();
        Double homeXgAgainst = homeStats.getXgAgainstAvgHome();
        Double awayXgAgainst = awayStats.getXgAgainstAvgAway();

        factors.put("xgDataAvailable", homeXgFor != null && awayXgFor != null);
        if (homeXgFor != null) {
            factors.put("homeXgForAvgHome", homeXgFor);
        }
        if (awayXgFor != null) {
            factors.put("awayXgForAvgAway", awayXgFor);
        }
        if (homeXgAgainst != null) {
            factors.put("homeXgAgainstAvgHome", homeXgAgainst);
        }
        if (awayXgAgainst != null) {
            factors.put("awayXgAgainstAvgAway", awayXgAgainst);
        }

        factors.put("xgBoostApplied", breakdown.xgBoost() > 0.01);
        if (breakdown.xgBoost() > 0.01) {
            factors.put("xgBoostAmount", breakdown.xgBoost());
            if (homeXgFor != null && awayXgFor != null) {
                if (homeXgAgainst != null && awayXgAgainst != null) {
                    double matchup = ((homeXgFor + awayXgAgainst) / 2.0)
                            + ((awayXgFor + homeXgAgainst) / 2.0);
                    factors.put("combinedXgMatchup", matchup);
                } else {
                    factors.put("combinedXg", homeXgFor + awayXgFor);
                }
            }
        }

        factors.put("baseScore", breakdown.baseScore());
        factors.put("appliedBoost", breakdown.appliedBoost());
        factors.put("boostCapped", breakdown.boostCapped());
        factors.put("maxCombinedBoost", MAX_COMBINED_BOOST);
        factors.put("calculatedScore", breakdown.score());

        factors.put("bothTeamsScoreEstimate", breakdown.bothScoreEstimate());
        factors.put("shrinkageApplied", true);
        factors.put("realisticCeiling", MAX_REALISTIC_PROBABILITY);
        factors.put("ceilingApplied", breakdown.rawScore() > CEILING_SQUASH_START);

        return factors;
    }

    private String buildDescription(FixtureContext context, ConfidenceLevel confidence, double score) {
        return RecommendationFactory.buildStandardDescription(
                confidence, "BTTS", score, "score", context);
    }

    private record WeightedSignal(String name, double value, double weight) {}

    private record ScoreBreakdown(
            double score,
            double baseScore,
            double goalsBoost,
            double leakyDefenseBoost,
            double xgBoost,
            double appliedBoost,
            boolean boostCapped,
            int homeFormSample,
            int awayFormSample,
            Double homeBttsFormUsed,
            Double awayBttsFormUsed,
            Double apiPotential,
            int signalsUsed,
            double bothScoreEstimate,
            double rawScore) {}
}
