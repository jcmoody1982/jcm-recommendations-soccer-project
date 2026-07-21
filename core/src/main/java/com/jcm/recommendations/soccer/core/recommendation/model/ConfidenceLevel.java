package com.jcm.recommendations.soccer.core.recommendation.model;

public enum ConfidenceLevel {
    STRONG("Strong", 3),
    MODERATE("Moderate", 2),
    WEAK("Weak", 1);

    private final String displayName;
    private final int weight;

    ConfidenceLevel(String displayName, int weight) {
        this.displayName = displayName;
        this.weight = weight;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getWeight() {
        return weight;
    }
}
