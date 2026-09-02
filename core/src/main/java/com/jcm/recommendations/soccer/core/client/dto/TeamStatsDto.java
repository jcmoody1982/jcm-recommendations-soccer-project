package com.jcm.recommendations.soccer.core.client.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TeamStatsDto {

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

    @JsonProperty("seasonScoredNum_overall")
    @JsonAlias({"seasonGoals_overall"})
    private Integer seasonGoalsOverall;
    @JsonProperty("seasonScoredNum_home")
    private Integer seasonGoalsHome;
    @JsonProperty("seasonScoredNum_away")
    private Integer seasonGoalsAway;

    @JsonProperty("seasonConcededNum_overall")
    @JsonAlias({"seasonConceded_overall"})
    private Integer seasonConcededOverall;
    @JsonProperty("seasonConcededNum_home")
    private Integer seasonConcededHome;
    @JsonProperty("seasonConcededNum_away")
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

    @JsonProperty("scoredAVG_overall")
    @JsonAlias({"seasonScoredAVG_overall"})
    private Double scoredAvgOverall;
    @JsonProperty("scoredAVG_home")
    @JsonAlias({"seasonScoredAVG_home"})
    private Double scoredAvgHome;
    @JsonProperty("scoredAVG_away")
    @JsonAlias({"seasonScoredAVG_away"})
    private Double scoredAvgAway;

    @JsonProperty("concededAVG_overall")
    @JsonAlias({"seasonConcededAVG_overall"})
    private Double concededAvgOverall;
    @JsonProperty("concededAVG_home")
    @JsonAlias({"seasonConcededAVG_home"})
    private Double concededAvgHome;
    @JsonProperty("concededAVG_away")
    @JsonAlias({"seasonConcededAVG_away"})
    private Double concededAvgAway;

    @JsonProperty("foulsAVG_overall")
    private Double foulsAvgOverall;
    @JsonProperty("foulsAVG_home")
    private Double foulsAvgHome;
    @JsonProperty("foulsAVG_away")
    private Double foulsAvgAway;

    @JsonProperty("scoredAVGHT_overall")
    private Double scoredAvgHtOverall;
    @JsonProperty("scoredAVGHT_home")
    private Double scoredAvgHtHome;
    @JsonProperty("scoredAVGHT_away")
    private Double scoredAvgHtAway;

    @JsonProperty("concededAVGHT_overall")
    private Double concededAvgHtOverall;
    @JsonProperty("concededAVGHT_home")
    private Double concededAvgHtHome;
    @JsonProperty("concededAVGHT_away")
    private Double concededAvgHtAway;

    @JsonProperty("scored_2hg_avg_overall")
    private Double scoredAvg2hOverall;
    @JsonProperty("scored_2hg_avg_home")
    private Double scoredAvg2hHome;
    @JsonProperty("scored_2hg_avg_away")
    private Double scoredAvg2hAway;

    @JsonProperty("conceded_2hg_avg_overall")
    private Double concededAvg2hOverall;
    @JsonProperty("conceded_2hg_avg_home")
    private Double concededAvg2hHome;
    @JsonProperty("conceded_2hg_avg_away")
    private Double concededAvg2hAway;

    @JsonProperty("btts_fhg_percentage_overall")
    private Double bttsFhgPercentageOverall;
    @JsonProperty("btts_fhg_percentage_home")
    private Double bttsFhgPercentageHome;
    @JsonProperty("btts_fhg_percentage_away")
    private Double bttsFhgPercentageAway;

    @JsonProperty("btts_2hg_percentage_overall")
    private Double btts2hgPercentageOverall;
    @JsonProperty("btts_2hg_percentage_home")
    private Double btts2hgPercentageHome;
    @JsonProperty("btts_2hg_percentage_away")
    private Double btts2hgPercentageAway;

    @JsonProperty("shotsAVG_overall")
    private Double shotsAvgOverall;
    @JsonProperty("shotsAVG_home")
    private Double shotsAvgHome;
    @JsonProperty("shotsAVG_away")
    private Double shotsAvgAway;

    // Expected Goals (xG) data
    @JsonProperty("xg_for_avg_overall")
    private Double xgForAvgOverall;
    @JsonProperty("xg_for_avg_home")
    private Double xgForAvgHome;
    @JsonProperty("xg_for_avg_away")
    private Double xgForAvgAway;

    @JsonProperty("xg_against_avg_overall")
    private Double xgAgainstAvgOverall;
    @JsonProperty("xg_against_avg_home")
    private Double xgAgainstAvgHome;
    @JsonProperty("xg_against_avg_away")
    private Double xgAgainstAvgAway;

    @JsonProperty("seasonMatchesPlayed_overall")
    private Integer matchesPlayed;
    @JsonProperty("seasonMatchesPlayed_home")
    private Integer matchesPlayedHome;
    @JsonProperty("seasonMatchesPlayed_away")
    private Integer matchesPlayedAway;

    private Integer points;

    @JsonProperty("leaguePosition_overall")
    @JsonAlias({"table_position"})
    private Integer position;

    /**
     * The feed carries points-per-game but no season points total, so rebuild it from results.
     */
    public Integer getPoints() {
        if (points != null) {
            return points;
        }
        if (seasonWinsOverall == null || seasonDrawsOverall == null) {
            return null;
        }
        return (3 * seasonWinsOverall) + seasonDrawsOverall;
    }
}
