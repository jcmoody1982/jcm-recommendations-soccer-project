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
 * - Form BTTS blended/dampened for thin venue samples
 * - Missing BTTS % / API potential omitted and weights renormalized (no fake 50s)
 * - Boosts are graded, include xGA matchup, and capped in combination
 */
@Component
@Slf4j
public class BttsRecommendationEngine implements RecommendationEngine {

    // Preferred weights when all signals present (renormalized when some are missing)
    private static final double WEIGHT_HOME_BTTS_SEASON = 0.15;
    private static final double WEIGHT_AWAY_BTTS_SEASON = 0.15;
    private static final double WEIGHT_HOME_BTTS_FORM = 0.20;
    private static final double WEIGHT_AWAY_BTTS_FORM = 0.20;
    private static final double WEIGHT_HOME_FTS_INVERSE = 0.10;
    private static final double WEIGHT_AWAY_FTS_INVERSE = 0.10;
    private static final double WEIGHT_API_POTENTIAL = 0.10;

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

    private static final double THRESHOLD_STRONG = 80.0;
    private static final double THRESHOLD_MODERATE = 65.0;

    private static final double FILTER_MIN_SCORED_PERCENTAGE = 50.0;
    private static final double FILTER_MAX_FTS_PERCENTAGE = 40.0;
    private static final int FILTER_MIN_VENUE_MATCHES = 3;

    private static final int FORM_FULL_SAMPLE = 5;
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
            signals.add(new WeightedSignal("homeBttsSeason", homeBttsSeason, WEIGHT_HOME_BTTS_SEASON));
        }
        if (awayBttsSeason != null) {
            signals.add(new WeightedSignal("awayBttsSeason", awayBttsSeason, WEIGHT_AWAY_BTTS_SEASON));
        }

        // Venue FTS inverse always available once filters passed (uses venue or overall counts)
        double homeFtsInverse = 100.0 - venueOrOverallFts(homeStats, true);
        double awayFtsInverse = 100.0 - venueOrOverallFts(awayStats, false);
        signals.add(new WeightedSignal("homeFtsInverse", homeFtsInverse, WEIGHT_HOME_FTS_INVERSE));
        signals.add(new WeightedSignal("awayFtsInverse", awayFtsInverse, WEIGHT_AWAY_FTS_INVERSE));

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

            if (homeFormPct != null && homeFormSample >= FORM_MIN_SAMPLE && homeBttsSeason != null) {
                homeBttsFormUsed = blendFormPercentage(homeBttsSeason, homeFormPct, homeFormSample);
                signals.add(new WeightedSignal("homeBttsForm", homeBttsFormUsed, WEIGHT_HOME_BTTS_FORM));
            } else if (homeFormPct != null && homeFormSample >= FORM_MIN_SAMPLE) {
                homeBttsFormUsed = blendFormPercentage(homeFormPct, homeFormPct, homeFormSample);
                signals.add(new WeightedSignal("homeBttsForm", homeBttsFormUsed, WEIGHT_HOME_BTTS_FORM));
            } else if (homeFormPct != null && homeFormSample == 0) {
                // Percentage present without W/D/L counts — treat as full sample
                homeFormSample = FORM_FULL_SAMPLE;
                homeBttsFormUsed = homeFormPct;
                signals.add(new WeightedSignal("homeBttsForm", homeBttsFormUsed, WEIGHT_HOME_BTTS_FORM));
            }

            if (awayFormPct != null && awayFormSample >= FORM_MIN_SAMPLE && awayBttsSeason != null) {
                awayBttsFormUsed = blendFormPercentage(awayBttsSeason, awayFormPct, awayFormSample);
                signals.add(new WeightedSignal("awayBttsForm", awayBttsFormUsed, WEIGHT_AWAY_BTTS_FORM));
            } else if (awayFormPct != null && awayFormSample >= FORM_MIN_SAMPLE) {
                awayBttsFormUsed = blendFormPercentage(awayFormPct, awayFormPct, awayFormSample);
                signals.add(new WeightedSignal("awayBttsForm", awayBttsFormUsed, WEIGHT_AWAY_BTTS_FORM));
            } else if (awayFormPct != null && awayFormSample == 0) {
                awayFormSample = FORM_FULL_SAMPLE;
                awayBttsFormUsed = awayFormPct;
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

        double score = clampScore(baseScore + appliedBoost);

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
                signals.size());
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

    private double blendFormPercentage(double seasonPct, double formPct, int sampleSize) {
        if (sampleSize >= FORM_FULL_SAMPLE) {
            return formPct;
        }
        double t = sampleSize / (double) FORM_FULL_SAMPLE;
        return seasonPct * (1.0 - t) + formPct * t;
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
            int signalsUsed) {}
}
