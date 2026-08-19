package com.jcm.recommendations.soccer.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "player_season_stats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"playerId", "seasonId"})
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerSeasonStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long playerId;
    private Long seasonId;
    private Long clubTeamId;
    private Long clubTeam2Id;

    private String knownAs;
    private String fullName;
    private String position;

    private Integer minutesPlayedOverall;
    private Integer minutesPlayedHome;
    private Integer minutesPlayedAway;
    private Integer appearancesOverall;
    private Integer appearancesHome;
    private Integer appearancesAway;
    private Integer minPerMatch;

    private Integer goalsOverall;
    private Integer goalsHome;
    private Integer goalsAway;
    private Double goalsPer90Overall;
    private Double goalsPer90Home;
    private Double goalsPer90Away;
    private Integer penaltyGoals;

    private Integer assistsOverall;
    private Integer assistsHome;
    private Integer assistsAway;
    private Double assistsPer90Overall;

    private Integer rankInClubTopScorer;
}
