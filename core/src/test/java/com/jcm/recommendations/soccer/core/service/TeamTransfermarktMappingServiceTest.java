package com.jcm.recommendations.soccer.core.service;

import com.jcm.recommendations.soccer.core.repository.TeamTransfermarktMappingRepository;
import com.jcm.recommendations.soccer.core.transfermarkt.config.TransfermarktProperties;
import com.jcm.recommendations.soccer.domain.TeamTransfermarktMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamTransfermarktMappingServiceTest {

    @Mock
    private TeamTransfermarktMappingRepository mappingRepository;

    private TeamTransfermarktMappingService service;

    @BeforeEach
    void setUp() {
        TransfermarktProperties properties = new TransfermarktProperties();
        properties.setMappingResource("transfermarkt/team-mapping-test.csv");
        service = new TeamTransfermarktMappingService(mappingRepository, properties);
    }

    @Test
    void importMappingsFromClasspath_loadsHighConfidenceRows() {
        when(mappingRepository.findById(10L)).thenReturn(java.util.Optional.empty());
        when(mappingRepository.findById(20L)).thenReturn(java.util.Optional.empty());
        when(mappingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int imported = service.importMappingsFromClasspath(true);

        assertThat(imported).isEqualTo(2);

        ArgumentCaptor<TeamTransfermarktMapping> captor = ArgumentCaptor.forClass(TeamTransfermarktMapping.class);
        verify(mappingRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(TeamTransfermarktMapping::getTeamId)
                .containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    void isEngineUsable_manualMappingAlwaysUsable() {
        TeamTransfermarktMapping mapping = TeamTransfermarktMapping.builder()
                .teamId(1L)
                .transfermarktClubId(99L)
                .matchMethod("MANUAL")
                .confidence("LOW")
                .build();

        assertThat(service.isEngineUsable(mapping)).isTrue();
    }

    @Test
    void isEngineUsable_csvRequiresHighConfidence() {
        TeamTransfermarktMapping high = TeamTransfermarktMapping.builder()
                .teamId(1L)
                .transfermarktClubId(99L)
                .matchMethod("CSV")
                .confidence("HIGH")
                .build();
        TeamTransfermarktMapping low = TeamTransfermarktMapping.builder()
                .teamId(2L)
                .transfermarktClubId(88L)
                .matchMethod("CSV")
                .confidence("LOW")
                .build();

        assertThat(service.isEngineUsable(high)).isTrue();
        assertThat(service.isEngineUsable(low)).isFalse();
    }
}
