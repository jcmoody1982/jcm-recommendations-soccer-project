package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.core.recommendation.util.RecommendationFactory;
import com.jcm.recommendations.soccer.core.recommendation.util.MatchBriefCopy;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.*;

/**
 * UC-018: Home/Away Specialist Recommendations
 * 
 * Identifies teams with significant performance gaps between home and away matches.
 * Classifications: Home Specialist, Away Specialist, Poor Traveler, Home Fortress, Balanced.
 * Uses PPG, win rates, goals, xG disparities, and recent form context.
 */
@Component
@Slf4j
public class HomeAwaySpecialistEngine implements RecommendationEngine {

    // PPG thresholds for home specialist
    private static final double THRESHOLD_STRONG_HOME_PPG_DIFF = 0.8;
    private static final double THRESHOLD_MODERATE_HOME_PPG_DIFF = 0.5;

    // Win percentage thresholds for home specialist
    private static final double THRESHOLD_STRONG_HOME_WIN_DIFF = 25.0;
    private static final double THRESHOLD_MODERATE_HOME_WIN_DIFF = 15.0;

    // Goals thresholds for home specialist
    private static final double THRESHOLD_STRONG_GOALS_DIFF_PCT = 40.0;
    private static final double THRESHOLD_MODERATE_GOALS_DIFF_PCT = 25.0;

    // Poor traveler thresholds
    private static final double THRESHOLD_POOR_TRAVELER_PPG = 0.8;
    private static final double THRESHOLD_POOR_TRAVELER_WIN_PCT = 20.0;
    private static final double THRESHOLD_POOR_TRAVELER_GOALS = 0.8;
    private static final double THRESHOLD_STRONG_POOR_TRAVELER_WIN_PCT = 15.0;

    // Away specialist thresholds — paused (Aug 2026 recalibration)

    // Fortress thresholds
    private static final double THRESHOLD_FORTRESS_WIN_PCT = 70.0;
    private static final double THRESHOLD_FORTRESS_LOSS_PCT = 15.0;
    private static final double THRESHOLD_FORTRESS_CONCEDED = 0.8;

    // Disparity score thresholds
    private static final double THRESHOLD_STRONG_DISPARITY = 40.0;
    private static final double THRESHOLD_MODERATE_DISPARITY = 25.0;

    // Form divergence threshold
    private static final double THRESHOLD_FORM_DIVERGENCE = 0.3;

    @Override
    public RecommendationType getType() {
        return RecommendationType.HOME_AWAY_SPECIALIST;
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        log.debug("Analyzing Home/Away Specialist for fixture: fixtureId={}, {} vs {}", 
                context.getFixture().getId(),
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());

        List<SpecialistCandidate> candidates = new ArrayList<>();

        // Analyze home team
        analyzeHomeTeamAsHomeSpecialist(context).ifPresent(candidates::add);
        analyzeHomeTeamAsFortress(context).ifPresent(candidates::add);

        // Analyze away team — poor traveler only (away specialist paused)
        analyzeAwayTeamAsPoorTraveler(context).ifPresent(candidates::add);

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        // Get best candidate by disparity score
        SpecialistCandidate best = candidates.stream()
                .max(Comparator.comparingDouble(SpecialistCandidate::overallDisparityScore))
                .orElse(null);

        if (best == null || best.confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        Map<String, Object> factors = buildFactors(context, best, candidates);

        // Home-only picks after away specialist pause
        String teamToBack = context.getHomeTeam().getName();

        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(RecommendationType.HOME_AWAY_SPECIALIST)
                .confidence(best.confidence)
                .score(best.overallDisparityScore)
                .market(teamToBack)
                .odds(null)
                .description(buildDescription(context, best))
                .factors(factors)
                .build();

        log.info("Home/Away Specialist recommendation generated: fixtureId={}, classification={}, team={}, score={}, confidence={}", 
                context.getFixture().getId(), best.classification, best.teamName,
                String.format("%.1f", best.overallDisparityScore), best.confidence);

        return Optional.of(recommendation);
    }

    private Optional<SpecialistCandidate> analyzeHomeTeamAsHomeSpecialist(FixtureContext context) {
        TeamSeasonStats stats = context.getHomeTeamStats();
        String teamName = context.getHomeTeam().getName();

        // PPG disparity
        double homePpg = safeDouble(stats.getPpgHome());
        double awayPpg = safeDouble(stats.getPpgAway());
        double overallPpg = (homePpg + awayPpg) / 2.0;
        double ppgDiff = homePpg - awayPpg;
        double ppgDisparity = overallPpg > 0 ? (ppgDiff / overallPpg) * 100 : 0;

        // Win rate disparity
        double homeWinPct = calculateWinPercentage(stats, true);
        double awayWinPct = calculateWinPercentage(stats, false);
        double winDisparity = homeWinPct - awayWinPct;

        if (ppgDiff <= 0 || winDisparity <= 0) {
            return Optional.empty();
        }

        // Goals scored disparity (venue-correct denominators)
        double homeGoalsAvg = calculateVenueGoalsAvg(stats, true);
        double awayGoalsAvg = calculateVenueGoalsAvg(stats, false);
        double overallGoals = (homeGoalsAvg + awayGoalsAvg) / 2.0;
        double goalsDisparity = overallGoals > 0 ? ((homeGoalsAvg - awayGoalsAvg) / overallGoals) * 100 : 0;

        // Goals conceded disparity (lower at home = positive)
        double homeConcededAvg = calculateVenueConcededAvg(stats, true);
        double awayConcededAvg = calculateVenueConcededAvg(stats, false);
        double overallConceded = (homeConcededAvg + awayConcededAvg) / 2.0;
        double concededDisparity = overallConceded > 0 ? ((awayConcededAvg - homeConcededAvg) / overallConceded) * 100 : 0;

        // xG disparity (if available)
        double xgDisparity = 0.0;
        boolean hasXgData = hasXgData(stats);
        if (hasXgData) {
            double homeXg = safeDouble(stats.getXgForAvgHome());
            double awayXg = safeDouble(stats.getXgForAvgAway());
            double overallXg = (homeXg + awayXg) / 2.0;
            xgDisparity = overallXg > 0 ? ((homeXg - awayXg) / overallXg) * 100 : 0;
        }

        // Calculate overall disparity score
        double overallDisparity;
        if (hasXgData) {
            overallDisparity = (ppgDisparity + winDisparity + goalsDisparity + concededDisparity + xgDisparity) / 5.0;
        } else {
            overallDisparity = (ppgDisparity + winDisparity + goalsDisparity + concededDisparity) / 4.0;
        }

        // Check if qualifies as home specialist (any moderate signal + positive home edge)
        boolean isModerateHomeSpecialist = ppgDiff >= THRESHOLD_MODERATE_HOME_PPG_DIFF
                || winDisparity >= THRESHOLD_MODERATE_HOME_WIN_DIFF
                || goalsDisparity >= THRESHOLD_MODERATE_GOALS_DIFF_PCT;

        if (!isModerateHomeSpecialist || overallDisparity < THRESHOLD_MODERATE_DISPARITY) {
            return Optional.empty();
        }

        // Check form context
        FormContext formContext = analyzeFormContext(context.getHomeTeamForm(), stats, true);

        ConfidenceLevel confidence = resolveConfidence(overallDisparity, hasXgData, formContext);
        if (confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        String classification = confidence == ConfidenceLevel.STRONG
                ? "Strong Home Specialist"
                : "Moderate Home Specialist";

        return Optional.of(new SpecialistCandidate(
                teamName,
                true,
                classification,
                "Back Home Win",
                overallDisparity,
                confidence,
                homePpg,
                awayPpg,
                homeWinPct,
                awayWinPct,
                ppgDisparity,
                winDisparity,
                goalsDisparity,
                concededDisparity,
                xgDisparity,
                hasXgData,
                homeGoalsAvg,
                awayGoalsAvg,
                homeConcededAvg,
                awayConcededAvg,
                formContext
        ));
    }

    private Optional<SpecialistCandidate> analyzeAwayTeamAsPoorTraveler(FixtureContext context) {
        TeamSeasonStats stats = context.getAwayTeamStats();
        String teamName = context.getAwayTeam().getName();

        double awayPpg = safeDouble(stats.getPpgAway());
        double awayWinPct = calculateWinPercentage(stats, false);
        double awayGoalsAvg = calculateVenueGoalsAvg(stats, false);

        // Check poor traveler criteria
        boolean isPoorTraveler = awayPpg < THRESHOLD_POOR_TRAVELER_PPG 
                && awayWinPct < THRESHOLD_POOR_TRAVELER_WIN_PCT;

        boolean isStrongPoorTraveler = awayPpg < THRESHOLD_POOR_TRAVELER_PPG 
                && awayWinPct < THRESHOLD_STRONG_POOR_TRAVELER_WIN_PCT 
                && awayGoalsAvg < THRESHOLD_POOR_TRAVELER_GOALS;

        if (!isPoorTraveler) {
            return Optional.empty();
        }

        double homePpg = safeDouble(stats.getPpgHome());
        double homeWinPct = calculateWinPercentage(stats, true);
        double homeGoalsAvg = calculateVenueGoalsAvg(stats, true);
        double homeConcededAvg = calculateVenueConcededAvg(stats, true);
        double awayConcededAvg = calculateVenueConcededAvg(stats, false);

        // Calculate disparities
        double overallPpg = (homePpg + awayPpg) / 2.0;
        double ppgDisparity = overallPpg > 0 ? ((homePpg - awayPpg) / overallPpg) * 100 : 0;
        double winDisparity = homeWinPct - awayWinPct;
        double overallGoals = (homeGoalsAvg + awayGoalsAvg) / 2.0;
        double goalsDisparity = overallGoals > 0 ? ((homeGoalsAvg - awayGoalsAvg) / overallGoals) * 100 : 0;
        double overallConceded = (homeConcededAvg + awayConcededAvg) / 2.0;
        double concededDisparity = overallConceded > 0 ? ((awayConcededAvg - homeConcededAvg) / overallConceded) * 100 : 0;

        // xG disparity
        double xgDisparity = 0.0;
        boolean hasXgData = hasXgData(stats);
        if (hasXgData) {
            double homeXg = safeDouble(stats.getXgForAvgHome());
            double awayXg = safeDouble(stats.getXgForAvgAway());
            double overallXg = (homeXg + awayXg) / 2.0;
            xgDisparity = overallXg > 0 ? ((homeXg - awayXg) / overallXg) * 100 : 0;
        }

        // Poor traveler score - higher is worse for away team (good for backing home)
        double overallDisparity = (1.0 - (awayPpg / 3.0)) * 100 + 
                (THRESHOLD_POOR_TRAVELER_WIN_PCT - awayWinPct) + 
                (THRESHOLD_POOR_TRAVELER_GOALS - awayGoalsAvg) * 20;
        overallDisparity = Math.min(100, Math.max(0, overallDisparity));

        if (overallDisparity < THRESHOLD_MODERATE_DISPARITY) {
            return Optional.empty();
        }

        FormContext formContext = analyzeFormContext(context.getAwayTeamForm(), stats, false);

        ConfidenceLevel confidence = resolveConfidence(overallDisparity, hasXgData, formContext);
        if (confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        String classification = isStrongPoorTraveler && confidence == ConfidenceLevel.STRONG
                ? "Strong Poor Traveler"
                : "Moderate Poor Traveler";

        return Optional.of(new SpecialistCandidate(
                teamName,
                false,
                classification,
                "Back Home Win / Fade Away",
                overallDisparity,
                confidence,
                homePpg,
                awayPpg,
                homeWinPct,
                awayWinPct,
                ppgDisparity,
                winDisparity,
                goalsDisparity,
                concededDisparity,
                xgDisparity,
                hasXgData,
                homeGoalsAvg,
                awayGoalsAvg,
                homeConcededAvg,
                awayConcededAvg,
                formContext
        ));
    }

    private Optional<SpecialistCandidate> analyzeHomeTeamAsFortress(FixtureContext context) {
        TeamSeasonStats stats = context.getHomeTeamStats();
        String teamName = context.getHomeTeam().getName();

        double homeWinPct = calculateWinPercentage(stats, true);
        double homeLossPct = calculateLossPercentage(stats, true);
        double homeConcededAvg = calculateConcededAvg(stats, true);

        // Fortress criteria
        if (homeWinPct < THRESHOLD_FORTRESS_WIN_PCT || homeLossPct > THRESHOLD_FORTRESS_LOSS_PCT) {
            return Optional.empty();
        }

        if (homeConcededAvg > THRESHOLD_FORTRESS_CONCEDED) {
            return Optional.empty();
        }

        double homePpg = safeDouble(stats.getPpgHome());
        double awayPpg = safeDouble(stats.getPpgAway());
        double awayWinPct = calculateWinPercentage(stats, false);
        double homeGoalsAvg = calculateVenueGoalsAvg(stats, true);
        double awayGoalsAvg = calculateVenueGoalsAvg(stats, false);
        double awayConcededAvg = calculateVenueConcededAvg(stats, false);

        // Calculate disparities
        double overallPpg = (homePpg + awayPpg) / 2.0;
        double ppgDisparity = overallPpg > 0 ? ((homePpg - awayPpg) / overallPpg) * 100 : 0;
        double winDisparity = homeWinPct - awayWinPct;
        double overallGoals = (homeGoalsAvg + awayGoalsAvg) / 2.0;
        double goalsDisparity = overallGoals > 0 ? ((homeGoalsAvg - awayGoalsAvg) / overallGoals) * 100 : 0;
        double overallConceded = (homeConcededAvg + awayConcededAvg) / 2.0;
        double concededDisparity = overallConceded > 0 ? ((awayConcededAvg - homeConcededAvg) / overallConceded) * 100 : 0;

        // xG disparity
        double xgDisparity = 0.0;
        boolean hasXgData = hasXgData(stats);
        if (hasXgData) {
            double homeXg = safeDouble(stats.getXgForAvgHome());
            double awayXg = safeDouble(stats.getXgForAvgAway());
            double overallXg = (homeXg + awayXg) / 2.0;
            xgDisparity = overallXg > 0 ? ((homeXg - awayXg) / overallXg) * 100 : 0;
        }

        // Fortress score
        double overallDisparity = homeWinPct + (100 - homeLossPct * 2) + ((1 - homeConcededAvg) * 50);
        overallDisparity = Math.min(100, overallDisparity / 2.5);

        if (overallDisparity < THRESHOLD_MODERATE_DISPARITY) {
            return Optional.empty();
        }

        FormContext formContext = analyzeFormContext(context.getHomeTeamForm(), stats, true);

        ConfidenceLevel confidence = resolveConfidence(overallDisparity, hasXgData, formContext);
        if (confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        return Optional.of(new SpecialistCandidate(
                teamName,
                true,
                "Home Fortress",
                "Strong Back Home Win",
                overallDisparity,
                confidence,
                homePpg,
                awayPpg,
                homeWinPct,
                awayWinPct,
                ppgDisparity,
                winDisparity,
                goalsDisparity,
                concededDisparity,
                xgDisparity,
                hasXgData,
                homeGoalsAvg,
                awayGoalsAvg,
                homeConcededAvg,
                awayConcededAvg,
                formContext
        ));
    }

private double calculateWinPercentage(TeamSeasonStats stats, boolean isHome) {
        int matchesAtVenue = calculateMatchesAtVenue(stats, isHome);
        if (matchesAtVenue == 0) {
            return 33.3;
        }
        int wins = isHome ? safeInt(stats.getSeasonWinsHome()) : safeInt(stats.getSeasonWinsAway());
        return (wins * 100.0) / matchesAtVenue;
    }

    private double calculateLossPercentage(TeamSeasonStats stats, boolean isHome) {
        int matchesAtVenue = calculateMatchesAtVenue(stats, isHome);
        if (matchesAtVenue == 0) {
            return 33.3;
        }
        int losses = isHome ? safeInt(stats.getSeasonLossesHome()) : safeInt(stats.getSeasonLossesAway());
        return (losses * 100.0) / matchesAtVenue;
    }

    private double calculateConcededAvg(TeamSeasonStats stats, boolean isHome) {
        int matchesAtVenue = calculateMatchesAtVenue(stats, isHome);
        if (matchesAtVenue == 0) {
            return 1.0;
        }
        int conceded = isHome ? safeInt(stats.getSeasonConcededHome()) : safeInt(stats.getSeasonConcededAway());
        return conceded / (double) matchesAtVenue;
    }

    private int calculateMatchesAtVenue(TeamSeasonStats stats, boolean isHome) {
        if (isHome) {
            return safeInt(stats.getSeasonWinsHome()) 
                    + safeInt(stats.getSeasonDrawsHome()) 
                    + safeInt(stats.getSeasonLossesHome());
        } else {
            return safeInt(stats.getSeasonWinsAway()) 
                    + safeInt(stats.getSeasonDrawsAway()) 
                    + safeInt(stats.getSeasonLossesAway());
        }
    }

    private boolean hasXgData(TeamSeasonStats stats) {
        return stats.getXgForAvgHome() != null && stats.getXgForAvgAway() != null;
    }

    private FormContext analyzeFormContext(TeamRecentForm form, TeamSeasonStats seasonStats, boolean isHome) {
        if (form == null) {
            return new FormContext(false, 0, 0, false, null);
        }

        // Compare recent form to season average
        double seasonPpg = isHome ? safeDouble(seasonStats.getPpgHome()) : safeDouble(seasonStats.getPpgAway());
        double formPpg = isHome ? safeDouble(form.getPpgHome()) : safeDouble(form.getPpgAway());
        double ppgDivergence = formPpg - seasonPpg;

        double seasonGoals = calculateVenueGoalsAvg(seasonStats, isHome, 1.0);
        double formGoals = isHome ? safeDouble(form.getScoredAvgHome()) : safeDouble(form.getScoredAvgAway());
        double goalsDivergence = formGoals - seasonGoals;

        // Check if form diverges significantly from season pattern
        boolean formDiverges = Math.abs(ppgDivergence) > THRESHOLD_FORM_DIVERGENCE;
        String formStatus = null;
        if (formDiverges) {
            formStatus = ppgDivergence > 0 ? "Improving" : "Declining";
        }

        return new FormContext(true, ppgDivergence, goalsDivergence, formDiverges, formStatus);
    }

    private ConfidenceLevel resolveConfidence(double overallDisparity, boolean hasXgData, FormContext formContext) {
        if (overallDisparity < THRESHOLD_MODERATE_DISPARITY) {
            return ConfidenceLevel.WEAK;
        }
        boolean decliningForm = formContext.hasFormData()
                && formContext.formDiverges()
                && formContext.ppgDivergence() < 0;
        if (overallDisparity >= THRESHOLD_STRONG_DISPARITY && hasXgData && !decliningForm) {
            return ConfidenceLevel.STRONG;
        }
        return ConfidenceLevel.MODERATE;
    }

    private Map<String, Object> buildFactors(FixtureContext context, SpecialistCandidate best,
            List<SpecialistCandidate> all) {
        Map<String, Object> factors = new HashMap<>();
        
        // Basic info
        factors.put("team", best.teamName);
        factors.put("isHomeTeam", best.isHomeTeam);
        factors.put("classification", best.classification);
        factors.put("recommendation", best.recommendation);
        factors.put("overallDisparityScore", best.overallDisparityScore);
        
        // PPG data
        factors.put("homePpg", best.homePpg);
        factors.put("awayPpg", best.awayPpg);
        factors.put("ppgDisparity", best.ppgDisparity);

        // Win percentage data
        factors.put("homeWinPct", best.homeWinPct);
        factors.put("awayWinPct", best.awayWinPct);
        factors.put("winDisparity", best.winDisparity);

        // Goals data
        factors.put("homeGoalsAvg", best.homeGoalsAvg);
        factors.put("awayGoalsAvg", best.awayGoalsAvg);
        factors.put("goalsDisparity", best.goalsDisparity);
        
        factors.put("homeConcededAvg", best.homeConcededAvg);
        factors.put("awayConcededAvg", best.awayConcededAvg);
        factors.put("concededDisparity", best.concededDisparity);

        // xG data
        factors.put("xgDataAvailable", best.hasXgData);
        if (best.hasXgData) {
            factors.put("xgDisparity", best.xgDisparity);
            TeamSeasonStats stats = best.isHomeTeam ? context.getHomeTeamStats() : context.getAwayTeamStats();
            factors.put("homeXgAvg", safeDouble(stats.getXgForAvgHome()));
            factors.put("awayXgAvg", safeDouble(stats.getXgForAvgAway()));
        }

        // Form context
        factors.put("formDataAvailable", best.formContext.hasFormData);
        if (best.formContext.hasFormData) {
            factors.put("formPpgDivergence", best.formContext.ppgDivergence);
            factors.put("formGoalsDivergence", best.formContext.goalsDivergence);
            factors.put("formDivergesFromSeason", best.formContext.formDiverges);
            if (best.formContext.formStatus != null) {
                factors.put("formStatus", best.formContext.formStatus);
            }
        }

        // All candidates found
        factors.put("candidatesFound", all.size());
        List<Map<String, Object>> allCandidates = new ArrayList<>();
        for (SpecialistCandidate c : all) {
            Map<String, Object> candidateMap = new HashMap<>();
            candidateMap.put("team", c.teamName);
            candidateMap.put("classification", c.classification);
            candidateMap.put("score", c.overallDisparityScore);
            candidateMap.put("confidence", c.confidence.name());
            allCandidates.add(candidateMap);
        }
        factors.put("allCandidates", allCandidates);

        // Positive indicators and risk flags
        List<String> positiveIndicators = new ArrayList<>();
        List<String> riskFlags = new ArrayList<>();

        if (best.overallDisparityScore >= THRESHOLD_STRONG_DISPARITY) {
            positiveIndicators.add("Strong disparity score");
        }
        if (best.classification.contains("Fortress")) {
            positiveIndicators.add("Home fortress - very difficult to beat at home");
        }
        if (best.classification.contains("Poor Traveler")) {
            positiveIndicators.add("Opponent struggles away from home");
        }
        if (best.hasXgData && Math.abs(best.xgDisparity) > 20) {
            positiveIndicators.add("xG data confirms home/away disparity");
        }

        if (best.formContext.formDiverges && best.formContext.ppgDivergence < 0) {
            riskFlags.add("Recent form declining from season pattern");
        }
        if (!best.hasXgData) {
            riskFlags.add("No xG data available for validation");
        }

        factors.put("positiveIndicators", positiveIndicators);
        factors.put("riskFlags", riskFlags);
        factors.put("awaySpecialistPaused", true);
        factors.put("homeOnlyPicks", true);

        return factors;
    }

    private String buildDescription(FixtureContext context, SpecialistCandidate candidate) {
        StringBuilder colour = new StringBuilder();

        if (candidate.isHomeTeam || candidate.classification.contains("Poor Traveler")) {
            colour.append(String.format("Home PPG: %.2f, Away PPG: %.2f", candidate.homePpg, candidate.awayPpg));
        } else {
            colour.append(String.format("Away PPG: %.2f (consistent away performer)", candidate.awayPpg));
        }

        if (candidate.formContext.hasFormData && candidate.formContext.formDiverges) {
            colour.append(". Form: ").append(candidate.formContext.formStatus);
        }

        colour.append(". Recommendation: ").append(candidate.recommendation);

        return MatchBriefCopy.narrate(MatchBriefCopy.Brief.builder()
                .confidence(candidate.confidence)
                .selection(candidate.classification + ": " + candidate.teamName)
                .context(context)
                .probabilityPct(candidate.overallDisparityScore)
                .colourNote(colour.toString())
                .build());
    }

    private record SpecialistCandidate(
            String teamName,
            boolean isHomeTeam,
            String classification,
            String recommendation,
            double overallDisparityScore,
            ConfidenceLevel confidence,
            double homePpg,
            double awayPpg,
            double homeWinPct,
            double awayWinPct,
            double ppgDisparity,
            double winDisparity,
            double goalsDisparity,
            double concededDisparity,
            double xgDisparity,
            boolean hasXgData,
            double homeGoalsAvg,
            double awayGoalsAvg,
            double homeConcededAvg,
            double awayConcededAvg,
            FormContext formContext
    ) {}

    private record FormContext(
            boolean hasFormData,
            double ppgDivergence,
            double goalsDivergence,
            boolean formDiverges,
            String formStatus
    ) {}
}
