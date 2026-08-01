package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.*;
import com.jcm.recommendations.soccer.core.recommendation.util.RecommendationFactory;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.*;

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

        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(RecommendationType.RESULT_BTTS)
                .confidence(confidence)
                .score(best.combinedProb)
                .market(best.market)
                .odds(null)
                .description(buildDescription(context, best, confidence))
                .factors(factors)
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

    private boolean hasHighCleanSheetRate(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double homeCleanSheetPct = calculateCleanSheetPercentage(homeStats, true);
        double awayCleanSheetPct = calculateCleanSheetPercentage(awayStats, false);

        return homeCleanSheetPct > 40.0 || awayCleanSheetPct > 40.0;
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
        return RecommendationFactory.buildStandardDescription(
                confidence, best.market, best.combinedProb, "combined", context);
    }

    private record ResultBttsCandidate(String market, String resultType, double resultProb, 
                                        double bttsProb, double combinedProb) {}
}
