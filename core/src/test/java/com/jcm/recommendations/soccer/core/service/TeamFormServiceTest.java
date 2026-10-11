package com.jcm.recommendations.soccer.core.service;

import com.jcm.recommendations.soccer.core.client.FootyStatsApiClient;
import com.jcm.recommendations.soccer.core.client.dto.TeamDto;
import com.jcm.recommendations.soccer.core.mapper.TeamMapper;
import com.jcm.recommendations.soccer.core.repository.TeamRecentFormRepository;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamFormServiceTest {

    @Mock
    private FootyStatsApiClient apiClient;
    @Mock
    private TeamRecentFormRepository teamRecentFormRepository;
    @Mock
    private TeamMapper teamMapper;

    @InjectMocks
    private TeamFormService teamFormService;

    @Test
    void noFormDataLeavesExistingFormUntouched() {
        when(apiClient.fetchTeamForm(7L)).thenReturn(null);

        teamFormService.syncTeamForm(7L);

        verify(teamRecentFormRepository, never()).save(any());
    }

    @Test
    void existingFormIsUpdatedInPlace() {
        TeamDto dto = new TeamDto();
        when(apiClient.fetchTeamForm(7L)).thenReturn(dto);
        when(teamMapper.toTeamRecentForm(dto)).thenReturn(TeamRecentForm.builder().teamId(7L).build());
        when(teamRecentFormRepository.findByTeamId(7L))
                .thenReturn(Optional.of(TeamRecentForm.builder().id(42L).teamId(7L).build()));

        teamFormService.syncTeamForm(7L);

        ArgumentCaptor<TeamRecentForm> saved = ArgumentCaptor.forClass(TeamRecentForm.class);
        verify(teamRecentFormRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(42L);
    }

    @Test
    void newFormIsInserted() {
        TeamDto dto = new TeamDto();
        when(apiClient.fetchTeamForm(8L)).thenReturn(dto);
        when(teamMapper.toTeamRecentForm(dto)).thenReturn(TeamRecentForm.builder().teamId(8L).build());
        when(teamRecentFormRepository.findByTeamId(8L)).thenReturn(Optional.empty());

        teamFormService.syncTeamForm(8L);

        ArgumentCaptor<TeamRecentForm> saved = ArgumentCaptor.forClass(TeamRecentForm.class);
        verify(teamRecentFormRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isNull();
    }

    @Test
    void lookupReturnsNullWhenMissing() {
        when(teamRecentFormRepository.findByTeamId(9L)).thenReturn(Optional.empty());

        assertThat(teamFormService.getTeamRecentForm(9L)).isNull();
    }
}
