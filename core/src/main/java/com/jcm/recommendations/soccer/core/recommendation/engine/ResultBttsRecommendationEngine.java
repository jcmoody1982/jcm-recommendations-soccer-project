package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class ResultBttsRecommendationEngine implements RecommendationEngine {

    private static final double THRESHOLD_STRONG = 35.0;
    private static final double THRESHOLD_MODERATE = 28.0;
    
    private static final double HOME_WIN_MIN = 50.0;
    private static final double AWAY_WIN_MIN = 45.0;
    private static final double DRAW_MIN = 25.0;
    private static final double BTTS_MIN = 55.0;

    @Override
    public RecommendationType getType() {
        return RecommendationType.RESULT_BTTS;
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        log.debug("Analyzing Result + BTTS for fixture: fixtureId={}, {} vs {}",
                context.getFixture().getId(),
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());

        double bttsProb = calculateBttsProb(context);
        if (bttsProb < BTTS_MIN) {
            return Optional.empty();
        }

        if (hasHighCleanSheetRate(context)) {
            return Optional.empty();
        }

        double homeWinProb = calculateWinProbability(context, true);
        double awayWinProb = calculateWinProbability(context, false);
        double drawProb = calculateDrawProbability(context);

        ResultBttsCandidate best = findBestCandidate(context, homeWinProb, drawProb, awayWinProb, bttsProb);

        if (best == null || best.combinedProb < THRESHOLD_MODERATE) {
            return Optional.empty();
        }

        ConfidenceLevel confidence = best.combinedProb >= THRESHOLD_STRONG 
                ? ConfidenceLevel.STRONG : ConfidenceLevel.MODERATE;

        Map<String, Object> factors = buildFactors(homeWinProb, drawProb, awayWinProb, bttsProb, best);

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
                .type(RecommendationType.RESULT_BTTS)
                .confidence(confidence)
                .score(best.combinedProb)
                .market(best.market)
                .odds(null)
                .description(buildDescription(context, best, confidence))
                .factors(factors)
                .generatedAt(Instant.now())
                .build();

        log.info("Result + BTTS recommendation: fixtureId={}, market={}, combined={}, confidence={}",
                context.getFixture().getId(), best.market, String.format("%.1f", best.combinedProb), confidence);

        return Optional.of(recommendation);
    }

    private double calculateBttsProb(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double homeBtts = safeDouble(homeStats.getSeasonBttsPercentageHome());
        double awayBtts = safeDouble(awayStats.getSeasonBttsPercentageAway());

        if (context.hasPotentials() && context.getPotentials().getBttsPotential() != null) {
            double apiPotential = context.getPotentials().getBttsPotential();
            return (homeBtts * 0.35) + (awayBtts * 0.35) + (apiPotential * 0.30);
        }

        return (homeBtts + awayBtts) / 2;
    }

    private double calculateWinProbability(FixtureContext context, boolean isHome) {
        TeamSeasonStats stats = isHome ? context.getHomeTeamStats() : context.getAwayTeamStats();
        
        if (stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 33.3;
        }

        int wins = isHome ? safeInt(stats.getSeasonWinsHome()) : safeInt(stats.getSeasonWinsAway());
        double winPct = (wins * 100.0) / stats.getMatchesPlayed();
        
        double ppg = isHome ? safeDouble(stats.getPpgHome()) : safeDouble(stats.getPpgAway());
        double ppgBonus = Math.min(15.0, ppg * 5);

        return winPct * 0.8 + ppgBonus;
    }

    private double calculateDrawProbability(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double homeDrawPct = calculateDrawPercentage(homeStats, true);
        double awayDrawPct = calculateDrawPercentage(awayStats, false);

        return (homeDrawPct + awayDrawPct) / 2;
    }

    private double calculateDrawPercentage(TeamSeasonStats stats, boolean isHome) {
        if (stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 25.0;
        }
        int draws = isHome ? safeInt(stats.getSeasonDrawsHome()) : safeInt(stats.getSeasonDrawsAway());
        return (draws * 100.0) / stats.getMatchesPlayed();
    }

    private boolean hasHighCleanSheetRate(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double homeCleanSheetPct = calculateCleanSheetPct(homeStats, true);
        double awayCleanSheetPct = calculateCleanSheetPct(awayStats, false);

        return homeCleanSheetPct > 40.0 || awayCleanSheetPct > 40.0;
    }

    private double calculateCleanSheetPct(TeamSeasonStats stats, boolean isHome) {
        if (stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 0.0;
        }
        int cleanSheets = isHome ? safeInt(stats.getSeasonCleanSheetsHome()) : safeInt(stats.getSeasonCleanSheetsAway());
        return (cleanSheets * 100.0) / stats.getMatchesPlayed();
    }

    private ResultBttsCandidate findBestCandidate(FixtureContext context, double homeWinProb, 
            double drawProb, double awayWinProb, double bttsProb) {
        
        ResultBttsCandidate best = null;

        if (homeWinProb >= HOME_WIN_MIN) {
            double combined = (homeWinProb / 100.0) * bttsProb;
            String market = context.getHomeTeam().getName() + " + BTTS";
            if (best == null || combined > best.combinedProb) {
                best = new ResultBttsCandidate(market, "HOME", homeWinProb, bttsProb, combined);
            }
        }

        if (awayWinProb >= AWAY_WIN_MIN) {
            double combined = (awayWinProb / 100.0) * bttsProb;
            String market = context.getAwayTeam().getName() + " + BTTS";
            if (best == null || combined > best.combinedProb) {
                best = new ResultBttsCandidate(market, "AWAY", awayWinProb, bttsProb, combined);
            }
        }

        if (drawProb >= DRAW_MIN && bttsProb >= 60.0) {
            double combined = (drawProb / 100.0) * bttsProb;
            String market = "Draw + BTTS";
            if (best == null || combined > best.combinedProb) {
                best = new ResultBttsCandidate(market, "DRAW", drawProb, bttsProb, combined);
            }
        }

        return best;
    }

    private Map<String, Object> buildFactors(double homeWinProb, double drawProb, double awayWinProb,
                                              double bttsProb, ResultBttsCandidate best) {
        Map<String, Object> factors = new HashMap<>();
        factors.put("homeWinProbability", homeWinProb);
        factors.put("drawProbability", drawProb);
        factors.put("awayWinProbability", awayWinProb);
        factors.put("bttsProbability", bttsProb);
        factors.put("resultProbability", best.resultProb);
        factors.put("combinedProbability", best.combinedProb);
        return factors;
    }

    private String buildDescription(FixtureContext context, ResultBttsCandidate best, ConfidenceLevel confidence) {
        return String.format("%s confidence %s recommendation (%.1f%% combined) - %s vs %s",
                confidence.getDisplayName(),
                best.market,
                best.combinedProb,
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private double safeDouble(Double value) {
        return value != null ? value : 0.0;
    }

    private record ResultBttsCandidate(String market, String resultType, double resultProb, 
                                        double bttsProb, double combinedProb) {}
}
