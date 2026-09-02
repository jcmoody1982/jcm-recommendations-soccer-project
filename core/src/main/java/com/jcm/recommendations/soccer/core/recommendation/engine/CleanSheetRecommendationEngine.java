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

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.*;

/**
 * UC-014: back in service after recalibration, having been paused on a ~21% hit rate.
 *
 * <p>A clean sheet is one event: the opponent fails to score. So the score is
 * {@code P(opponent scores 0) = exp(-lambda)} under a Poisson on the opponent's expected goals,
 * rather than a weighted index of defensive indicators with multipliers stacked on top.
 *
 * <p>The previous scoring summed eight 0-100 ratings and then applied six sequential multipliers
 * compounding to about x1.82, and called STRONG at 70. Clean sheets happen in roughly 32% of home
 * matches and 23% of away ones, so a 70 was never a 70 — which is the likely source of the ~21%
 * hit rate that paused this board.
 *
 * <p>Streaks, xG regression and opponent weakness are still reported, but they no longer move the
 * number. They are descriptions of the same evidence the lambda already contains, and multiplying
 * by them counted it twice.
 *
 * <p>Not Elite-eligible: the market carries no price here, and Elite only ranks picks it could
 * actually stake.
 */
@Slf4j
@Component
public class CleanSheetRecommendationEngine implements RecommendationEngine {

    private static final double WEIGHT_LAMBDA_SEASON = 0.50;
    private static final double WEIGHT_LAMBDA_XG = 0.30;
    private static final double WEIGHT_LAMBDA_FORM = 0.20;

    /**
     * League scoring rates measured from real {@code /league-teams?include=stats} payloads over 40
     * team-seasons: home sides score 1.54 per game, away sides 1.23. Used as the shrinkage prior
     * for a thin venue record. On the same sample {@code exp(-lambda)} predicted clean sheets
     * within 2.5 points of the observed rate at both venues.
     */
    private static final double PRIOR_HOME_GOALS = 1.54;
    private static final double PRIOR_AWAY_GOALS = 1.23;

    private static final double SHRINKAGE_PSEUDO_MATCHES = 6.0;

    /**
     * Base rates are 32% (home) and 23% (away), so these sit clearly above a coin-toss against the
     * market rather than above 50 on an index. STRONG corresponds to holding the opponent to about
     * 0.73 expected goals, MODERATE to about 0.97.
     */
    private static final double THRESHOLD_STRONG = 48.0;
    private static final double THRESHOLD_MODERATE = 38.0;

    /** Below this, a venue split is noise rather than a defensive record. */
    private static final int MIN_VENUE_MATCHES = 4;

    private static final int FORM_MATCHES = 5;

    // Descriptive bands, reported only — these no longer scale the score.
    private static final double XGA_ELITE_THRESHOLD = 0.80;
    private static final double XGA_STRONG_THRESHOLD = 1.10;
    private static final double XGA_AVERAGE_THRESHOLD = 1.40;
    private static final double OPP_XG_POOR_THRESHOLD = 0.80;
    private static final double OPP_XG_BELOW_AVG_THRESHOLD = 1.10;
    private static final double OPP_XG_AVERAGE_THRESHOLD = 1.50;
    private static final double WEAK_ATTACK_FTS_PCT = 40.0;

    @Override
    public RecommendationType getType() {
        return RecommendationType.CLEAN_SHEET;
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        log.debug("Analyzing Clean Sheet for fixture: fixtureId={}, {} vs {}",
                context.getFixture().getId(),
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());

        List<CleanSheetCandidate> candidates = new ArrayList<>();
        analyzeTeamCleanSheet(context, true).ifPresent(candidates::add);
        analyzeTeamCleanSheet(context, false).ifPresent(candidates::add);

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        CleanSheetCandidate best = candidates.stream()
                .max(Comparator.comparingDouble(CleanSheetCandidate::score))
                .orElseThrow();

        ConfidenceLevel confidence = determineConfidence(best.score);
        if (confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(RecommendationType.CLEAN_SHEET)
                .confidence(confidence)
                .score(best.score)
                .market(best.teamName + " Clean Sheet")
                .odds(null)
                .description(buildDescription(context, best, confidence))
                .factors(buildFactors(best, candidates))
                .build();

        log.info("Clean Sheet recommendation generated: fixtureId={}, team={}, score={}, confidence={}",
                context.getFixture().getId(), best.teamName, String.format("%.1f", best.score), confidence);

        return Optional.of(recommendation);
    }

    private Optional<CleanSheetCandidate> analyzeTeamCleanSheet(FixtureContext context, boolean isHomeTeam) {
        TeamSeasonStats teamStats = isHomeTeam ? context.getHomeTeamStats() : context.getAwayTeamStats();
        TeamSeasonStats opponentStats = isHomeTeam ? context.getAwayTeamStats() : context.getHomeTeamStats();
        if (teamStats == null || opponentStats == null) {
            return Optional.empty();
        }

        // The opponent plays at the other venue to the team keeping the sheet.
        boolean opponentIsHome = !isHomeTeam;

        int teamVenueMatches = calculateMatchesAtVenue(teamStats, isHomeTeam);
        int opponentVenueMatches = calculateMatchesAtVenue(opponentStats, opponentIsHome);
        if (teamVenueMatches < MIN_VENUE_MATCHES || opponentVenueMatches < MIN_VENUE_MATCHES) {
            return Optional.empty();
        }

        double opponentPrior = opponentIsHome ? PRIOR_HOME_GOALS : PRIOR_AWAY_GOALS;

        double opponentScored = shrink(
                calculateVenueGoalsAvg(opponentStats, opponentIsHome, opponentPrior),
                opponentVenueMatches,
                opponentPrior);
        double teamConceded = shrink(
                calculateVenueConcededAvg(teamStats, isHomeTeam, opponentPrior),
                teamVenueMatches,
                opponentPrior);
        double seasonLambda = (opponentScored + teamConceded) / 2.0;

        double weightedSum = seasonLambda * WEIGHT_LAMBDA_SEASON;
        double weightUsed = WEIGHT_LAMBDA_SEASON;

        Double opponentXgFor = opponentIsHome
                ? opponentStats.getXgForAvgHome()
                : opponentStats.getXgForAvgAway();
        Double teamXgAgainst = isHomeTeam
                ? teamStats.getXgAgainstAvgHome()
                : teamStats.getXgAgainstAvgAway();
        boolean hasXgData = opponentXgFor != null && teamXgAgainst != null;
        if (hasXgData) {
            weightedSum += ((opponentXgFor + teamXgAgainst) / 2.0) * WEIGHT_LAMBDA_XG;
            weightUsed += WEIGHT_LAMBDA_XG;
        }

        TeamRecentForm teamForm = isHomeTeam ? context.getHomeTeamForm() : context.getAwayTeamForm();
        TeamRecentForm opponentForm = isHomeTeam ? context.getAwayTeamForm() : context.getHomeTeamForm();
        Double formLambda = formLambda(teamForm, opponentForm, isHomeTeam, opponentIsHome);
        if (formLambda != null) {
            weightedSum += formLambda * WEIGHT_LAMBDA_FORM;
            weightUsed += WEIGHT_LAMBDA_FORM;
        }

        double lambda = weightedSum / weightUsed;
        double cleanSheetPct = Math.exp(-lambda) * 100.0;

        int formCleanSheets = venueFormCleanSheets(teamForm, isHomeTeam);

        return Optional.of(new CleanSheetCandidate(
                isHomeTeam ? context.getHomeTeam().getName() : context.getAwayTeam().getName(),
                isHomeTeam,
                cleanSheetPct,
                lambda,
                seasonLambda,
                hasXgData ? (opponentXgFor + teamXgAgainst) / 2.0 : null,
                formLambda,
                opponentScored,
                teamConceded,
                teamVenueMatches,
                opponentVenueMatches,
                calculateCleanSheetPercentage(teamStats, isHomeTeam),
                calculateFailedToScorePercentage(opponentStats, opponentIsHome),
                teamXgAgainst,
                opponentXgFor,
                formCleanSheets >= 3,
                formCleanSheets == 0 && teamForm != null,
                hasXgData
        ));
    }

    /**
     * Expected goals for the opponent from the last five matches: what they have been scoring at
     * their venue set against what this team has been conceding at theirs.
     */
    private Double formLambda(
            TeamRecentForm teamForm, TeamRecentForm opponentForm, boolean isHomeTeam, boolean opponentIsHome) {
        if (teamForm == null || opponentForm == null) {
            return null;
        }
        Double opponentScored = opponentIsHome ? opponentForm.getScoredAvgHome() : opponentForm.getScoredAvgAway();
        Double teamConceded = isHomeTeam ? teamForm.getConcededAvgHome() : teamForm.getConcededAvgAway();
        if (opponentScored == null || teamConceded == null) {
            return null;
        }
        return (opponentScored + teamConceded) / 2.0;
    }

    private int venueFormCleanSheets(TeamRecentForm form, boolean isHomeTeam) {
        if (form == null) {
            return -1;
        }
        return isHomeTeam ? safeInt(form.getCleanSheetsHome()) : safeInt(form.getCleanSheetsAway());
    }

    /**
     * Pulls an observed venue rate toward the league prior by {@link #SHRINKAGE_PSEUDO_MATCHES}
     * matches, so eight home games do not carry the weight of a full season.
     */
    private double shrink(double observed, int sampleSize, double prior) {
        if (sampleSize <= 0) {
            return prior;
        }
        return ((observed * sampleSize) + (prior * SHRINKAGE_PSEUDO_MATCHES))
                / (sampleSize + SHRINKAGE_PSEUDO_MATCHES);
    }

    private ConfidenceLevel determineConfidence(double cleanSheetPct) {
        if (cleanSheetPct >= THRESHOLD_STRONG) {
            return ConfidenceLevel.STRONG;
        }
        if (cleanSheetPct >= THRESHOLD_MODERATE) {
            return ConfidenceLevel.MODERATE;
        }
        return ConfidenceLevel.WEAK;
    }

    private Map<String, Object> buildFactors(CleanSheetCandidate best, List<CleanSheetCandidate> all) {
        Map<String, Object> factors = new HashMap<>();

        factors.put("team", best.teamName);
        factors.put("isHomeTeam", best.isHomeTeam);
        factors.put("cleanSheetProbability", best.score);
        factors.put("opponentExpectedGoals", best.lambda);
        factors.put("seasonExpectedGoals", best.seasonLambda);
        factors.put("opponentScoredAvgAtVenue", best.opponentScored);
        factors.put("teamConcededAvgAtVenue", best.teamConceded);
        factors.put("teamVenueMatches", best.teamVenueMatches);
        factors.put("opponentVenueMatches", best.opponentVenueMatches);
        factors.put("shrinkageApplied", true);
        factors.put("xgDataAvailable", best.hasXgData);
        if (best.xgLambda != null) {
            factors.put("xgExpectedGoals", best.xgLambda);
        }
        if (best.formLambda != null) {
            factors.put("formExpectedGoals", best.formLambda);
        }

        // Reported for context; deliberately not folded into the score.
        factors.put("teamCleanSheetSeasonPct", best.teamCsSeason);
        factors.put("opponentFailedToScoreSeasonPct", best.opponentFtsSeason);
        if (best.hasXgData) {
            factors.put("teamXgaPerGame", best.teamXgAgainst);
            factors.put("opponentXgPerGame", best.opponentXgFor);
            factors.put("teamDefensiveXgRating", xgaRating(best.teamXgAgainst));
            factors.put("opponentAttackingXgRating", opponentXgRating(best.opponentXgFor));
        }
        factors.put("hotDefensiveStreak", best.hotStreak);
        factors.put("concededInAllRecent", best.concededInAllRecent);
        factors.put("candidatesAnalyzed", all.size());

        List<String> risks = new ArrayList<>();
        if (best.concededInAllRecent) {
            risks.add("Conceded in all recent matches");
        }
        if (best.teamVenueMatches < FORM_MATCHES + MIN_VENUE_MATCHES) {
            risks.add("Thin venue record");
        }
        if (best.hasXgData && best.opponentXgFor > OPP_XG_AVERAGE_THRESHOLD) {
            risks.add("Opponent creates chances at a high rate");
        }
        factors.put("riskFlags", risks);

        List<String> positives = new ArrayList<>();
        if (best.hotStreak) {
            positives.add("Hot defensive streak (3+ clean sheets in last five)");
        }
        if (best.opponentFtsSeason > WEAK_ATTACK_FTS_PCT) {
            positives.add("Opponent has poor attacking record");
        }
        if (best.hasXgData && best.teamXgAgainst < XGA_ELITE_THRESHOLD) {
            positives.add("Elite expected goals against");
        }
        factors.put("positiveIndicators", positives);

        return factors;
    }

    private String xgaRating(double xga) {
        if (xga < XGA_ELITE_THRESHOLD) {
            return "Elite";
        }
        if (xga < XGA_STRONG_THRESHOLD) {
            return "Strong";
        }
        if (xga < XGA_AVERAGE_THRESHOLD) {
            return "Average";
        }
        return "Leaky";
    }

    private String opponentXgRating(double xg) {
        if (xg < OPP_XG_POOR_THRESHOLD) {
            return "Poor";
        }
        if (xg < OPP_XG_BELOW_AVG_THRESHOLD) {
            return "Below Average";
        }
        if (xg < OPP_XG_AVERAGE_THRESHOLD) {
            return "Average";
        }
        return "Strong";
    }

    private String buildDescription(
            FixtureContext context, CleanSheetCandidate candidate, ConfidenceLevel confidence) {
        StringBuilder colour = new StringBuilder();
        if (candidate.hotStreak) {
            colour.append("hot streak between the sticks");
        }
        if (candidate.opponentFtsSeason > WEAK_ATTACK_FTS_PCT) {
            if (!colour.isEmpty()) {
                colour.append(". ");
            }
            colour.append("opponent blanked often at this venue");
        }

        return MatchBriefCopy.narrate(MatchBriefCopy.Brief.builder()
                .confidence(confidence)
                .selection("Clean Sheet for " + candidate.teamName)
                .context(context)
                .probabilityPct(candidate.score)
                .colourNote(colour.isEmpty() ? null : colour.toString())
                .build());
    }

    private record CleanSheetCandidate(
            String teamName,
            boolean isHomeTeam,
            double score,
            double lambda,
            double seasonLambda,
            Double xgLambda,
            Double formLambda,
            double opponentScored,
            double teamConceded,
            int teamVenueMatches,
            int opponentVenueMatches,
            double teamCsSeason,
            double opponentFtsSeason,
            Double teamXgAgainst,
            Double opponentXgFor,
            boolean hotStreak,
            boolean concededInAllRecent,
            boolean hasXgData
    ) {}
}
