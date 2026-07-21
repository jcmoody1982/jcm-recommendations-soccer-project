package com.jcm.recommendations.soccer.core.mapper;

import com.jcm.recommendations.soccer.core.client.dto.MatchDto;
import com.jcm.recommendations.soccer.domain.Fixture;
import com.jcm.recommendations.soccer.domain.FixtureOdds;
import com.jcm.recommendations.soccer.domain.FixturePotentials;
import org.springframework.stereotype.Component;

@Component
public class FixtureMapper {

    public Fixture toFixture(MatchDto dto, Long seasonId) {
        if (dto == null) {
            return null;
        }

        return Fixture.builder()
                .id(dto.getId())
                .seasonId(seasonId)
                .competitionId(dto.getCompetitionId())
                .homeTeamId(dto.getHomeId())
                .homeTeamName(dto.getHomeName())
                .awayTeamId(dto.getAwayId())
                .awayTeamName(dto.getAwayName())
                .dateUnix(dto.getDateUnix())
                .status(dto.getStatus())
                .gameWeek(dto.getGameWeek())
                .stadiumName(dto.getStadiumName())
                .stadiumLocation(dto.getStadiumLocation())
                .refereeId(dto.getRefereeId())
                .refereeName(dto.getReferee())
                .homeGoalCount(dto.getHomeGoalCount())
                .awayGoalCount(dto.getAwayGoalCount())
                .totalGoalCount(dto.getTotalGoalCount())
                .build();
    }

    public FixtureOdds toFixtureOdds(MatchDto dto) {
        if (dto == null) {
            return null;
        }

        return FixtureOdds.builder()
                .fixtureId(dto.getId())
                .oddsFt1(dto.getOddsFt1())
                .oddsFtX(dto.getOddsFtX())
                .oddsFt2(dto.getOddsFt2())
                .oddsFtOver05(dto.getOddsFtOver05())
                .oddsFtOver15(dto.getOddsFtOver15())
                .oddsFtOver25(dto.getOddsFtOver25())
                .oddsFtOver35(dto.getOddsFtOver35())
                .oddsFtOver45(dto.getOddsFtOver45())
                .oddsFtUnder05(dto.getOddsFtUnder05())
                .oddsFtUnder15(dto.getOddsFtUnder15())
                .oddsFtUnder25(dto.getOddsFtUnder25())
                .oddsFtUnder35(dto.getOddsFtUnder35())
                .oddsFtUnder45(dto.getOddsFtUnder45())
                .oddsBttsYes(dto.getOddsBttsYes())
                .oddsBttsNo(dto.getOddsBttsNo())
                .build();
    }

    public FixturePotentials toFixturePotentials(MatchDto dto) {
        if (dto == null) {
            return null;
        }

        return FixturePotentials.builder()
                .fixtureId(dto.getId())
                .bttsPotential(dto.getBttsPotential())
                .o15Potential(dto.getO15Potential())
                .o25Potential(dto.getO25Potential())
                .o35Potential(dto.getO35Potential())
                .o45Potential(dto.getO45Potential())
                .o05HtPotential(dto.getO05HtPotential())
                .o15HtPotential(dto.getO15HtPotential())
                .u15Potential(dto.getU15Potential())
                .avgPotential(dto.getAvgPotential())
                .cornersPotential(dto.getCornersPotential())
                .cornersO85Potential(dto.getCornersO85Potential())
                .cornersO95Potential(dto.getCornersO95Potential())
                .cornersO105Potential(dto.getCornersO105Potential())
                .cardsPotential(dto.getCardsPotential())
                .offsidesPotential(dto.getOffsidesPotential())
                .build();
    }
}
