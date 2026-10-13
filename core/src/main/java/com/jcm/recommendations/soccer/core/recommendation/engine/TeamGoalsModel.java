package com.jcm.recommendations.soccer.core.recommendation.engine;

import com.jcm.recommendations.soccer.core.recommendation.model.FixtureContext;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;

import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.calculateVenueConcededAvg;
import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.calculateVenueGoalsAvg;
import static com.jcm.recommendations.soccer.core.recommendation.util.RecommendationUtils.safeDouble;

/**
 * A side's expected goals in one fixture: its scoring rate at this venue averaged with what the
 * opponent concedes at the reverse venue, from season goals, xG and recent form. Shared by Team
 * Over 0.5 and the player props so both read the same fixture expectation.
 */
final class TeamGoalsModel {

    // Contributions to the expected goals, renormalised over whichever inputs are present.
    private static final double WEIGHT_SEASON = 0.50;
    private static final double WEIGHT_XG = 0.30;
    private static final double WEIGHT_FORM = 0.20;

    private TeamGoalsModel() {}

    static double expectedGoals(FixtureContext context, boolean home) {
        TeamSeasonStats team = home ? context.getHomeTeamStats() : context.getAwayTeamStats();
        TeamSeasonStats opponent = home ? context.getAwayTeamStats() : context.getHomeTeamStats();
        TeamRecentForm teamForm = context.hasRecentForm()
                ? (home ? context.getHomeTeamForm() : context.getAwayTeamForm())
                : null;
        TeamRecentForm opponentForm = context.hasRecentForm()
                ? (home ? context.getAwayTeamForm() : context.getHomeTeamForm())
                : null;

        double seasonScored = calculateVenueGoalsAvg(team, home, 1.0);
        double seasonConceded = calculateVenueConcededAvg(opponent, !home, 1.0);
        double weightedSum = ((seasonScored + seasonConceded) / 2.0) * WEIGHT_SEASON;
        double weightUsed = WEIGHT_SEASON;

        Double xgFor = home ? team.getXgForAvgHome() : team.getXgForAvgAway();
        Double xgAgainst = home ? opponent.getXgAgainstAvgAway() : opponent.getXgAgainstAvgHome();
        if (xgFor != null && xgAgainst != null) {
            weightedSum += ((xgFor + xgAgainst) / 2.0) * WEIGHT_XG;
            weightUsed += WEIGHT_XG;
        }

        if (teamForm != null && opponentForm != null) {
            double formScored = safeDouble(home ? teamForm.getScoredAvgHome() : teamForm.getScoredAvgAway(), seasonScored);
            double formConceded = safeDouble(
                    home ? opponentForm.getConcededAvgAway() : opponentForm.getConcededAvgHome(), seasonConceded);
            weightedSum += ((formScored + formConceded) / 2.0) * WEIGHT_FORM;
            weightUsed += WEIGHT_FORM;
        }

        return weightedSum / weightUsed;
    }

    /** The side's season goals per match across both venues, or null when the feed has neither. */
    static Double seasonGoalsPerMatch(TeamSeasonStats team) {
        if (team == null) {
            return null;
        }
        if (team.getScoredAvgOverall() != null && team.getScoredAvgOverall() > 0) {
            return team.getScoredAvgOverall();
        }
        Integer goals = team.getSeasonGoalsOverall();
        Integer matches = team.getMatchesPlayed();
        if (goals == null || matches == null || matches <= 0) {
            return null;
        }
        return goals / (double) matches;
    }
}
