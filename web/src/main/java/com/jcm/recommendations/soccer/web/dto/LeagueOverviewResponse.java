package com.jcm.recommendations.soccer.web.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class LeagueOverviewResponse {
    private List<CountryGroupDto> countries;
    private int totalFixtures;
    private Instant lastUpdated;
}
