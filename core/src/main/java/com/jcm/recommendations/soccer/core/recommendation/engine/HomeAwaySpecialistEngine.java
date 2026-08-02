package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.core.recommendation.util.RecommendationFactory;
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

    // Away specialist thresholds (rare)
    private static final double THRESHOLD_STRONG_AWAY_PPG_DIFF = 0.3;
    private static final double THRESHOLD_AWAY_WIN_DIFF = 10.0;
    private static final double THRESHOLD_CONSISTENT_AWAY_PPG = 1.5;

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

        // Analyze away team
        analyzeAwayTeamAsPoorTraveler(context).ifPresent(candidates::add);
        analyzeAwayTeamAsAwaySpecialist(context).ifPresent(candidates::add);

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

        // Determine team to back
        String teamToBack;
        if (best.isHomeTeam || best.classification.contains("Poor Traveler")) {
            teamToBack = context.getHomeTeam().getName();
        } else {
            teamToBack = context.getAwayTeam().getName();
        }

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

        // Goals scored disparity
        double homeGoalsAvg = calculateGoalsAvg(stats.getSeasonGoalsHome(), stats.getMatchesPlayed(), 1.0);
        double awayGoalsAvg = calculateGoalsAvg(stats.getSeasonGoalsAway(), stats.getMatchesPlayed(), 1.0);
        double overallGoals = (homeGoalsAvg + awayGoalsAvg) / 2.0;
        double goalsDisparity = overallGoals > 0 ? ((homeGoalsAvg - awayGoalsAvg) / overallGoals) * 100 : 0;

        // Goals conceded disparity (lower at home = positive)
        double homeConcededAvg = calculateGoalsAvg(stats.getSeasonConcededHome(), stats.getMatchesPlayed(), 1.0);
        double awayConcededAvg = calculateGoalsAvg(stats.getSeasonConcededAway(), stats.getMatchesPlayed(), 1.0);
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

        // Check if qualifies as home specialist
        boolean isStrongHomeSpecialist = ppgDiff >= THRESHOLD_STRONG_HOME_PPG_DIFF 
                || winDisparity >= THRESHOLD_STRONG_HOME_WIN_DIFF 
                || goalsDisparity >= THRESHOLD_STRONG_GOALS_DIFF_PCT;

        boolean isModerateHomeSpecialist = ppgDiff >= THRESHOLD_MODERATE_HOME_PPG_DIFF 
                || winDisparity >= THRESHOLD_MODERATE_HOME_WIN_DIFF 
                || goalsDisparity >= THRESHOLD_MODERATE_GOALS_DIFF_PCT;

        if (!isModerateHomeSpecialist && overallDisparity < THRESHOLD_MODERATE_DISPARITY) {
            return Optional.empty();
        }

        // Check form context
        FormContext formContext = analyzeFormContext(context.getHomeTeamForm(), stats, true);

        ConfidenceLevel confidence;
        String classification;
        if (isStrongHomeSpecialist || overallDisparity >= THRESHOLD_STRONG_DISPARITY) {
            confidence = ConfidenceLevel.STRONG;
            classification = "Strong Home Specialist";
        } else {
            confidence = ConfidenceLevel.MODERATE;
            classification = "Moderate Home Specialist";
        }

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
        double awayGoalsAvg = calculateGoalsAvg(stats.getSeasonGoalsAway(), stats.getMatchesPlayed(), 1.0);

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
        double homeGoalsAvg = calculateGoalsAvg(stats.getSeasonGoalsHome(), stats.getMatchesPlayed(), 1.0);
        double homeConcededAvg = calculateGoalsAvg(stats.getSeasonConcededHome(), stats.getMatchesPlayed(), 1.0);
        double awayConcededAvg = calculateGoalsAvg(stats.getSeasonConcededAway(), stats.getMatchesPlayed(), 1.0);

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

        ConfidenceLevel confidence = isStrongPoorTraveler ? ConfidenceLevel.STRONG : ConfidenceLevel.MODERATE;
        String classification = isStrongPoorTraveler ? "Strong Poor Traveler" : "Moderate Poor Traveler";

        FormContext formContext = analyzeFormContext(context.getAwayTeamForm(), stats, false);

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

    private Optional<SpecialistCandidate> analyzeAwayTeamAsAwaySpecialist(FixtureContext context) {
        TeamSeasonStats stats = context.getAwayTeamStats();
        String teamName = context.getAwayTeam().getName();

        double homePpg = safeDouble(stats.getPpgHome());
        double awayPpg = safeDouble(stats.getPpgAway());
        double awayWinPct = calculateWinPercentage(stats, false);
        double homeWinPct = calculateWinPercentage(stats, true);

        // Away specialist is rare - away PPG close to or better than home
        boolean isStrongAwaySpecialist = awayPpg > homePpg + THRESHOLD_STRONG_AWAY_PPG_DIFF 
                && awayWinPct > homeWinPct + THRESHOLD_AWAY_WIN_DIFF;

        boolean isModerateAwaySpecialist = awayPpg >= homePpg - 0.2 
                && awayWinPct >= homeWinPct - 5 
                && awayPpg >= THRESHOLD_CONSISTENT_AWAY_PPG;

        if (!isModerateAwaySpecialist) {
            return Optional.empty();
        }

        double homeGoalsAvg = calculateGoalsAvg(stats.getSeasonGoalsHome(), stats.getMatchesPlayed(), 1.0);
        double awayGoalsAvg = calculateGoalsAvg(stats.getSeasonGoalsAway(), stats.getMatchesPlayed(), 1.0);
        double homeConcededAvg = calculateGoalsAvg(stats.getSeasonConcededHome(), stats.getMatchesPlayed(), 1.0);
        double awayConcededAvg = calculateGoalsAvg(stats.getSeasonConcededAway(), stats.getMatchesPlayed(), 1.0);

        // Calculate disparities (inverted for away specialist - positive means better away)
        double overallPpg = (homePpg + awayPpg) / 2.0;
        double ppgDisparity = overallPpg > 0 ? ((awayPpg - homePpg) / overallPpg) * 100 : 0;
        double winDisparity = awayWinPct - homeWinPct;
        double overallGoals = (homeGoalsAvg + awayGoalsAvg) / 2.0;
        double goalsDisparity = overallGoals > 0 ? ((awayGoalsAvg - homeGoalsAvg) / overallGoals) * 100 : 0;
        double overallConceded = (homeConcededAvg + awayConcededAvg) / 2.0;
        double concededDisparity = overallConceded > 0 ? ((homeConcededAvg - awayConcededAvg) / overallConceded) * 100 : 0;

        // xG disparity
        double xgDisparity = 0.0;
        boolean hasXgData = hasXgData(stats);
        if (hasXgData) {
            double homeXg = safeDouble(stats.getXgForAvgHome());
            double awayXg = safeDouble(stats.getXgForAvgAway());
            double overallXg = (homeXg + awayXg) / 2.0;
            xgDisparity = overallXg > 0 ? ((awayXg - homeXg) / overallXg) * 100 : 0;
        }

        // Away specialist disparity (bonus for being rare)
        double overallDisparity;
        if (hasXgData) {
            overallDisparity = (ppgDisparity + winDisparity + goalsDisparity + concededDisparity + xgDisparity) / 5.0 + 20;
        } else {
            overallDisparity = (ppgDisparity + winDisparity + goalsDisparity + concededDisparity) / 4.0 + 20;
        }
        overallDisparity = Math.min(100, overallDisparity);

        ConfidenceLevel confidence = isStrongAwaySpecialist ? ConfidenceLevel.STRONG : ConfidenceLevel.MODERATE;
        String classification = isStrongAwaySpecialist ? "Strong Away Specialist" : "Consistent Away Performer";

        FormContext formContext = analyzeFormContext(context.getAwayTeamForm(), stats, false);

        return Optional.of(new SpecialistCandidate(
                teamName,
                false,
                classification,
                "Back Away Win",
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
        double homeGoalsAvg = calculateGoalsAvg(stats.getSeasonGoalsHome(), stats.getMatchesPlayed(), 1.0);
        double awayGoalsAvg = calculateGoalsAvg(stats.getSeasonGoalsAway(), stats.getMatchesPlayed(), 1.0);
        double awayConcededAvg = calculateGoalsAvg(stats.getSeasonConcededAway(), stats.getMatchesPlayed(), 1.0);

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

        FormContext formContext = analyzeFormContext(context.getHomeTeamForm(), stats, true);

        return Optional.of(new SpecialistCandidate(
                teamName,
                true,
                "Home Fortress",
                "Strong Back Home Win",
                overallDisparity,
                ConfidenceLevel.STRONG,
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

        double seasonGoals = isHome 
                ? calculateGoalsAvg(seasonStats.getSeasonGoalsHome(), seasonStats.getMatchesPlayed(), 1.0)
                : calculateGoalsAvg(seasonStats.getSeasonGoalsAway(), seasonStats.getMatchesPlayed(), 1.0);
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

        return factors;
    }

    private String buildDescription(FixtureContext context, SpecialistCandidate candidate) {
        StringBuilder sb = new StringBuilder();
        
        sb.append(candidate.confidence.getDisplayName()).append(" confidence ");
        sb.append(candidate.classification).append(": ");
        sb.append(candidate.teamName);
        sb.append(String.format(" (%.1f%% disparity)", candidate.overallDisparityScore));
        sb.append(". ");

        // Add key stats
        if (candidate.isHomeTeam || candidate.classification.contains("Poor Traveler")) {
            sb.append(String.format("Home PPG: %.2f, Away PPG: %.2f. ", candidate.homePpg, candidate.awayPpg));
        } else {
            sb.append(String.format("Away PPG: %.2f (consistent away performer). ", candidate.awayPpg));
        }

        // Add form context
        if (candidate.formContext.hasFormData && candidate.formContext.formDiverges) {
            sb.append("Form: ").append(candidate.formContext.formStatus).append(". ");
        }

        sb.append("Recommendation: ").append(candidate.recommendation).append(". ");
        sb.append(context.getHomeTeam().getName()).append(" vs ").append(context.getAwayTeam().getName());

        return sb.toString();
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
