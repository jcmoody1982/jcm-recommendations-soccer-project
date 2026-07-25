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
public class DoubleChanceRecommendationEngine implements RecommendationEngine {

    private static final double THRESHOLD_STRONG_1X = 70.0;
    private static final double THRESHOLD_MODERATE_1X = 60.0;
    private static final double THRESHOLD_STRONG_X2 = 65.0;
    private static final double THRESHOLD_MODERATE_X2 = 55.0;
    private static final double VALUE_THRESHOLD = 5.0;

    @Override
    public RecommendationType getType() {
        return RecommendationType.DOUBLE_CHANCE;
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        log.debug("Analyzing Double Chance for fixture: fixtureId={}, {} vs {}",
                context.getFixture().getId(),
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());

        double homeWinProb = calculateWinProbability(context.getHomeTeamStats(), true);
        double awayWinProb = calculateWinProbability(context.getAwayTeamStats(), false);
        double drawProb = calculateDrawProbability(context);

        double homeDrawProb = homeWinProb + drawProb;
        double drawAwayProb = drawProb + awayWinProb;

        String market;
        double bestProb;
        ConfidenceLevel confidence;

        if (homeDrawProb >= drawAwayProb && homeDrawProb >= THRESHOLD_MODERATE_1X) {
            market = "Home/Draw (1X)";
            bestProb = homeDrawProb;
            confidence = homeDrawProb >= THRESHOLD_STRONG_1X ? ConfidenceLevel.STRONG : ConfidenceLevel.MODERATE;
        } else if (drawAwayProb >= THRESHOLD_MODERATE_X2) {
            market = "Draw/Away (X2)";
            bestProb = drawAwayProb;
            confidence = drawAwayProb >= THRESHOLD_STRONG_X2 ? ConfidenceLevel.STRONG : ConfidenceLevel.MODERATE;
        } else {
            return Optional.empty();
        }

        Map<String, Object> factors = buildFactors(homeWinProb, drawProb, awayWinProb, homeDrawProb, drawAwayProb);

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
                .type(RecommendationType.DOUBLE_CHANCE)
                .confidence(confidence)
                .score(bestProb)
                .market(market)
                .odds(null)
                .description(buildDescription(context, market, bestProb, confidence))
                .factors(factors)
                .generatedAt(Instant.now())
                .build();

        log.info("Double Chance recommendation: fixtureId={}, market={}, probability={}, confidence={}",
                context.getFixture().getId(), market, String.format("%.1f", bestProb), confidence);

        return Optional.of(recommendation);
    }

    private double calculateWinProbability(TeamSeasonStats stats, boolean isHome) {
        if (stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 33.3;
        }

        int wins = isHome ? safeInt(stats.getSeasonWinsHome()) : safeInt(stats.getSeasonWinsAway());
        double winPct = (wins * 100.0) / stats.getMatchesPlayed();

        double ppg = isHome ? safeDouble(stats.getPpgHome()) : safeDouble(stats.getPpgAway());
        double ppgFactor = Math.min(1.0, ppg / 2.5);

        return winPct * 0.7 + (ppgFactor * 30);
    }

    private double calculateDrawProbability(FixtureContext context) {
        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        double homeDrawPct = calculateDrawPercentage(homeStats, true);
        double awayDrawPct = calculateDrawPercentage(awayStats, false);

        double ppgDiff = Math.abs(safeDouble(homeStats.getPpgHome()) - safeDouble(awayStats.getPpgAway()));
        double similarityBonus = ppgDiff < 0.3 ? 10.0 : ppgDiff < 0.5 ? 5.0 : 0.0;

        return (homeDrawPct + awayDrawPct) / 2 + similarityBonus;
    }

    private double calculateDrawPercentage(TeamSeasonStats stats, boolean isHome) {
        if (stats.getMatchesPlayed() == null || stats.getMatchesPlayed() == 0) {
            return 25.0;
        }
        int draws = isHome ? safeInt(stats.getSeasonDrawsHome()) : safeInt(stats.getSeasonDrawsAway());
        return (draws * 100.0) / stats.getMatchesPlayed();
    }

    private Map<String, Object> buildFactors(double homeWin, double draw, double awayWin,
                                              double homeDrawProb, double drawAwayProb) {
        Map<String, Object> factors = new HashMap<>();
        factors.put("homeWinProbability", homeWin);
        factors.put("drawProbability", draw);
        factors.put("awayWinProbability", awayWin);
        factors.put("homeDrawCombined", homeDrawProb);
        factors.put("drawAwayCombined", drawAwayProb);
        return factors;
    }

    private String buildDescription(FixtureContext context, String market, double probability,
                                    ConfidenceLevel confidence) {
        return String.format("%s confidence %s recommendation (%.1f%% combined probability) - %s vs %s",
                confidence.getDisplayName(),
                market,
                probability,
                context.getHomeTeam().getName(),
                context.getAwayTeam().getName());
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private double safeDouble(Double value) {
        return value != null ? value : 0.0;
    }
}
