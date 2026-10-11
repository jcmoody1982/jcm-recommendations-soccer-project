package com.jcm.recommendations.soccer.core.service;

import com.jcm.recommendations.soccer.domain.Fixture;
import com.jcm.recommendations.soccer.domain.League;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataSyncServiceTest {

    @Mock
    private LeagueService leagueService;
    @Mock
    private FixtureService fixtureService;
    @Mock
    private TeamService teamService;
    @Mock
    private PlayerService playerService;
    @Mock
    private RefereeService refereeService;
    @Mock
    private TeamFormService teamFormService;
    @Mock
    private FixtureH2hService fixtureH2hService;
    @Mock
    private TeamSquadProfileService teamSquadProfileService;

    @InjectMocks
    private DataSyncService dataSyncService;

    private final League premierLeague = League.builder().currentSeasonId(1L).name("Premier League").build();
    private final League laLiga = League.builder().currentSeasonId(2L).name("La Liga").build();
    private final Fixture upcoming = Fixture.builder().id(100L).homeTeamId(10L).awayTeamId(20L).build();

    @BeforeEach
    void setUp() {
        when(leagueService.syncLeagues()).thenReturn(new LeagueService.SyncResult(1, 1, 0));
        when(leagueService.getAllLeagues()).thenReturn(List.of(premierLeague, laLiga));
    }

    private void seasonSyncSucceeds() {
        when(fixtureService.syncFixturesForSeason(anyLong(), anyString())).thenReturn(new FixtureService.SyncResult(2, 1, 0));
        when(teamService.syncTeamsForSeason(anyLong(), anyString())).thenReturn(new TeamService.SyncResult(1, 0, 0));
        when(playerService.syncPlayersForSeason(anyLong(), anyString())).thenReturn(new PlayerService.SyncResult(5, 0, 0));
        when(refereeService.syncRefereesForSeason(anyLong(), anyString())).thenReturn(new RefereeService.SyncResult(1, 0, 0));
        when(fixtureService.getUpcomingFixtures()).thenReturn(List.of(upcoming));
        when(fixtureH2hService.syncHeadToHeadForUpcoming(any())).thenReturn(new FixtureH2hService.SyncResult(1, 0, 0));
        when(teamSquadProfileService.syncSquadProfilesForTeams(any())).thenReturn(new TeamSquadProfileService.SyncResult(2, 0, 0, 0));
    }

    @Test
    void fullSyncTotalsEverySeasonAndEnrichesUpcomingTeamsOnce() {
        seasonSyncSucceeds();

        DataSyncService.SyncSummary summary = dataSyncService.runFullSync();

        assertThat(summary.success()).isTrue();
        assertThat(summary.leaguesProcessed()).isEqualTo(2);
        assertThat(summary.fixturesProcessed()).isEqualTo(6);
        assertThat(summary.teamsProcessed()).isEqualTo(2);
        assertThat(summary.playersProcessed()).isEqualTo(10);
        assertThat(summary.refereesProcessed()).isEqualTo(2);
        assertThat(summary.formsProcessed()).isEqualTo(2);
        assertThat(summary.h2hProcessed()).isEqualTo(1);
        assertThat(summary.squadProfilesProcessed()).isEqualTo(2);
        verify(teamFormService).syncTeamForm(10L);
        verify(teamFormService).syncTeamForm(20L);
        verify(teamSquadProfileService).syncSquadProfilesForTeams(Set.of(10L, 20L));
    }

    @Test
    void oneFailedSeasonDoesNotStopTheOthersButMarksTheRunUnsuccessful() {
        seasonSyncSucceeds();
        when(fixtureService.syncFixturesForSeason(eq(1L), anyString())).thenThrow(new RuntimeException("API down"));

        DataSyncService.SyncSummary summary = dataSyncService.runFullSync();

        assertThat(summary.success()).isFalse();
        assertThat(summary.fixturesProcessed()).isEqualTo(3);
        verify(teamService, never()).syncTeamsForSeason(eq(1L), anyString());
        verify(teamService).syncTeamsForSeason(eq(2L), anyString());
    }

    @Test
    void playerFailureIsTolerated() {
        seasonSyncSucceeds();
        when(playerService.syncPlayersForSeason(anyLong(), anyString())).thenThrow(new RuntimeException("players 500"));

        DataSyncService.SyncSummary summary = dataSyncService.runFullSync();

        assertThat(summary.success()).isTrue();
        assertThat(summary.playersProcessed()).isZero();
        verify(refereeService, times(2)).syncRefereesForSeason(anyLong(), anyString());
    }

    @Test
    void enrichmentFailuresAreToleratedAndCountedAsZero() {
        seasonSyncSucceeds();
        doThrow(new RuntimeException("form")).when(teamFormService).syncTeamForm(10L);
        when(fixtureH2hService.syncHeadToHeadForUpcoming(any())).thenThrow(new RuntimeException("h2h"));
        when(teamSquadProfileService.syncSquadProfilesForTeams(any())).thenThrow(new RuntimeException("squad"));

        DataSyncService.SyncSummary summary = dataSyncService.runFullSync();

        assertThat(summary.success()).isTrue();
        assertThat(summary.formsProcessed()).isEqualTo(1);
        assertThat(summary.h2hProcessed()).isZero();
        assertThat(summary.squadProfilesProcessed()).isZero();
    }

    @Test
    void leagueSyncFailureAbortsTheWholeRun() {
        when(leagueService.syncLeagues()).thenThrow(new RuntimeException("league-list 401"));

        assertThatThrownBy(() -> dataSyncService.runFullSync()).hasMessageContaining("401");
        verify(fixtureService, never()).syncFixturesForSeason(anyLong(), anyString());
    }
}
