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
public class HomeAwaySpecialistEngine implements RecommendationEngine {

    private static final double THRESHOLD_STRONG_HOME_SPECIALIST = 0.8;
    private static final double THRESHOLD_MODERATE_HOME_SPECIALIST = 0.5;
    private static final double THRESHOLD_POOR_TRAVELER_PPG = 0.8;
    private static final double THRESHOLD_POOR_TRAVELER_WIN_PCT = 20.0;
    private static final double THRESHOLD_FORTRESS_WIN_PCT = 70.0;

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

        analyzeHomeTeamAsHomeSpecialist(context).ifPresent(candidates::add);
        analyzeAwayTeamAsPoorTraveler(context).ifPresent(candidates::add);
        analyzeAwayTeamAsAwaySpecialist(context).ifPresent(candidates::add);
        analyzeHomeTeamAsFortress(context).ifPresent(candidates::add);

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        SpecialistCandidate best = candidates.stream()
                .max(Comparator.comparingDouble(SpecialistCandidate::disparityScore))
                .orElse(null);

        if (best == null || best.confidence == ConfidenceLevel.WEAK) {
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
                .type(RecommendationType.HOME_AWAY_SPECIALIST)
                .confidence(best.confidence)
                .score(best.disparityScore)
                .market(best.recommendation)
                .odds(null)
                .description(buildDescription(context, best))
                .factors(factors)
                .generatedAt(Instant.now())
                .build();

        log.info("Home/Away Specialist recommendation generated: fixtureId={}, classification={}, team={}, score={}, confidence={}", 
                context.getFixture().getId(), best.classification, best.teamName,
                String.format("%.1f", best.disparityScore), best.confidence);

        return Optional.of(recommendation);
    }

    private Optional<SpecialistCandidate> analyzeHomeTeamAsHomeSpecialist(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        String teamName = context.getHomeTeam().getName();

        double homePpg = safeDouble(homeStats.getPpgHome());
        double awayPpg = safeDouble(homeStats.getPpgAway());
        double ppgDiff = homePpg - awayPpg;

        if (ppgDiff < THRESHOLD_MODERATE_HOME_SPECIALIST) {
            return Optional.empty();
        }

        double homeWinPct = calculateWinPercentage(homeStats, true);
        double awayWinPct = calculateWinPercentage(homeStats, false);
        double winPctDiff = homeWinPct - awayWinPct;

        double disparity = (ppgDiff / Math.max(0.1, homePpg)) * 100;
        ConfidenceLevel confidence = ppgDiff >= THRESHOLD_STRONG_HOME_SPECIALIST 
                ? ConfidenceLevel.STRONG : ConfidenceLevel.MODERATE;

        return Optional.of(new SpecialistCandidate(
                teamName,
                true,
                "Strong Home Specialist",
                "Back Home Win",
                disparity,
                confidence,
                homePpg,
                awayPpg,
                homeWinPct,
                awayWinPct
        ));
    }

    private Optional<SpecialistCandidate> analyzeAwayTeamAsPoorTraveler(FixtureContext context) {
        TeamSeasonStats awayStats = context.getAwayTeamStats();
        String teamName = context.getAwayTeam().getName();

        double awayPpg = safeDouble(awayStats.getPpgAway());
        double awayWinPct = calculateWinPercentage(awayStats, false);

        if (awayPpg >= THRESHOLD_POOR_TRAVELER_PPG || awayWinPct >= THRESHOLD_POOR_TRAVELER_WIN_PCT) {
            return Optional.empty();
        }

        double disparity = (1.0 - (awayPpg / 3.0)) * 100;
        ConfidenceLevel confidence = awayWinPct < 15 ? ConfidenceLevel.STRONG : ConfidenceLevel.MODERATE;

        return Optional.of(new SpecialistCandidate(
                teamName,
                false,
                "Poor Traveler",
                "Back Home Win / Fade Away",
                disparity,
                confidence,
                safeDouble(awayStats.getPpgHome()),
                awayPpg,
                calculateWinPercentage(awayStats, true),
                awayWinPct
        ));
    }

    private Optional<SpecialistCandidate> analyzeAwayTeamAsAwaySpecialist(FixtureContext context) {
        TeamSeasonStats awayStats = context.getAwayTeamStats();
        String teamName = context.getAwayTeam().getName();

        double homePpg = safeDouble(awayStats.getPpgHome());
        double awayPpg = safeDouble(awayStats.getPpgAway());

        if (awayPpg <= homePpg - 0.2) {
            return Optional.empty();
        }

        double awayWinPct = calculateWinPercentage(awayStats, false);
        double homeWinPct = calculateWinPercentage(awayStats, true);

        if (awayWinPct <= homeWinPct - 5) {
            return Optional.empty();
        }

        double disparity = ((awayPpg - homePpg) / Math.max(0.1, homePpg)) * 100 + 50;
        ConfidenceLevel confidence = awayPpg > homePpg + 0.3 ? ConfidenceLevel.STRONG : ConfidenceLevel.MODERATE;

        return Optional.of(new SpecialistCandidate(
                teamName,
                false,
                "Strong Away Specialist",
                "Back Away Win",
                disparity,
                confidence,
                homePpg,
                awayPpg,
                homeWinPct,
                awayWinPct
        ));
    }

    private Optional<SpecialistCandidate> analyzeHomeTeamAsFortress(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        String teamName = context.getHomeTeam().getName();

        double homeWinPct = calculateWinPercentage(homeStats, true);
        double homeLossPct = calculateLossPercentage(homeStats, true);

        if (homeWinPct < THRESHOLD_FORTRESS_WIN_PCT || homeLossPct > 15) {
            return Optional.empty();
        }

        double concededAvg = calculateConcededAvg(homeStats, true);
        if (concededAvg > 0.8) {
            return Optional.empty();
        }

        double disparity = homeWinPct + (100 - homeLossPct * 2) + ((1 - concededAvg) * 50);
        disparity = disparity / 2.5;

        return Optional.of(new SpecialistCandidate(
                teamName,
                true,
                "Home Fortress",
                "Back Home Win",
                disparity,
                ConfidenceLevel.STRONG,
                safeDouble(homeStats.getPpgHome()),
                safeDouble(homeStats.getPpgAway()),
                homeWinPct,
                calculateWinPercentage(homeStats, false)
        ));
    }

    private double calculateWinPercentage(TeamSeasonStats stats, boolean isHome) {
        if (stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 33.3;
        }
        int wins = isHome ? safeInt(stats.getSeasonWinsHome()) : safeInt(stats.getSeasonWinsAway());
        return (wins * 100.0) / stats.getMatchesPlayed();
    }

    private double calculateLossPercentage(TeamSeasonStats stats, boolean isHome) {
        if (stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 33.3;
        }
        int losses = isHome ? safeInt(stats.getSeasonLossesHome()) : safeInt(stats.getSeasonLossesAway());
        return (losses * 100.0) / stats.getMatchesPlayed();
    }

    private double calculateConcededAvg(TeamSeasonStats stats, boolean isHome) {
        if (stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 1.0;
        }
        int conceded = isHome ? safeInt(stats.getSeasonConcededHome()) : safeInt(stats.getSeasonConcededAway());
        return conceded / (double) stats.getMatchesPlayed();
    }

    private Map<String, Object> buildFactors(SpecialistCandidate best, List<SpecialistCandidate> all) {
        Map<String, Object> factors = new HashMap<>();
        
        factors.put("team", best.teamName);
        factors.put("isHomeTeam", best.isHomeTeam);
        factors.put("classification", best.classification);
        factors.put("disparityScore", best.disparityScore);
        factors.put("homePpg", best.homePpg);
        factors.put("awayPpg", best.awayPpg);
        factors.put("homeWinPct", best.homeWinPct);
        factors.put("awayWinPct", best.awayWinPct);
        factors.put("candidatesFound", all.size());

        return factors;
    }

    private String buildDescription(FixtureContext context, SpecialistCandidate candidate) {
        return String.format("%s confidence %s: %s (%.1f%% disparity) - Recommendation: %s - %s vs %s",
                candidate.confidence.getDisplayName(),
                candidate.classification,
                candidate.teamName,
                candidate.disparityScore,
                candidate.recommendation,
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private double safeDouble(Double value) {
        return value != null ? value : 0.0;
    }

    private record SpecialistCandidate(
            String teamName,
            boolean isHomeTeam,
            String classification,
            String recommendation,
            double disparityScore,
            ConfidenceLevel confidence,
            double homePpg,
            double awayPpg,
            double homeWinPct,
            double awayWinPct
    ) {}
}
