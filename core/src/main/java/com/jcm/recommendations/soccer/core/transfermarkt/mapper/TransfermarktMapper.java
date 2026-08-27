package com.jcm.recommendations.soccer.core.transfermarkt.mapper;

import com.jcm.recommendations.soccer.core.transfermarkt.dto.ClubSquadDto;
import com.jcm.recommendations.soccer.domain.TeamSquadProfile;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class TransfermarktMapper {

    public TeamSquadProfile toTeamSquadProfile(
            Long footyStatsTeamId,
            ClubSquadDto dto,
            boolean engineUsable,
            Instant fetchedAt) {
        if (dto == null || footyStatsTeamId == null) {
            return null;
        }

        return TeamSquadProfile.builder()
                .teamId(footyStatsTeamId)
                .transfermarktClubId(dto.getClubId())
                .totalMarketValueEur(dto.getTotalMarketValueEur())
                .avgMarketValueEur(dto.getAvgMarketValueEur())
                .squadSize(dto.getSquadSize())
                .avgAge(dto.getAvgAge())
                .foreignPlayers(dto.getForeignPlayers())
                .seasonLabel(dto.getSeason())
                .engineUsable(engineUsable)
                .fetchedAt(fetchedAt)
                .build();
    }
}
