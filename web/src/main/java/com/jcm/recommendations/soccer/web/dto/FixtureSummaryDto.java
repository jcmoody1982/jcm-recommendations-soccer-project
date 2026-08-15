package com.jcm.recommendations.soccer.web.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class FixtureSummaryDto {
    private Long fixtureId;
    private String homeTeam;
    private String awayTeam;
    private Instant matchDate;
    /** Unix seconds — preferred for timezone-safe early-kickoff checks on the site. */
    private Long matchDateUnix;
}
