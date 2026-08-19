package com.jcm.recommendations.soccer.core.mapper;

import com.jcm.recommendations.soccer.core.client.dto.PlayerDto;
import com.jcm.recommendations.soccer.domain.Player;
import com.jcm.recommendations.soccer.domain.PlayerSeasonStats;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerMapperTest {

    private final PlayerMapper mapper = new PlayerMapper();

    @Test
    void mapsIdentityAndDropsSentinelClubIds() {
        PlayerDto dto = new PlayerDto();
        dto.setId(55L);
        dto.setKnownAs("Salah");
        dto.setFullName("Mohamed Salah");
        dto.setPosition("Forward");
        dto.setClubTeamId(10L);
        dto.setClubTeam2Id(-1L);
        dto.setGoalsPer90Overall(0.71);
        dto.setAssistsPer90Overall(0.28);
        dto.setRankInClubTopScorer(-1);

        Player player = mapper.toPlayer(dto, 100L);
        PlayerSeasonStats stats = mapper.toSeasonStats(dto, 100L);

        assertThat(player.getId()).isEqualTo(55L);
        assertThat(player.getKnownAs()).isEqualTo("Salah");
        assertThat(player.getSeasonId()).isEqualTo(100L);
        assertThat(stats.getPlayerId()).isEqualTo(55L);
        assertThat(stats.getClubTeamId()).isEqualTo(10L);
        assertThat(stats.getClubTeam2Id()).isNull();
        assertThat(stats.getRankInClubTopScorer()).isNull();
        assertThat(stats.getGoalsPer90Overall()).isEqualTo(0.71);
    }
}
