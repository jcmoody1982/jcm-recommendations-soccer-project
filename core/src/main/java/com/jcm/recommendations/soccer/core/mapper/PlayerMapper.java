package com.jcm.recommendations.soccer.core.mapper;

import com.jcm.recommendations.soccer.core.client.dto.PlayerDto;
import com.jcm.recommendations.soccer.domain.Player;
import com.jcm.recommendations.soccer.domain.PlayerSeasonStats;
import org.springframework.stereotype.Component;

@Component
public class PlayerMapper {

    public Player toPlayer(PlayerDto dto, Long seasonId) {
        if (dto == null) {
            return null;
        }
        return Player.builder()
                .id(dto.getId())
                .knownAs(dto.getKnownAs())
                .fullName(dto.getFullName())
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .position(dto.getPosition())
                .nationality(dto.getNationality())
                .seasonId(seasonId)
                .build();
    }

    public PlayerSeasonStats toSeasonStats(PlayerDto dto, Long seasonId) {
        if (dto == null) {
            return null;
        }
        return PlayerSeasonStats.builder()
                .playerId(dto.getId())
                .seasonId(seasonId)
                .clubTeamId(positiveId(dto.getClubTeamId()))
                .clubTeam2Id(positiveId(dto.getClubTeam2Id()))
                .knownAs(dto.getKnownAs())
                .fullName(dto.getFullName())
                .position(dto.getPosition())
                .minutesPlayedOverall(dto.getMinutesPlayedOverall())
                .minutesPlayedHome(dto.getMinutesPlayedHome())
                .minutesPlayedAway(dto.getMinutesPlayedAway())
                .appearancesOverall(dto.getAppearancesOverall())
                .appearancesHome(dto.getAppearancesHome())
                .appearancesAway(dto.getAppearancesAway())
                .minPerMatch(dto.getMinPerMatch())
                .goalsOverall(dto.getGoalsOverall())
                .goalsHome(dto.getGoalsHome())
                .goalsAway(dto.getGoalsAway())
                .goalsPer90Overall(dto.getGoalsPer90Overall())
                .goalsPer90Home(dto.getGoalsPer90Home())
                .goalsPer90Away(dto.getGoalsPer90Away())
                .penaltyGoals(dto.getPenaltyGoals())
                .assistsOverall(dto.getAssistsOverall())
                .assistsHome(dto.getAssistsHome())
                .assistsAway(dto.getAssistsAway())
                .assistsPer90Overall(dto.getAssistsPer90Overall())
                .rankInClubTopScorer(positiveRank(dto.getRankInClubTopScorer()))
                .build();
    }

    private static Long positiveId(Long value) {
        return value != null && value > 0 ? value : null;
    }

    private static Integer positiveRank(Integer value) {
        return value != null && value > 0 ? value : null;
    }
}
