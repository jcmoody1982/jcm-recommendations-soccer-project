package com.jcm.recommendations.soccer.web.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CompetitionDto {
    private Long leagueId;
    private String name;
    private String logoUrl;
    private int fixtureCount;
    private List<FixtureSummaryDto> fixtures;
}
