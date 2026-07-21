package com.jcm.recommendations.soccer.core.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RefereeDto {

    private Long id;

    @JsonProperty("full_name")
    private String fullName;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    @JsonProperty("known_as")
    private String knownAs;

    @JsonProperty("competition_id")
    private Long competitionId;

    @JsonProperty("appearances_overall")
    private Integer appearancesOverall;

    @JsonProperty("wins_home")
    private Integer winsHome;

    @JsonProperty("wins_away")
    private Integer winsAway;

    @JsonProperty("draws_overall")
    private Integer drawsOverall;

    @JsonProperty("wins_per_home")
    private Double winsPerHome;

    @JsonProperty("wins_per_away")
    private Double winsPerAway;

    @JsonProperty("draws_per")
    private Double drawsPer;

    @JsonProperty("goals_overall")
    private Integer goalsOverall;

    @JsonProperty("goals_home")
    private Integer goalsHome;

    @JsonProperty("goals_away")
    private Integer goalsAway;

    @JsonProperty("goals_per_match_overall")
    private Double goalsPerMatchOverall;

    @JsonProperty("goals_per_match_home")
    private Double goalsPerMatchHome;

    @JsonProperty("goals_per_match_away")
    private Double goalsPerMatchAway;

    @JsonProperty("btts_overall")
    private Integer bttsOverall;

    @JsonProperty("btts_percentage")
    private Double bttsPercentage;

    @JsonProperty("penalties_given_overall")
    private Integer penaltiesGivenOverall;

    @JsonProperty("penalties_given_home")
    private Integer penaltiesGivenHome;

    @JsonProperty("penalties_given_away")
    private Integer penaltiesGivenAway;

    @JsonProperty("penalties_given_per_match_overall")
    private Double penaltiesGivenPerMatchOverall;

    @JsonProperty("penalties_given_percentage_overall")
    private Double penaltiesGivenPercentageOverall;

    @JsonProperty("cards_overall")
    private Integer cardsOverall;

    @JsonProperty("cards_home")
    private Integer cardsHome;

    @JsonProperty("cards_away")
    private Integer cardsAway;

    @JsonProperty("cards_per_match_overall")
    private Double cardsPerMatchOverall;

    @JsonProperty("cards_per_match_home")
    private Double cardsPerMatchHome;

    @JsonProperty("cards_per_match_away")
    private Double cardsPerMatchAway;

    @JsonProperty("yellow_cards_overall")
    private Integer yellowCardsOverall;

    @JsonProperty("red_cards_overall")
    private Integer redCardsOverall;

    @JsonProperty("over05_cards_overall")
    private Integer over05CardsOverall;

    @JsonProperty("over15_cards_overall")
    private Integer over15CardsOverall;

    @JsonProperty("over25_cards_overall")
    private Integer over25CardsOverall;

    @JsonProperty("over35_cards_overall")
    private Integer over35CardsOverall;

    @JsonProperty("over45_cards_overall")
    private Integer over45CardsOverall;

    @JsonProperty("over55_cards_overall")
    private Integer over55CardsOverall;

    @JsonProperty("over65_cards_overall")
    private Integer over65CardsOverall;

    @JsonProperty("over05_cards_percentage_overall")
    private Double over05CardsPercentageOverall;

    @JsonProperty("over15_cards_percentage_overall")
    private Double over15CardsPercentageOverall;

    @JsonProperty("over25_cards_percentage_overall")
    private Double over25CardsPercentageOverall;

    @JsonProperty("over35_cards_percentage_overall")
    private Double over35CardsPercentageOverall;

    @JsonProperty("over45_cards_percentage_overall")
    private Double over45CardsPercentageOverall;

    @JsonProperty("min_per_card_overall")
    private Double minPerCardOverall;

    @JsonProperty("min_per_goal_overall")
    private Double minPerGoalOverall;
}
