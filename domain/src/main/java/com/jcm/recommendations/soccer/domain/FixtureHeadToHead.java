package com.jcm.recommendations.soccer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "fixture_head_to_head")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FixtureHeadToHead {

    @Id
    private Long fixtureId;

    private Integer previousMeetings;
    private Integer homeWins;
    private Integer awayWins;
    private Integer draws;

    @Column(length = 4000)
    private String previousMatchIdsJson;

    private Instant fetchedAt;
}
