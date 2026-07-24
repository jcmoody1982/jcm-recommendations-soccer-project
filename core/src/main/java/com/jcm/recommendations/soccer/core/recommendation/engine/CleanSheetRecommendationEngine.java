package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
@Slf4j
public class CleanSheetRecommendationEngine implements RecommendationEngine {

    private static final double WEIGHT_TEAM_CS_SEASON = 0.20;
    private static final double WEIGHT_TEAM_CS_FORM = 0.20;
    private static final double WEIGHT_TEAM_CONCEDED_SEASON = 0.15;
    private static final double WEIGHT_TEAM_CONCEDED_FORM = 0.15;
    private static final double WEIGHT_OPPONENT_FTS_SEASON = 0.15;
    private static final double WEIGHT_OPPONENT_FTS_FORM = 0.15;

    private static final double THRESHOLD_STRONG = 70.0;
    private static final double THRESHOLD_MODERATE = 50.0;

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
                .orElse(null);

        if (best == null) {
            return Optional.empty();
        }

        ConfidenceLevel confidence = determineConfidence(best.score);
        if (confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        Map<String, Object> factors = buildFactors(best, candidates);

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
                .type(RecommendationType.CLEAN_SHEET)
                .confidence(confidence)
                .score(best.score)
                .market(best.teamName + " Clean Sheet")
                .odds(null)
                .description(buildDescription(context, best, confidence))
                .factors(factors)
                .generatedAt(Instant.now())
                .build();

        log.info("Clean Sheet recommendation generated: fixtureId={}, team={}, score={}, confidence={}", 
                context.getFixture().getId(), best.teamName, String.format("%.1f", best.score), confidence);

        return Optional.of(recommendation);
    }

    private Optional<CleanSheetCandidate> analyzeTeamCleanSheet(FixtureContext context, boolean isHomeTeam) {
        TeamSeasonStats teamStats = isHomeTeam ? context.getHomeTeamStats() : context.getAwayTeamStats();
        TeamSeasonStats opponentStats = isHomeTeam ? context.getAwayTeamStats() : context.getHomeTeamStats();
        String teamName = isHomeTeam ? context.getHomeTeam().getName() : context.getAwayTeam().getName();

        if (teamStats == null || opponentStats == null) {
            return Optional.empty();
        }

        double teamCsSeason = calculateCleanSheetPct(teamStats, isHomeTeam);
        double teamCsForm = teamCsSeason;
        if (context.hasRecentForm()) {
            var form = isHomeTeam ? context.getHomeTeamForm() : context.getAwayTeamForm();
            if (form != null) {
                int formCs = isHomeTeam 
                        ? safeInt(form.getCleanSheetsHome()) 
                        : safeInt(form.getCleanSheetsAway());
                teamCsForm = formCs * 20.0;
            }
        }

        double teamConcededInverse = 100.0 - calculateConcededRating(teamStats, isHomeTeam);
        double teamConcededFormInverse = teamConcededInverse;
        if (context.hasRecentForm()) {
            var form = isHomeTeam ? context.getHomeTeamForm() : context.getAwayTeamForm();
            if (form != null) {
                Double concededAvg = isHomeTeam ? form.getConcededAvgHome() : form.getConcededAvgAway();
                teamConcededFormInverse = 100.0 - (safeDouble(concededAvg) * 40.0);
            }
        }

        double opponentFtsSeason = calculateFailedToScorePct(opponentStats, !isHomeTeam);
        double opponentFtsForm = opponentFtsSeason;
        if (context.hasRecentForm()) {
            var form = isHomeTeam ? context.getAwayTeamForm() : context.getHomeTeamForm();
            if (form != null) {
                int formFts = !isHomeTeam 
                        ? safeInt(form.getFailedToScoreHome()) 
                        : safeInt(form.getFailedToScoreAway());
                opponentFtsForm = formFts * 20.0;
            }
        }

        double score = (teamCsSeason * WEIGHT_TEAM_CS_SEASON)
                + (teamCsForm * WEIGHT_TEAM_CS_FORM)
                + (teamConcededInverse * WEIGHT_TEAM_CONCEDED_SEASON)
                + (teamConcededFormInverse * WEIGHT_TEAM_CONCEDED_FORM)
                + (opponentFtsSeason * WEIGHT_OPPONENT_FTS_SEASON)
                + (opponentFtsForm * WEIGHT_OPPONENT_FTS_FORM);

        score = applyDefensiveStrengthBonus(score, teamStats, isHomeTeam);

        if (score < THRESHOLD_MODERATE) {
            return Optional.empty();
        }

        return Optional.of(new CleanSheetCandidate(
                teamName,
                isHomeTeam,
                score,
                teamCsSeason,
                teamCsForm,
                opponentFtsSeason
        ));
    }

    private double applyDefensiveStrengthBonus(double score, TeamSeasonStats stats, boolean isHome) {
        double concededAvg = calculateConcededAvg(stats, isHome);
        
        if (concededAvg < 0.75) {
            return score * 1.2;
        } else if (concededAvg < 1.0) {
            return score * 1.1;
        } else if (concededAvg > 1.25) {
            return score * 0.85;
        }
        
        return score;
    }

    private double calculateCleanSheetPct(TeamSeasonStats stats, boolean isHome) {
        if (stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 30.0;
        }
        int cs = isHome ? safeInt(stats.getSeasonCleanSheetsHome()) : safeInt(stats.getSeasonCleanSheetsAway());
        return (cs * 100.0) / stats.getMatchesPlayed();
    }

    private double calculateFailedToScorePct(TeamSeasonStats stats, boolean isHome) {
        if (stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 20.0;
        }
        int fts = isHome ? safeInt(stats.getSeasonFailedToScoreHome()) : safeInt(stats.getSeasonFailedToScoreAway());
        return (fts * 100.0) / stats.getMatchesPlayed();
    }

    private double calculateConcededAvg(TeamSeasonStats stats, boolean isHome) {
        if (stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 1.0;
        }
        int conceded = isHome ? safeInt(stats.getSeasonConcededHome()) : safeInt(stats.getSeasonConcededAway());
        return conceded / (double) stats.getMatchesPlayed();
    }

    private double calculateConcededRating(TeamSeasonStats stats, boolean isHome) {
        double avg = calculateConcededAvg(stats, isHome);
        return Math.min(100.0, avg * 40.0);
    }

    private ConfidenceLevel determineConfidence(double score) {
        if (score >= THRESHOLD_STRONG) {
            return ConfidenceLevel.STRONG;
        } else if (score >= THRESHOLD_MODERATE) {
            return ConfidenceLevel.MODERATE;
        }
        return ConfidenceLevel.WEAK;
    }

    private Map<String, Object> buildFactors(CleanSheetCandidate best, List<CleanSheetCandidate> all) {
        Map<String, Object> factors = new HashMap<>();
        
        factors.put("team", best.teamName);
        factors.put("isHomeTeam", best.isHomeTeam);
        factors.put("cleanSheetScore", best.score);
        factors.put("teamCleanSheetSeasonPct", best.teamCsSeason);
        factors.put("teamCleanSheetFormPct", best.teamCsForm);
        factors.put("opponentFailedToScorePct", best.opponentFtsSeason);
        factors.put("candidatesAnalyzed", all.size());

        return factors;
    }

    private String buildDescription(FixtureContext context, CleanSheetCandidate candidate, ConfidenceLevel confidence) {
        return String.format("%s confidence Clean Sheet for %s (%.1f%% probability) - %s vs %s",
                confidence.getDisplayName(),
                candidate.teamName,
                candidate.score,
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private double safeDouble(Double value) {
        return value != null ? value : 0.0;
    }

    private record CleanSheetCandidate(
            String teamName,
            boolean isHomeTeam,
            double score,
            double teamCsSeason,
            double teamCsForm,
            double opponentFtsSeason
    ) {}
}
