package com.jcm.recommendations.soccer.core.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TeamDto {

    private Long id;
    private String name;
    private String cleanName;
    private String country;
    private String image;

    @JsonProperty("stadium_name")
    private String stadiumName;

    @JsonProperty("competition_id")
    private Long competitionId;

    private Integer matchesPlayed;
    private Integer points;
    private Integer position;

    @JsonProperty("seasonWinsNum_overall")
    private Integer seasonWinsOverall;
    @JsonProperty("seasonWinsNum_home")
    private Integer seasonWinsHome;
    @JsonProperty("seasonWinsNum_away")
    private Integer seasonWinsAway;

    @JsonProperty("seasonDrawsNum_overall")
    private Integer seasonDrawsOverall;
    @JsonProperty("seasonDrawsNum_home")
    private Integer seasonDrawsHome;
    @JsonProperty("seasonDrawsNum_away")
    private Integer seasonDrawsAway;

    @JsonProperty("seasonLossesNum_overall")
    private Integer seasonLossesOverall;
    @JsonProperty("seasonLossesNum_home")
    private Integer seasonLossesHome;
    @JsonProperty("seasonLossesNum_away")
    private Integer seasonLossesAway;

    @JsonProperty("seasonGoals_overall")
    private Integer seasonGoalsOverall;
    @JsonProperty("seasonGoals_home")
    private Integer seasonGoalsHome;
    @JsonProperty("seasonGoals_away")
    private Integer seasonGoalsAway;

    @JsonProperty("seasonConceded_overall")
    private Integer seasonConcededOverall;
    @JsonProperty("seasonConceded_home")
    private Integer seasonConcededHome;
    @JsonProperty("seasonConceded_away")
    private Integer seasonConcededAway;

    @JsonProperty("seasonGoalDifference_overall")
    private Integer seasonGoalDifference;

    @JsonProperty("seasonPPG_overall")
    private Double ppgOverall;
    @JsonProperty("seasonPPG_home")
    private Double ppgHome;
    @JsonProperty("seasonPPG_away")
    private Double ppgAway;

    @JsonProperty("seasonBTTS_overall")
    private Integer seasonBttsOverall;
    @JsonProperty("seasonBTTS_home")
    private Integer seasonBttsHome;
    @JsonProperty("seasonBTTS_away")
    private Integer seasonBttsAway;

    @JsonProperty("seasonBTTSPercentage_overall")
    private Double seasonBttsPercentageOverall;
    @JsonProperty("seasonBTTSPercentage_home")
    private Double seasonBttsPercentageHome;
    @JsonProperty("seasonBTTSPercentage_away")
    private Double seasonBttsPercentageAway;

    @JsonProperty("seasonOver15Num_overall")
    private Integer seasonOver15Overall;
    @JsonProperty("seasonOver25Num_overall")
    private Integer seasonOver25Overall;
    @JsonProperty("seasonOver35Num_overall")
    private Integer seasonOver35Overall;

    @JsonProperty("seasonOver15Percentage_overall")
    private Double seasonOver15PercentageOverall;
    @JsonProperty("seasonOver25Percentage_overall")
    private Double seasonOver25PercentageOverall;
    @JsonProperty("seasonOver35Percentage_overall")
    private Double seasonOver35PercentageOverall;

    @JsonProperty("seasonCS_overall")
    private Integer seasonCleanSheetsOverall;
    @JsonProperty("seasonCS_home")
    private Integer seasonCleanSheetsHome;
    @JsonProperty("seasonCS_away")
    private Integer seasonCleanSheetsAway;

    @JsonProperty("seasonFTS_overall")
    private Integer seasonFailedToScoreOverall;
    @JsonProperty("seasonFTS_home")
    private Integer seasonFailedToScoreHome;
    @JsonProperty("seasonFTS_away")
    private Integer seasonFailedToScoreAway;

    @JsonProperty("cornersAVG_overall")
    private Double cornersAvgOverall;
    @JsonProperty("cornersAVG_home")
    private Double cornersAvgHome;
    @JsonProperty("cornersAVG_away")
    private Double cornersAvgAway;

    @JsonProperty("cardsAVG_overall")
    private Double cardsAvgOverall;
    @JsonProperty("cardsAVG_home")
    private Double cardsAvgHome;
    @JsonProperty("cardsAVG_away")
    private Double cardsAvgAway;
}
