package com.jcm.recommendations.soccer.core.results.settlement;

import com.jcm.recommendations.soccer.domain.PickOutcome;

public record GradeResult(PickOutcome outcome, String detail) {

    public static GradeResult win() {
        return new GradeResult(PickOutcome.WIN, null);
    }

    public static GradeResult loss() {
        return new GradeResult(PickOutcome.LOSS, null);
    }

    public static GradeResult voided(String detail) {
        return new GradeResult(PickOutcome.VOID, detail);
    }

    public static GradeResult pending(String detail) {
        return new GradeResult(PickOutcome.PENDING, detail);
    }

    public static GradeResult unsupported(String detail) {
        return new GradeResult(PickOutcome.UNSUPPORTED, detail);
    }

    public boolean isResolved() {
        return outcome != PickOutcome.PENDING;
    }
}
