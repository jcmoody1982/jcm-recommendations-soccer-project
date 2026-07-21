package com.jcm.recommendations.soccer.core.mapper;

import com.jcm.recommendations.soccer.core.client.dto.TeamDto;
import com.jcm.recommendations.soccer.domain.Team;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import org.springframework.stereotype.Component;

@Component
public class TeamMapper {

    public Team toTeam(TeamDto dto, Long seasonId) {
        if (dto == null) {
            return null;
        }

        return Team.builder()
                .id(dto.getId())
                .name(dto.getName())
                .cleanName(dto.getCleanName())
                .country(dto.getCountry())
                .image(dto.getImage())
                .stadiumName(dto.getStadiumName())
                .seasonId(seasonId)
                .build();
    }

    public TeamSeasonStats toTeamSeasonStats(TeamDto dto, Long seasonId) {
        if (dto == null) {
            return null;
        }

        return TeamSeasonStats.builder()
                .teamId(dto.getId())
                .seasonId(seasonId)
                .matchesPlayed(dto.getMatchesPlayed())
                .points(dto.getPoints())
                .position(dto.getPosition())
                .seasonWinsOverall(dto.getSeasonWinsOverall())
                .seasonWinsHome(dto.getSeasonWinsHome())
                .seasonWinsAway(dto.getSeasonWinsAway())
                .seasonDrawsOverall(dto.getSeasonDrawsOverall())
                .seasonDrawsHome(dto.getSeasonDrawsHome())
                .seasonDrawsAway(dto.getSeasonDrawsAway())
                .seasonLossesOverall(dto.getSeasonLossesOverall())
                .seasonLossesHome(dto.getSeasonLossesHome())
                .seasonLossesAway(dto.getSeasonLossesAway())
                .seasonGoalsOverall(dto.getSeasonGoalsOverall())
                .seasonGoalsHome(dto.getSeasonGoalsHome())
                .seasonGoalsAway(dto.getSeasonGoalsAway())
                .seasonConcededOverall(dto.getSeasonConcededOverall())
                .seasonConcededHome(dto.getSeasonConcededHome())
                .seasonConcededAway(dto.getSeasonConcededAway())
                .seasonGoalDifference(dto.getSeasonGoalDifference())
                .ppgOverall(dto.getPpgOverall())
                .ppgHome(dto.getPpgHome())
                .ppgAway(dto.getPpgAway())
                .seasonBttsOverall(dto.getSeasonBttsOverall())
                .seasonBttsHome(dto.getSeasonBttsHome())
                .seasonBttsAway(dto.getSeasonBttsAway())
                .seasonBttsPercentageOverall(dto.getSeasonBttsPercentageOverall())
                .seasonBttsPercentageHome(dto.getSeasonBttsPercentageHome())
                .seasonBttsPercentageAway(dto.getSeasonBttsPercentageAway())
                .seasonOver15Overall(dto.getSeasonOver15Overall())
                .seasonOver25Overall(dto.getSeasonOver25Overall())
                .seasonOver35Overall(dto.getSeasonOver35Overall())
                .seasonOver15PercentageOverall(dto.getSeasonOver15PercentageOverall())
                .seasonOver25PercentageOverall(dto.getSeasonOver25PercentageOverall())
                .seasonOver35PercentageOverall(dto.getSeasonOver35PercentageOverall())
                .seasonCleanSheetsOverall(dto.getSeasonCleanSheetsOverall())
                .seasonCleanSheetsHome(dto.getSeasonCleanSheetsHome())
                .seasonCleanSheetsAway(dto.getSeasonCleanSheetsAway())
                .seasonFailedToScoreOverall(dto.getSeasonFailedToScoreOverall())
                .seasonFailedToScoreHome(dto.getSeasonFailedToScoreHome())
                .seasonFailedToScoreAway(dto.getSeasonFailedToScoreAway())
                .cornersAvgOverall(dto.getCornersAvgOverall())
                .cornersAvgHome(dto.getCornersAvgHome())
                .cornersAvgAway(dto.getCornersAvgAway())
                .cardsAvgOverall(dto.getCardsAvgOverall())
                .cardsAvgHome(dto.getCardsAvgHome())
                .cardsAvgAway(dto.getCardsAvgAway())
                .build();
    }

    public TeamRecentForm toTeamRecentForm(TeamDto dto) {
        if (dto == null) {
            return null;
        }

        return TeamRecentForm.builder()
                .teamId(dto.getId())
                .competitionId(dto.getCompetitionId())
                .winsOverall(dto.getSeasonWinsOverall())
                .winsHome(dto.getSeasonWinsHome())
                .winsAway(dto.getSeasonWinsAway())
                .drawsOverall(dto.getSeasonDrawsOverall())
                .drawsHome(dto.getSeasonDrawsHome())
                .drawsAway(dto.getSeasonDrawsAway())
                .lossesOverall(dto.getSeasonLossesOverall())
                .lossesHome(dto.getSeasonLossesHome())
                .lossesAway(dto.getSeasonLossesAway())
                .ppgOverall(dto.getPpgOverall())
                .ppgHome(dto.getPpgHome())
                .ppgAway(dto.getPpgAway())
                .goalsOverall(dto.getSeasonGoalsOverall())
                .goalsHome(dto.getSeasonGoalsHome())
                .goalsAway(dto.getSeasonGoalsAway())
                .concededOverall(dto.getSeasonConcededOverall())
                .concededHome(dto.getSeasonConcededHome())
                .concededAway(dto.getSeasonConcededAway())
                .bttsOverall(dto.getSeasonBttsOverall())
                .bttsHome(dto.getSeasonBttsHome())
                .bttsAway(dto.getSeasonBttsAway())
                .bttsPercentageOverall(dto.getSeasonBttsPercentageOverall())
                .bttsPercentageHome(dto.getSeasonBttsPercentageHome())
                .bttsPercentageAway(dto.getSeasonBttsPercentageAway())
                .over15Overall(dto.getSeasonOver15Overall())
                .over25Overall(dto.getSeasonOver25Overall())
                .over35Overall(dto.getSeasonOver35Overall())
                .over15PercentageOverall(dto.getSeasonOver15PercentageOverall())
                .over25PercentageOverall(dto.getSeasonOver25PercentageOverall())
                .over35PercentageOverall(dto.getSeasonOver35PercentageOverall())
                .cleanSheetsOverall(dto.getSeasonCleanSheetsOverall())
                .cleanSheetsHome(dto.getSeasonCleanSheetsHome())
                .cleanSheetsAway(dto.getSeasonCleanSheetsAway())
                .failedToScoreOverall(dto.getSeasonFailedToScoreOverall())
                .failedToScoreHome(dto.getSeasonFailedToScoreHome())
                .failedToScoreAway(dto.getSeasonFailedToScoreAway())
                .cornersAvgOverall(dto.getCornersAvgOverall())
                .cornersAvgHome(dto.getCornersAvgHome())
                .cornersAvgAway(dto.getCornersAvgAway())
                .cardsAvgOverall(dto.getCardsAvgOverall())
                .cardsAvgHome(dto.getCardsAvgHome())
                .cardsAvgAway(dto.getCardsAvgAway())
                .build();
    }
}
