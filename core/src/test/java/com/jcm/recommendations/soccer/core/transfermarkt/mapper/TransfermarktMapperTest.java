package com.jcm.recommendations.soccer.core.transfermarkt.mapper;

import com.jcm.recommendations.soccer.core.transfermarkt.dto.ClubSquadDto;
import com.jcm.recommendations.soccer.domain.TeamSquadProfile;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TransfermarktMapperTest {

    private final TransfermarktMapper mapper = new TransfermarktMapper();

    @Test
    void mapsClubSquadToTeamSquadProfile() {
        ClubSquadDto dto = new ClubSquadDto();
        dto.setClubId(11L);
        dto.setClubName("Arsenal FC");
        dto.setTotalMarketValueEur(929_000_000L);
        dto.setAvgMarketValueEur(35_700_000L);
        dto.setSquadSize(26);
        dto.setAvgAge(26.5);
        dto.setForeignPlayers(17);
        dto.setSeason("2025");

        Instant fetchedAt = Instant.parse("2026-08-26T12:00:00Z");
        TeamSquadProfile profile = mapper.toTeamSquadProfile(42L, dto, true, fetchedAt);

        assertThat(profile.getTeamId()).isEqualTo(42L);
        assertThat(profile.getTransfermarktClubId()).isEqualTo(11L);
        assertThat(profile.getTotalMarketValueEur()).isEqualTo(929_000_000L);
        assertThat(profile.getEngineUsable()).isTrue();
        assertThat(profile.getFetchedAt()).isEqualTo(fetchedAt);
    }
}
