package com.jcm.recommendations.soccer.core.results.settlement;

import com.jcm.recommendations.soccer.domain.CompletedMatch;
import com.jcm.recommendations.soccer.domain.PickOutcome;
import com.jcm.recommendations.soccer.domain.RecommendationSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PickSettlementGraderTest {

    private PickSettlementGrader grader;

    @BeforeEach
    void setUp() {
        grader = new PickSettlementGrader();
    }

    @Test
    void bttsYesWinsWhenBothScore() {
        GradeResult result = grader.grade(snapshot("BTTS", "BTTS Yes"), complete(2, 1));
        assertThat(result.outcome()).isEqualTo(PickOutcome.WIN);
    }

    @Test
    void bttsYesLosesWhenOneBlank() {
        GradeResult result = grader.grade(snapshot("BTTS", "BTTS Yes"), complete(2, 0));
        assertThat(result.outcome()).isEqualTo(PickOutcome.LOSS);
    }

    @Test
    void over25Wins() {
        GradeResult result = grader.grade(snapshot("OVER_GOALS", "Over 2.5 Goals"), complete(2, 1));
        assertThat(result.outcome()).isEqualTo(PickOutcome.WIN);
    }

    @Test
    void under25LosesWhenThreeGoals() {
        GradeResult result = grader.grade(snapshot("UNDER_GOALS", "Under 2.5 Goals"), complete(2, 1));
        assertThat(result.outcome()).isEqualTo(PickOutcome.LOSS);
    }

    @Test
    void matchResultTeamNameWins() {
        RecommendationSnapshot snap = snapshot("MATCH_RESULT", "Arsenal");
        GradeResult result = grader.grade(snap, complete(2, 0));
        assertThat(result.outcome()).isEqualTo(PickOutcome.WIN);
    }

    @Test
    void matchResultTeamNameDrawIsLoss() {
        RecommendationSnapshot snap = snapshot("MATCH_RESULT", "Arsenal");
        GradeResult result = grader.grade(snap, complete(1, 1));
        assertThat(result.outcome()).isEqualTo(PickOutcome.LOSS);
    }

    @Test
    void doubleChance1XWinsOnDraw() {
        GradeResult result = grader.grade(snapshot("DOUBLE_CHANCE", "Home/Draw (1X)"), complete(1, 1));
        assertThat(result.outcome()).isEqualTo(PickOutcome.WIN);
    }

    @Test
    void resultBttsRequiresBothLegs() {
        RecommendationSnapshot snap = snapshot("RESULT_BTTS", "Arsenal + BTTS");
        assertThat(grader.grade(snap, complete(2, 1)).outcome()).isEqualTo(PickOutcome.WIN);
        assertThat(grader.grade(snap, complete(2, 0)).outcome()).isEqualTo(PickOutcome.LOSS);
    }

    @Test
    void cleanSheetHome() {
        RecommendationSnapshot snap = snapshot("CLEAN_SHEET", "Arsenal Clean Sheet");
        assertThat(grader.grade(snap, complete(1, 0)).outcome()).isEqualTo(PickOutcome.WIN);
        assertThat(grader.grade(snap, complete(1, 1)).outcome()).isEqualTo(PickOutcome.LOSS);
    }

    @Test
    void firstHalfGoalsPendingWithoutHt() {
        CompletedMatch match = complete(2, 1);
        match.setHtHomeGoals(null);
        match.setHtAwayGoals(null);
        GradeResult result = grader.grade(snapshot("FIRST_HALF_GOALS", "Over 0.5 HT Goals"), match);
        assertThat(result.outcome()).isEqualTo(PickOutcome.PENDING);
    }

    @Test
    void firstHalfGoalsWinsWithHt() {
        CompletedMatch match = complete(2, 1);
        match.setHtHomeGoals(1);
        match.setHtAwayGoals(0);
        GradeResult result = grader.grade(snapshot("FIRST_HALF_GOALS", "Over 0.5 HT Goals"), match);
        assertThat(result.outcome()).isEqualTo(PickOutcome.WIN);
    }

    @Test
    void cornersOverWinsWhenTotalAboveLine() {
        CompletedMatch match = complete(1, 0);
        match.setHomeCorners(6);
        match.setAwayCorners(4);
        assertThat(grader.grade(snapshot("OVER_CORNERS", "Over 9.5 Corners"), match).outcome())
                .isEqualTo(PickOutcome.WIN);
        assertThat(grader.grade(snapshot("UNDER_CORNERS", "Under 8.5 Corners"), match).outcome())
                .isEqualTo(PickOutcome.LOSS);
    }

    @Test
    void cornersPendingWithoutTotals() {
        GradeResult result = grader.grade(snapshot("OVER_CORNERS", "Over 9.5 Corners"), complete(1, 0));
        assertThat(result.outcome()).isEqualTo(PickOutcome.PENDING);
    }

    @Test
    void bookingPointsYellowTenRedTwentyFive() {
        CompletedMatch match = complete(1, 0);
        match.setHomeYellowCards(2);
        match.setAwayYellowCards(1);
        match.setHomeRedCards(1);
        match.setAwayRedCards(0);
        // 30 + 25 = 55 → Over 50 wins; Under 30 loses
        assertThat(grader.grade(snapshot("BOOKING_POINTS", "Over 50 Booking Points"), match).outcome())
                .isEqualTo(PickOutcome.WIN);
        assertThat(grader.grade(snapshot("BOOKING_POINTS", "Under 30 Booking Points"), match).outcome())
                .isEqualTo(PickOutcome.LOSS);
    }

    @Test
    void bookingPointsExactLineIsPushVoid() {
        CompletedMatch match = complete(1, 0);
        match.setHomeYellowCards(2);
        match.setAwayYellowCards(2);
        match.setHomeRedCards(0);
        match.setAwayRedCards(0);
        // 40 points exact → void (push) for Over/Under 40
        assertThat(grader.grade(snapshot("BOOKING_POINTS", "Over 40 Booking Points"), match).outcome())
                .isEqualTo(PickOutcome.VOID);
        assertThat(grader.grade(snapshot("BOOKING_POINTS", "Under 40 Booking Points"), match).outcome())
                .isEqualTo(PickOutcome.VOID);
    }

    @Test
    void bookingPointsPendingWithoutCards() {
        assertThat(grader.grade(snapshot("BOOKING_POINTS", "Over 40 Booking Points"), complete(1, 0)).outcome())
                .isEqualTo(PickOutcome.PENDING);
    }

    @Test
    void valueBetCornersAndBookings() {
        CompletedMatch match = complete(1, 0);
        match.setHomeCorners(3);
        match.setAwayCorners(5);
        match.setHomeYellowCards(1);
        match.setAwayYellowCards(0);
        match.setHomeRedCards(0);
        match.setAwayRedCards(0);
        assertThat(grader.grade(snapshot("VALUE_BET", "Under 8.5 Corners"), match).outcome())
                .isEqualTo(PickOutcome.WIN);
        assertThat(grader.grade(snapshot("VALUE_BET", "Under 30 Booking Points"), match).outcome())
                .isEqualTo(PickOutcome.WIN);
    }

    @Test
    void doubleChanceX2() {
        assertThat(grader.grade(snapshot("DOUBLE_CHANCE", "Draw/Away (X2)"), complete(0, 1)).outcome())
                .isEqualTo(PickOutcome.WIN);
    }

    @Test
    void valueBetHomeWin() {
        GradeResult result = grader.grade(snapshot("VALUE_BET", "Home Win"), complete(2, 0));
        assertThat(result.outcome()).isEqualTo(PickOutcome.WIN);
    }

    @Test
    void secondHalfDerivedFromFtMinusHt() {
        CompletedMatch match = complete(3, 1);
        match.setHtHomeGoals(1);
        match.setHtAwayGoals(0);
        GradeResult result = grader.grade(snapshot("SECOND_HALF_GOALS", "Over 0.5 2H Goals"), match);
        assertThat(result.outcome()).isEqualTo(PickOutcome.WIN);
    }

    @Test
    void bttsNoWinsWhenOneBlank() {
        assertThat(grader.grade(snapshot("BTTS", "BTTS No"), complete(2, 0)).outcome())
                .isEqualTo(PickOutcome.WIN);
        assertThat(grader.grade(snapshot("VALUE_BET", "BTTS No"), complete(1, 1)).outcome())
                .isEqualTo(PickOutcome.LOSS);
    }

    @Test
    void under15AndOver35Lines() {
        assertThat(grader.grade(snapshot("UNDER_GOALS", "Under 1.5 Goals"), complete(1, 0)).outcome())
                .isEqualTo(PickOutcome.WIN);
        assertThat(grader.grade(snapshot("OVER_GOALS", "Over 3.5 Goals"), complete(2, 2)).outcome())
                .isEqualTo(PickOutcome.WIN);
        assertThat(grader.grade(snapshot("OVER_GOALS", "Over 3.5 Goals"), complete(2, 1)).outcome())
                .isEqualTo(PickOutcome.LOSS);
    }

    @Test
    void drawMarket() {
        assertThat(grader.grade(snapshot("DRAW", "Draw"), complete(1, 1)).outcome())
                .isEqualTo(PickOutcome.WIN);
        assertThat(grader.grade(snapshot("DRAW", "Draw"), complete(2, 1)).outcome())
                .isEqualTo(PickOutcome.LOSS);
        assertThat(grader.grade(snapshot("MATCH_RESULT", "Draw"), complete(0, 0)).outcome())
                .isEqualTo(PickOutcome.WIN);
    }

    @Test
    void valueBetAwayWin() {
        assertThat(grader.grade(snapshot("VALUE_BET", "Away Win"), complete(0, 2)).outcome())
                .isEqualTo(PickOutcome.WIN);
        assertThat(grader.grade(snapshot("VALUE_BET", "Away Win"), complete(1, 0)).outcome())
                .isEqualTo(PickOutcome.LOSS);
    }

    @Test
    void halfGoalsEngineMarketStrings() {
        CompletedMatch match = complete(2, 1);
        match.setHtHomeGoals(1);
        match.setHtAwayGoals(1);
        assertThat(grader.grade(snapshot("FIRST_HALF_GOALS", "Over 0.5 First Half Goals"), match).outcome())
                .isEqualTo(PickOutcome.WIN);
        assertThat(grader.grade(snapshot("FIRST_HALF_GOALS", "Over 1.5 First Half Goals"), match).outcome())
                .isEqualTo(PickOutcome.WIN);

        match.setSecondHalfHomeGoals(0);
        match.setSecondHalfAwayGoals(0);
        assertThat(grader.grade(snapshot("SECOND_HALF_GOALS", "Over 0.5 Second Half Goals"), match).outcome())
                .isEqualTo(PickOutcome.LOSS);
    }

    @Test
    void teamWinTypesLoseOnDraw() {
        assertThat(grader.grade(snapshot("HOME_AWAY_SPECIALIST", "Arsenal"), complete(1, 1)).outcome())
                .isEqualTo(PickOutcome.LOSS);
        assertThat(grader.grade(snapshot("TOP_VS_BOTTOM", "Chelsea"), complete(0, 2)).outcome())
                .isEqualTo(PickOutcome.WIN);
        assertThat(grader.grade(snapshot("WINNING_FORM_MISMATCH", "Chelsea"), complete(2, 0)).outcome())
                .isEqualTo(PickOutcome.LOSS);
        assertThat(grader.grade(snapshot("LOSING_FORM_MISMATCH", "Arsenal"), complete(3, 1)).outcome())
                .isEqualTo(PickOutcome.WIN);
    }

    @Test
    void cleanSheetAway() {
        RecommendationSnapshot snap = snapshot("CLEAN_SHEET", "Chelsea Clean Sheet");
        assertThat(grader.grade(snap, complete(0, 1)).outcome()).isEqualTo(PickOutcome.WIN);
        assertThat(grader.grade(snap, complete(1, 1)).outcome()).isEqualTo(PickOutcome.LOSS);
    }

    private static RecommendationSnapshot snapshot(String type, String market) {
        return RecommendationSnapshot.builder()
                .fixtureId(1L)
                .type(type)
                .market(market)
                .homeTeamName("Arsenal")
                .awayTeamName("Chelsea")
                .build();
    }

    private static CompletedMatch complete(int home, int away) {
        return CompletedMatch.builder()
                .fixtureId(1L)
                .status("complete")
                .homeGoals(home)
                .awayGoals(away)
                .build();
    }
}
