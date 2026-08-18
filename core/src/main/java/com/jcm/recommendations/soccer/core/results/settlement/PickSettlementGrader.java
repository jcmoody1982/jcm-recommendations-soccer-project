package com.jcm.recommendations.soccer.core.results.settlement;

import com.jcm.recommendations.soccer.core.recommendation.model.RecommendationType;
import com.jcm.recommendations.soccer.domain.CompletedMatch;
import com.jcm.recommendations.soccer.domain.RecommendationSnapshot;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UC-033 graders for scoreline, corners, and booking-points markets.
 */
@Component
public class PickSettlementGrader {

    static final int YELLOW_CARD_POINTS = 10;
    static final int RED_CARD_POINTS = 25;

    private static final Pattern OVER_UNDER_LINE = Pattern.compile(
            "(?i)(Over|Under)\\s+(\\d+(?:\\.\\d+)?)\\s*"
                    + "(?:Goals|HT Goals|2H Goals|First Half Goals|Second Half Goals|Corners|Booking Points)?");

    public GradeResult grade(RecommendationSnapshot snapshot, CompletedMatch match) {
        RecommendationType type;
        try {
            type = RecommendationType.valueOf(snapshot.getType());
        } catch (RuntimeException e) {
            return GradeResult.unsupported("Unknown type: " + snapshot.getType());
        }

        return switch (type) {
            case OVER_CORNERS, UNDER_CORNERS -> gradeCorners(snapshot.getMarket(), match);
            case BOOKING_POINTS -> gradeBookingPoints(snapshot.getMarket(), match);
            case BTTS -> gradeBtts(snapshot.getMarket(), match);
            case OVER_GOALS, UNDER_GOALS, OVER_15_GOALS, OVER_25_GOALS ->
                    gradeOverUnderGoals(snapshot.getMarket(), match, true);
            case MATCH_RESULT -> gradeTeamOrDraw(snapshot, match, true);
            case DRAW -> gradeDraw(match);
            case DOUBLE_CHANCE -> gradeDoubleChance(snapshot.getMarket(), match);
            case RESULT_BTTS -> gradeResultBtts(snapshot, match);
            case CLEAN_SHEET -> gradeCleanSheet(snapshot, match);
            case FIRST_HALF_GOALS -> gradeHalfGoals(snapshot.getMarket(), match, true);
            case SECOND_HALF_GOALS -> gradeHalfGoals(snapshot.getMarket(), match, false);
            case TOP_VS_BOTTOM, WINNING_FORM_MISMATCH, LOSING_FORM_MISMATCH, HOME_AWAY_SPECIALIST ->
                    gradeTeamOrDraw(snapshot, match, false);
            case VALUE_BET -> gradeValueBet(snapshot, match);
        };
    }

    /** True when type is unknown (not a RecommendationType). Known types are always graded. */
    public boolean isUnsupportedType(String typeName) {
        try {
            RecommendationType.valueOf(typeName);
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }

    private GradeResult gradeBookingPoints(String market, CompletedMatch match) {
        if (match.getHomeYellowCards() == null || match.getAwayYellowCards() == null
                || match.getHomeRedCards() == null || match.getAwayRedCards() == null) {
            return GradeResult.pending("Missing cards");
        }
        int points = (match.getHomeYellowCards() + match.getAwayYellowCards()) * YELLOW_CARD_POINTS
                + (match.getHomeRedCards() + match.getAwayRedCards()) * RED_CARD_POINTS;
        return gradeOverUnderLine(market, points, true);
    }

    private GradeResult gradeCorners(String market, CompletedMatch match) {
        if (match.getHomeCorners() == null || match.getAwayCorners() == null) {
            return GradeResult.pending("Missing corners");
        }
        int total = match.getHomeCorners() + match.getAwayCorners();
        return gradeOverUnderLine(market, total, false);
    }

    private GradeResult gradeBtts(String market, CompletedMatch match) {
        Optional<Integer[]> ft = ftGoals(match);
        if (ft.isEmpty()) {
            return GradeResult.pending("Missing FT goals");
        }
        boolean bothScored = ft.get()[0] > 0 && ft.get()[1] > 0;
        String m = safe(market).toLowerCase(Locale.ROOT);
        if (m.contains("no")) {
            return bothScored ? GradeResult.loss() : GradeResult.win();
        }
        // Default BTTS Yes
        return bothScored ? GradeResult.win() : GradeResult.loss();
    }

    private GradeResult gradeOverUnderGoals(String market, CompletedMatch match, boolean requireFt) {
        Optional<Integer[]> ft = ftGoals(match);
        if (ft.isEmpty()) {
            return GradeResult.pending("Missing FT goals");
        }
        int total = ft.get()[0] + ft.get()[1];
        return gradeOverUnderLine(market, total, false);
    }

    private GradeResult gradeHalfGoals(String market, CompletedMatch match, boolean firstHalf) {
        Integer total;
        if (firstHalf) {
            if (match.getHtHomeGoals() == null || match.getHtAwayGoals() == null) {
                return GradeResult.pending("Missing HT goals");
            }
            total = match.getHtHomeGoals() + match.getHtAwayGoals();
        } else {
            total = secondHalfTotal(match);
            if (total == null) {
                return GradeResult.pending("Missing 2H goals");
            }
        }
        return gradeOverUnderLine(market, total, false);
    }

    private GradeResult gradeOverUnderLine(String market, int total) {
        return gradeOverUnderLine(market, total, false);
    }

    private GradeResult gradeOverUnderLine(String market, int total, boolean voidOnExactLine) {
        Matcher matcher = OVER_UNDER_LINE.matcher(safe(market));
        if (!matcher.find()) {
            return GradeResult.unsupported("Unparseable over/under market: " + market);
        }
        boolean over = matcher.group(1).equalsIgnoreCase("Over");
        double line = Double.parseDouble(matcher.group(2));
        if (voidOnExactLine && Math.abs(total - line) < 1e-9) {
            return GradeResult.voided("Push at line " + line);
        }
        boolean hit = over ? total > line : total < line;
        return hit ? GradeResult.win() : GradeResult.loss();
    }

    private GradeResult gradeDraw(CompletedMatch match) {
        Optional<Integer[]> ft = ftGoals(match);
        if (ft.isEmpty()) {
            return GradeResult.pending("Missing FT goals");
        }
        return ft.get()[0].equals(ft.get()[1]) ? GradeResult.win() : GradeResult.loss();
    }

    private GradeResult gradeDoubleChance(String market, CompletedMatch match) {
        Optional<Integer[]> ft = ftGoals(match);
        if (ft.isEmpty()) {
            return GradeResult.pending("Missing FT goals");
        }
        int home = ft.get()[0];
        int away = ft.get()[1];
        String m = safe(market).toUpperCase(Locale.ROOT);
        if (m.contains("1X") || m.contains("HOME/DRAW")) {
            return home >= away ? GradeResult.win() : GradeResult.loss();
        }
        if (m.contains("X2") || m.contains("DRAW/AWAY")) {
            return away >= home ? GradeResult.win() : GradeResult.loss();
        }
        if (m.contains("12") || m.contains("HOME/AWAY")) {
            return home != away ? GradeResult.win() : GradeResult.loss();
        }
        return GradeResult.unsupported("Unparseable double chance: " + market);
    }

    private GradeResult gradeTeamOrDraw(RecommendationSnapshot snapshot, CompletedMatch match, boolean allowDrawMarket) {
        Optional<Integer[]> ft = ftGoals(match);
        if (ft.isEmpty()) {
            return GradeResult.pending("Missing FT goals");
        }
        int home = ft.get()[0];
        int away = ft.get()[1];
        String market = safe(snapshot.getMarket());

        if (allowDrawMarket && market.equalsIgnoreCase("Draw")) {
            return home == away ? GradeResult.win() : GradeResult.loss();
        }
        if (market.equalsIgnoreCase("Home Win")) {
            return home > away ? GradeResult.win() : GradeResult.loss();
        }
        if (market.equalsIgnoreCase("Away Win")) {
            return away > home ? GradeResult.win() : GradeResult.loss();
        }
        if (namesEqual(market, snapshot.getHomeTeamName())) {
            return home > away ? GradeResult.win() : GradeResult.loss();
        }
        if (namesEqual(market, snapshot.getAwayTeamName())) {
            return away > home ? GradeResult.win() : GradeResult.loss();
        }
        return GradeResult.unsupported("Unparseable team/result market: " + market);
    }

    private GradeResult gradeCleanSheet(RecommendationSnapshot snapshot, CompletedMatch match) {
        Optional<Integer[]> ft = ftGoals(match);
        if (ft.isEmpty()) {
            return GradeResult.pending("Missing FT goals");
        }
        String market = safe(snapshot.getMarket());
        String team = market.replaceAll("(?i)\\s*Clean Sheet\\s*$", "").trim();
        if (namesEqual(team, snapshot.getHomeTeamName())) {
            return ft.get()[1] == 0 ? GradeResult.win() : GradeResult.loss();
        }
        if (namesEqual(team, snapshot.getAwayTeamName())) {
            return ft.get()[0] == 0 ? GradeResult.win() : GradeResult.loss();
        }
        return GradeResult.unsupported("Unparseable clean sheet market: " + market);
    }

    private GradeResult gradeResultBtts(RecommendationSnapshot snapshot, CompletedMatch match) {
        Optional<Integer[]> ft = ftGoals(match);
        if (ft.isEmpty()) {
            return GradeResult.pending("Missing FT goals");
        }
        int home = ft.get()[0];
        int away = ft.get()[1];
        boolean btts = home > 0 && away > 0;
        String market = safe(snapshot.getMarket());

        if (!market.toUpperCase(Locale.ROOT).contains("BTTS")) {
            return GradeResult.unsupported("Unparseable result+BTTS market: " + market);
        }
        String resultPart = market.replaceAll("(?i)\\s*\\+\\s*BTTS\\s*$", "").trim();

        boolean resultOk;
        if (resultPart.equalsIgnoreCase("Draw")) {
            resultOk = home == away;
        } else if (namesEqual(resultPart, snapshot.getHomeTeamName())) {
            resultOk = home > away;
        } else if (namesEqual(resultPart, snapshot.getAwayTeamName())) {
            resultOk = away > home;
        } else {
            return GradeResult.unsupported("Unparseable result+BTTS result leg: " + market);
        }
        return (resultOk && btts) ? GradeResult.win() : GradeResult.loss();
    }

    private GradeResult gradeValueBet(RecommendationSnapshot snapshot, CompletedMatch match) {
        String market = safe(snapshot.getMarket());
        String lower = market.toLowerCase(Locale.ROOT);
        if (lower.startsWith("btts")) {
            return gradeBtts(market, match);
        }
        if (lower.contains("goals")) {
            return gradeOverUnderGoals(market, match, true);
        }
        if (lower.equals("home win") || lower.equals("away win") || lower.equals("draw")) {
            return gradeTeamOrDraw(snapshot, match, true);
        }
        if (lower.contains("corner")) {
            return gradeCorners(market, match);
        }
        if (lower.contains("booking")) {
            return gradeBookingPoints(market, match);
        }
        return GradeResult.unsupported("Unparseable value bet market: " + market);
    }

    private static Optional<Integer[]> ftGoals(CompletedMatch match) {
        if (match.getHomeGoals() == null || match.getAwayGoals() == null) {
            return Optional.empty();
        }
        return Optional.of(new Integer[]{match.getHomeGoals(), match.getAwayGoals()});
    }

    private static Integer secondHalfTotal(CompletedMatch match) {
        if (match.getSecondHalfHomeGoals() != null && match.getSecondHalfAwayGoals() != null) {
            return match.getSecondHalfHomeGoals() + match.getSecondHalfAwayGoals();
        }
        if (match.getHomeGoals() == null || match.getAwayGoals() == null
                || match.getHtHomeGoals() == null || match.getHtAwayGoals() == null) {
            return null;
        }
        return (match.getHomeGoals() - match.getHtHomeGoals())
                + (match.getAwayGoals() - match.getHtAwayGoals());
    }

    private static boolean namesEqual(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.trim().equalsIgnoreCase(b.trim());
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
