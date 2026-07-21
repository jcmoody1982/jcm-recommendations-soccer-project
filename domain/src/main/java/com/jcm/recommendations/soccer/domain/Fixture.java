package com.jcm.recommendations.soccer.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fixture")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Fixture {

    @Id
    private Long id;

    private Long seasonId;
    private Long competitionId;

    private Long homeTeamId;
    private String homeTeamName;

    private Long awayTeamId;
    private String awayTeamName;

    private Long dateUnix;

    private String status;

    private Integer gameWeek;

    private String stadiumName;
    private String stadiumLocation;

    private Long refereeId;
    private String refereeName;

    private Integer homeGoalCount;
    private Integer awayGoalCount;
    private Integer totalGoalCount;
}
