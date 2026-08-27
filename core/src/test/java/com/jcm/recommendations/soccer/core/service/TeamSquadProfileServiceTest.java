package com.jcm.recommendations.soccer.core.service;

import com.jcm.recommendations.soccer.core.repository.TeamSquadProfileRepository;
import com.jcm.recommendations.soccer.core.transfermarkt.client.TransfermarktClient;
import com.jcm.recommendations.soccer.core.transfermarkt.config.TransfermarktProperties;
import com.jcm.recommendations.soccer.core.transfermarkt.dto.ClubSquadDto;
import com.jcm.recommendations.soccer.core.transfermarkt.mapper.TransfermarktMapper;
import com.jcm.recommendations.soccer.domain.TeamSquadProfile;
import com.jcm.recommendations.soccer.domain.TeamTransfermarktMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamSquadProfileServiceTest {

    @Mock
    private TransfermarktClient transfermarktClient;
    @Mock
    private TeamSquadProfileRepository squadProfileRepository;
    @Mock
    private TeamTransfermarktMappingService mappingService;

    private TransfermarktProperties properties;
    private TeamSquadProfileService service;

    @BeforeEach
    void setUp() {
        properties = new TransfermarktProperties();
        properties.setEnabled(true);
        properties.setFreshnessDays(7);
        properties.setRateLimitMs(0L);
        service = new TeamSquadProfileService(
                transfermarktClient,
                properties,
                squadProfileRepository,
                mappingService,
                new TransfermarktMapper());
    }

    @Test
    void isFresh_withinFreshnessWindow() {
        Instant recent = Instant.now().minus(2, ChronoUnit.DAYS);
        assertThat(service.isFresh(recent)).isTrue();
    }

    @Test
    void sync_skipsWhenDisabled() {
        when(transfermarktClient.isEnabled()).thenReturn(false);

        TeamSquadProfileService.SyncResult result = service.syncSquadProfilesForTeams(List.of(1L));

        assertThat(result.synced()).isZero();
        verifyNoInteractions(mappingService);
    }

    @Test
    void sync_persistsWhenMappedAndStale() {
        when(transfermarktClient.isEnabled()).thenReturn(true);
        when(mappingService.findMapping(5L)).thenReturn(Optional.of(TeamTransfermarktMapping.builder()
                .teamId(5L)
                .transfermarktClubId(11L)
                .matchMethod("MANUAL")
                .confidence("HIGH")
                .build()));
        when(squadProfileRepository.findById(5L)).thenReturn(Optional.empty());
        when(mappingService.isEngineUsable(any())).thenReturn(true);

        ClubSquadDto dto = new ClubSquadDto();
        dto.setClubId(11L);
        dto.setTotalMarketValueEur(500_000_000L);
        dto.setAvgMarketValueEur(20_000_000L);
        dto.setSquadSize(25);
        when(transfermarktClient.fetchClubSquad(eq(11L), any())).thenReturn(Optional.of(dto));
        when(squadProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TeamSquadProfileService.SyncResult result = service.syncSquadProfilesForTeams(List.of(5L));

        assertThat(result.synced()).isEqualTo(1);

        ArgumentCaptor<TeamSquadProfile> captor = ArgumentCaptor.forClass(TeamSquadProfile.class);
        verify(squadProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getTeamId()).isEqualTo(5L);
        assertThat(captor.getValue().getTotalMarketValueEur()).isEqualTo(500_000_000L);
        assertThat(captor.getValue().getEngineUsable()).isTrue();
    }
}
