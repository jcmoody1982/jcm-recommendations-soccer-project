package com.jcm.recommendations.soccer.core.recommendation.model;

public enum RecommendationType {
    BTTS("Both Teams To Score", "UC-005"),
    OVER_GOALS("Over Goals", "UC-006"),
    UNDER_GOALS("Under Goals", "UC-007"),
    BOOKING_POINTS("Booking Points", "UC-008"),
    VALUE_BET("Value Bet", "UC-009"),
    WINNING_FORM_MISMATCH("Winning Form Mismatch", "UC-010"),
    LOSING_FORM_MISMATCH("Losing Form Mismatch", "UC-011"),
    OVER_CORNERS("Over Corners", "UC-012"),
    UNDER_CORNERS("Under Corners", "UC-013"),
    CLEAN_SHEET("Clean Sheet", "UC-014"),
    FIRST_HALF_GOALS("First Half Goals", "UC-015"),
    SECOND_HALF_GOALS("Second Half Goals", "UC-016"),
    MATCH_RESULT("Match Result", "UC-017"),
    HOME_AWAY_SPECIALIST("Home/Away Specialist", "UC-018"),
    DRAW("Draw", "UC-019");

    private final String displayName;
    private final String useCaseId;

    RecommendationType(String displayName, String useCaseId) {
        this.displayName = displayName;
        this.useCaseId = useCaseId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUseCaseId() {
        return useCaseId;
    }
}
