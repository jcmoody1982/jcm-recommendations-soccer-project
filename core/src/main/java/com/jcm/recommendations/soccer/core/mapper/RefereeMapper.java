package com.jcm.recommendations.soccer.core.mapper;

import com.jcm.recommendations.soccer.core.client.dto.RefereeDto;
import com.jcm.recommendations.soccer.domain.Referee;
import com.jcm.recommendations.soccer.domain.RefereeStats;
import org.springframework.stereotype.Component;

@Component
public class RefereeMapper {

    public Referee toReferee(RefereeDto dto, Long seasonId) {
        if (dto == null) {
            return null;
        }

        return Referee.builder()
                .id(dto.getId())
                .fullName(dto.getFullName())
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .knownAs(dto.getKnownAs())
                .seasonId(seasonId)
                .build();
    }

    public RefereeStats toRefereeStats(RefereeDto dto, Long seasonId) {
        if (dto == null) {
            return null;
        }

        return RefereeStats.builder()
                .refereeId(dto.getId())
                .seasonId(seasonId)
                .appearancesOverall(dto.getAppearancesOverall())
                .winsHome(dto.getWinsHome())
                .winsAway(dto.getWinsAway())
                .drawsOverall(dto.getDrawsOverall())
                .winsPerHome(dto.getWinsPerHome())
                .winsPerAway(dto.getWinsPerAway())
                .drawsPer(dto.getDrawsPer())
                .goalsOverall(dto.getGoalsOverall())
                .goalsHome(dto.getGoalsHome())
                .goalsAway(dto.getGoalsAway())
                .goalsPerMatchOverall(dto.getGoalsPerMatchOverall())
                .goalsPerMatchHome(dto.getGoalsPerMatchHome())
                .goalsPerMatchAway(dto.getGoalsPerMatchAway())
                .bttsOverall(dto.getBttsOverall())
                .bttsPercentage(dto.getBttsPercentage())
                .penaltiesGivenOverall(dto.getPenaltiesGivenOverall())
                .penaltiesGivenHome(dto.getPenaltiesGivenHome())
                .penaltiesGivenAway(dto.getPenaltiesGivenAway())
                .penaltiesGivenPerMatchOverall(dto.getPenaltiesGivenPerMatchOverall())
                .penaltiesGivenPercentageOverall(dto.getPenaltiesGivenPercentageOverall())
                .cardsOverall(dto.getCardsOverall())
                .cardsHome(dto.getCardsHome())
                .cardsAway(dto.getCardsAway())
                .cardsPerMatchOverall(dto.getCardsPerMatchOverall())
                .cardsPerMatchHome(dto.getCardsPerMatchHome())
                .cardsPerMatchAway(dto.getCardsPerMatchAway())
                .yellowCardsOverall(dto.getYellowCardsOverall())
                .redCardsOverall(dto.getRedCardsOverall())
                .over05CardsOverall(dto.getOver05CardsOverall())
                .over15CardsOverall(dto.getOver15CardsOverall())
                .over25CardsOverall(dto.getOver25CardsOverall())
                .over35CardsOverall(dto.getOver35CardsOverall())
                .over45CardsOverall(dto.getOver45CardsOverall())
                .over55CardsOverall(dto.getOver55CardsOverall())
                .over65CardsOverall(dto.getOver65CardsOverall())
                .over05CardsPercentageOverall(dto.getOver05CardsPercentageOverall())
                .over15CardsPercentageOverall(dto.getOver15CardsPercentageOverall())
                .over25CardsPercentageOverall(dto.getOver25CardsPercentageOverall())
                .over35CardsPercentageOverall(dto.getOver35CardsPercentageOverall())
                .over45CardsPercentageOverall(dto.getOver45CardsPercentageOverall())
                .minPerCardOverall(dto.getMinPerCardOverall())
                .minPerGoalOverall(dto.getMinPerGoalOverall())
                .build();
    }
}
