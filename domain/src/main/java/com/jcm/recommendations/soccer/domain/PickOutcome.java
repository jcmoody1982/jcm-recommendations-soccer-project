package com.jcm.recommendations.soccer.domain;

public enum PickOutcome {
    PENDING,
    WIN,
    LOSS,
    VOID,
    UNSUPPORTED;

    public boolean isTerminal() {
        return this != PENDING;
    }
}
