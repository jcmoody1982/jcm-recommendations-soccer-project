package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.RecommendationEngine;
import com.jcm.recommendations.soccer.core.recommendation.model.ConfidenceLevel;
import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.core.recommendation.model.Recommendation;
import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.core.recommendation.util.MatchBriefCopy;
import com.jcm.recommendations.soccer.core.recommendation.util.RecommendationFactory;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.calculateVenueConcededAvg;
import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.calculateVenueGoalsAvg;
import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.safeDouble;
import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.safeInt;

/**
 * UC-018: Home/Away Specialist — rebuilt after the 2026-09-11..23 window (~45% hit,
 * Strong worse than Moderate, disparity score mistaken for win probability).
 *
 * <p>Publishes only when three things agree:
 * <ol>
 *   <li>Home side has a real venue edge (specialist or fortress) on adequate sample</li>
 *   <li>Away side is a confirmed poor traveler on adequate sample</li>
 *   <li>Home-win odds exist in a stakeable band and the published score is a market blend</li>
 * </ol>
 *
 * <p>Away-specialist / fade-home paths stay paused. Score is a home-win probability, not a
 * raw disparity index.
 */
@Component
@Slf4j
public class HomeAwaySpecialistEngine implements RecommendationEngine {

    private static final int MIN_VENUE_MATCHES = 8;

    private static final double MIN_HOME_PPG_EDGE = 0.70;
    private static final double MIN_HOME_WIN_EDGE = 20.0;
    private static final double FORTRESS_HOME_WIN_PCT = 65.0;
    private static final double FORTRESS_HOME_LOSS_PCT = 18.0;
    private static final double FORTRESS_HOME_CONCEDED = 1.0;

    private static final double POOR_TRAVELER_AWAY_PPG = 0.90;
    private static final double POOR_TRAVELER_AWAY_WIN_PCT = 22.0;

    /** Exclusive lower bound — skip near-locks. */
    static final double MIN_HOME_ODDS_EXCLUSIVE = 1.40;
    /** Inclusive upper bound — skip longshots the old disparity board loved. */
    static final double MAX_HOME_ODDS_INCLUSIVE = 2.50;

    private static final double PUBLISH_MODEL_WEIGHT = 0.40;
    private static final double PUBLISH_MARKET_WEIGHT = 0.60;

    private static final double THRESHOLD_STRONG = 60.0;
    private static final double THRESHOLD_MODERATE = 55.0;
    private static final double STRONG_MAX_ODDS = 2.20;

    private static final double FORM_DECLINE_PPG = -0.30;

    @Override
    public RecommendationType getType() {
        return RecommendationType.HOME_AWAY_SPECIALIST;
    }

    @Override
    public Optional<Recommendation> analyze(FixtureContext context) {
        if (!isApplicable(context)) {
            return Optional.empty();
        }

        TeamSeasonStats homeStats = context.getHomeTeamStats();
        TeamSeasonStats awayStats = context.getAwayTeamStats();

        int homeMatches = matchesAtVenue(homeStats, true);
        int awayMatches = matchesAtVenue(awayStats, false);
        if (homeMatches < MIN_VENUE_MATCHES || awayMatches < MIN_VENUE_MATCHES) {
            return Optional.empty();
        }

        boolean homeSpecialist = isHomeSpecialist(homeStats);
        boolean homeFortress = isHomeFortress(homeStats);
        boolean poorTraveler = isPoorTraveler(awayStats);
        if ((!homeSpecialist && !homeFortress) || !poorTraveler) {
            return Optional.empty();
        }

        if (homeFormDeclining(context.getHomeTeamForm(), homeStats)) {
            return Optional.empty();
        }

        Double homeOdds = context.hasOdds() ? context.getOdds().getOddsFt1() : null;
        if (homeOdds == null
                || homeOdds <= MIN_HOME_ODDS_EXCLUSIVE
                || homeOdds > MAX_HOME_ODDS_INCLUSIVE) {
            return Optional.empty();
        }

        double rawModel = estimateHomeWinProbability(homeStats, awayStats, homeSpecialist, homeFortress);
        double implied = (1.0 / homeOdds) * 100.0;
        double published = PUBLISH_MODEL_WEIGHT * rawModel + PUBLISH_MARKET_WEIGHT * implied;

        boolean hasXg = hasHomeAwayXg(homeStats);
        ConfidenceLevel confidence = determineConfidence(published, homeOdds, hasXg);
        if (confidence == ConfidenceLevel.WEAK) {
            return Optional.empty();
        }

        String classification = buildClassification(homeSpecialist, homeFortress);
        Map<String, Object> factors = buildFactors(
                context, homeStats, awayStats, classification, homeSpecialist, homeFortress,
                poorTraveler, homeOdds, implied, rawModel, published, hasXg, homeMatches, awayMatches);

        Recommendation recommendation = RecommendationFactory.fromContext(context)
                .type(RecommendationType.HOME_AWAY_SPECIALIST)
                .confidence(confidence)
                .score(published)
                .market(context.getHomeTeam().getName())
                .odds(homeOdds)
                .description(buildDescription(context, classification, published, confidence, homeOdds))
                .factors(factors)
                .build();

        log.info(
                "Home/Away Specialist: fixtureId={}, classification={}, published={}, odds={}, confidence={}",
                context.getFixture().getId(),
                classification,
                String.format("%.1f", published),
                homeOdds,
                confidence);

        return Optional.of(recommendation);
    }

    private static boolean isHomeSpecialist(TeamSeasonStats stats) {
        double homePpg = safeDouble(stats.getPpgHome());
        double awayPpg = safeDouble(stats.getPpgAway());
        double homeWin = winPct(stats, true);
        double awayWin = winPct(stats, false);
        return (homePpg - awayPpg) >= MIN_HOME_PPG_EDGE
                && (homeWin - awayWin) >= MIN_HOME_WIN_EDGE;
    }

    private static boolean isHomeFortress(TeamSeasonStats stats) {
        return winPct(stats, true) >= FORTRESS_HOME_WIN_PCT
                && lossPct(stats, true) <= FORTRESS_HOME_LOSS_PCT
                && calculateVenueConcededAvg(stats, true) <= FORTRESS_HOME_CONCEDED;
    }

    private static boolean isPoorTraveler(TeamSeasonStats stats) {
        return safeDouble(stats.getPpgAway()) < POOR_TRAVELER_AWAY_PPG
                && winPct(stats, false) < POOR_TRAVELER_AWAY_WIN_PCT;
    }

    private static boolean homeFormDeclining(TeamRecentForm form, TeamSeasonStats season) {
        if (form == null || form.getPpgHome() == null || season.getPpgHome() == null) {
            return false;
        }
        return safeDouble(form.getPpgHome()) - safeDouble(season.getPpgHome()) <= FORM_DECLINE_PPG;
    }

    /**
     * Maps venue edges into a rough home-win probability band before market blend.
     * Anchored near mid-50s so disparity cannot mint a fake 80% alone.
     */
    static double estimateHomeWinProbability(
            TeamSeasonStats homeStats,
            TeamSeasonStats awayStats,
            boolean homeSpecialist,
            boolean homeFortress) {
        double base = 52.0;
        double ppgEdge = safeDouble(homeStats.getPpgHome()) - safeDouble(homeStats.getPpgAway());
        double winEdge = winPct(homeStats, true) - winPct(homeStats, false);
        double travelerWeakness = POOR_TRAVELER_AWAY_WIN_PCT - winPct(awayStats, false);

        base += Math.min(8.0, ppgEdge * 4.0);
        base += Math.min(6.0, winEdge * 0.15);
        base += Math.min(6.0, travelerWeakness * 0.25);
        if (homeFortress) {
            base += 3.0;
        }
        if (homeSpecialist && homeFortress) {
            base += 2.0;
        }
        if (hasHomeAwayXg(homeStats)) {
            double xgEdge = safeDouble(homeStats.getXgForAvgHome()) - safeDouble(homeStats.getXgForAvgAway());
            base += Math.max(-2.0, Math.min(3.0, xgEdge * 4.0));
        }
        return Math.max(48.0, Math.min(68.0, base));
    }

    private static ConfidenceLevel determineConfidence(double published, double odds, boolean hasXg) {
        if (published >= THRESHOLD_STRONG && odds <= STRONG_MAX_ODDS && hasXg) {
            return ConfidenceLevel.STRONG;
        }
        if (published >= THRESHOLD_MODERATE) {
            return ConfidenceLevel.MODERATE;
        }
        return ConfidenceLevel.WEAK;
    }

    private static String buildClassification(boolean specialist, boolean fortress) {
        if (specialist && fortress) {
            return "Home Fortress + Poor Traveler";
        }
        if (fortress) {
            return "Home Fortress + Poor Traveler";
        }
        return "Home Specialist + Poor Traveler";
    }

    private static Map<String, Object> buildFactors(
            FixtureContext context,
            TeamSeasonStats homeStats,
            TeamSeasonStats awayStats,
            String classification,
            boolean homeSpecialist,
            boolean homeFortress,
            boolean poorTraveler,
            double homeOdds,
            double implied,
            double rawModel,
            double published,
            boolean hasXg,
            int homeMatches,
            int awayMatches) {
        Map<String, Object> factors = new HashMap<>();
        factors.put("team", context.getHomeTeam().getName());
        factors.put("isHomeTeam", true);
        factors.put("classification", classification);
        factors.put("recommendation", "Back Home Win");
        factors.put("homeOnlyPicks", true);
        factors.put("awaySpecialistPaused", true);
        factors.put("dualConfirmationRequired", true);
        factors.put("homeSpecialist", homeSpecialist);
        factors.put("homeFortress", homeFortress);
        factors.put("poorTraveler", poorTraveler);
        factors.put("homeVenueMatches", homeMatches);
        factors.put("awayVenueMatches", awayMatches);
        factors.put("homePpg", safeDouble(homeStats.getPpgHome()));
        factors.put("awayPpgHomeTeam", safeDouble(homeStats.getPpgAway()));
        factors.put("awayTeamAwayPpg", safeDouble(awayStats.getPpgAway()));
        factors.put("homeWinPct", winPct(homeStats, true));
        factors.put("homeTeamAwayWinPct", winPct(homeStats, false));
        factors.put("awayTeamAwayWinPct", winPct(awayStats, false));
        factors.put("homeGoalsAvg", calculateVenueGoalsAvg(homeStats, true));
        factors.put("awayGoalsAvg", calculateVenueGoalsAvg(awayStats, false));
        factors.put("homeConcededAvg", calculateVenueConcededAvg(homeStats, true));
        factors.put("awayConcededAvg", calculateVenueConcededAvg(awayStats, false));
        factors.put("oddsFt1", homeOdds);
        factors.put("impliedHomeWinPct", implied);
        factors.put("rawModelProbability", rawModel);
        factors.put("publishedScore", published);
        factors.put("marketBlendApplied", true);
        factors.put("publishModelWeight", PUBLISH_MODEL_WEIGHT);
        factors.put("publishMarketWeight", PUBLISH_MARKET_WEIGHT);
        factors.put("xgDataAvailable", hasXg);
        if (hasXg) {
            factors.put("homeXgAvg", safeDouble(homeStats.getXgForAvgHome()));
            factors.put("awayXgAvg", safeDouble(homeStats.getXgForAvgAway()));
        }
        return factors;
    }

    private static String buildDescription(
            FixtureContext context,
            String classification,
            double published,
            ConfidenceLevel confidence,
            double homeOdds) {
        return MatchBriefCopy.narrate(MatchBriefCopy.Brief.builder()
                .confidence(confidence)
                .selection(classification + ": " + context.getHomeTeam().getName())
                .context(context)
                .probabilityPct(published)
                .colourNote(String.format(
                        "Dual venue confirmation · home win @ %.2f · blended win likelihood %.0f%%",
                        homeOdds,
                        published))
                .build());
    }

    private static double winPct(TeamSeasonStats stats, boolean isHome) {
        int matches = matchesAtVenue(stats, isHome);
        if (matches == 0) {
            return 33.3;
        }
        int wins = isHome ? safeInt(stats.getSeasonWinsHome()) : safeInt(stats.getSeasonWinsAway());
        return (wins * 100.0) / matches;
    }

    private static double lossPct(TeamSeasonStats stats, boolean isHome) {
        int matches = matchesAtVenue(stats, isHome);
        if (matches == 0) {
            return 33.3;
        }
        int losses = isHome ? safeInt(stats.getSeasonLossesHome()) : safeInt(stats.getSeasonLossesAway());
        return (losses * 100.0) / matches;
    }

    private static int matchesAtVenue(TeamSeasonStats stats, boolean isHome) {
        if (isHome) {
            return safeInt(stats.getSeasonWinsHome())
                    + safeInt(stats.getSeasonDrawsHome())
                    + safeInt(stats.getSeasonLossesHome());
        }
        return safeInt(stats.getSeasonWinsAway())
                + safeInt(stats.getSeasonDrawsAway())
                + safeInt(stats.getSeasonLossesAway());
    }

    private static boolean hasHomeAwayXg(TeamSeasonStats stats) {
        return stats.getXgForAvgHome() != null && stats.getXgForAvgAway() != null;
    }
}
