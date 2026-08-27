package com.jcm.recommendations.soccer.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "team_squad_profile")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamSquadProfile {

    @Id
    private Long teamId;

    private Long transfermarktClubId;

    private Long totalMarketValueEur;
    private Long avgMarketValueEur;

    private Integer squadSize;
    private Double avgAge;
    private Integer foreignPlayers;

    private String seasonLabel;

    /** When false, engines ignore this row (e.g. unverified TM mapping). */
    private Boolean engineUsable;

    private Instant fetchedAt;
}
