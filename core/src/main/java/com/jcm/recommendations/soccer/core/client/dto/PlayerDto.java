package com.jcm.recommendations.soccer.core.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlayerDto {

    private Long id;

    @JsonProperty("known_as")
    private String knownAs;

    @JsonProperty("full_name")
    private String fullName;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    private String position;
    private String nationality;

    @JsonProperty("competition_id")
    private Long competitionId;

    @JsonProperty("club_team_id")
    private Long clubTeamId;

    @JsonProperty("club_team_2_id")
    private Long clubTeam2Id;

    @JsonProperty("minutes_played_overall")
    private Integer minutesPlayedOverall;

    @JsonProperty("minutes_played_home")
    private Integer minutesPlayedHome;

    @JsonProperty("minutes_played_away")
    private Integer minutesPlayedAway;

    @JsonProperty("appearances_overall")
    private Integer appearancesOverall;

    @JsonProperty("appearances_home")
    private Integer appearancesHome;

    @JsonProperty("appearances_away")
    private Integer appearancesAway;

    @JsonProperty("min_per_match")
    private Integer minPerMatch;

    @JsonProperty("goals_overall")
    private Integer goalsOverall;

    @JsonProperty("goals_home")
    private Integer goalsHome;

    @JsonProperty("goals_away")
    private Integer goalsAway;

    @JsonProperty("goals_per_90_overall")
    private Double goalsPer90Overall;

    @JsonProperty("goals_per_90_home")
    private Double goalsPer90Home;

    @JsonProperty("goals_per_90_away")
    private Double goalsPer90Away;

    @JsonProperty("penalty_goals")
    private Integer penaltyGoals;

    @JsonProperty("assists_overall")
    private Integer assistsOverall;

    @JsonProperty("assists_home")
    private Integer assistsHome;

    @JsonProperty("assists_away")
    private Integer assistsAway;

    @JsonProperty("assists_per_90_overall")
    private Double assistsPer90Overall;

    @JsonProperty("rank_in_club_top_scorer")
    private Integer rankInClubTopScorer;
}
