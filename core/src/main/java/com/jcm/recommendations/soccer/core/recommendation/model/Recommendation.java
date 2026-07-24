package com.jcm.recommendations.soccer.core.recommendation.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class Recommendation {
    
    private Long fixtureId;
    private Long homeTeamId;
    private Long awayTeamId;
    private String homeTeamName;
    private String awayTeamName;
    private Long matchDateUnix;
    
    private Long leagueId;
    private String leagueName;
    private String leagueImage;
    
    private RecommendationType type;
    private ConfidenceLevel confidence;
    private double score;
    private String market;
    private Double odds;
    private String description;
    
    private Map<String, Object> factors;
    
    private Instant generatedAt;
    
    public boolean isStrong() {
        return confidence == ConfidenceLevel.STRONG;
    }
    
    public boolean isModerate() {
        return confidence == ConfidenceLevel.MODERATE;
    }
    
    public boolean isActionable() {
        return confidence != ConfidenceLevel.WEAK;
    }
}
