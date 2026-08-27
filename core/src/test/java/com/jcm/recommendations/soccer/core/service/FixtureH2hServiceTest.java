package com.jcm.recommendations.soccer.core.service;

import com.jcm.recommendations.soccer.core.client.FootyStatsApiClient;
import com.jcm.recommendations.soccer.core.client.dto.MatchDto;
import com.jcm.recommendations.soccer.core.mapper.FixtureMapper;
import com.jcm.recommendations.soccer.core.repository.FixtureHeadToHeadRepository;
import com.jcm.recommendations.soccer.domain.Fixture;
import com.jcm.recommendations.soccer.domain.FixtureHeadToHead;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FixtureH2hServiceTest {

    private static final ZoneId LONDON = ZoneId.of("Europe/London");

    @Mock
    private FootyStatsApiClient apiClient;
    @Mock
    private FixtureHeadToHeadRepository headToHeadRepository;

    private FixtureMapper fixtureMapper;
    private FixtureH2hService service;

    @BeforeEach
    void setUp() {
        fixtureMapper = new FixtureMapper();
        service = new FixtureH2hService(apiClient, headToHeadRepository, fixtureMapper);
    }

    @Test
    void isFreshForLondonDay_trueWhenFetchedSameLondonDay() {
        LocalDate today = LocalDate.now(LONDON);
        Instant fetchedAt = today.atStartOfDay(LONDON).toInstant().plusSeconds(3600);

        assertThat(service.isFreshForLondonDay(fetchedAt, today)).isTrue();
    }

    @Test
    void isFreshForLondonDay_falseWhenFetchedPreviousDay() {
        LocalDate today = LocalDate.now(LONDON);
        Instant fetchedAt = today.minusDays(1).atStartOfDay(LONDON).toInstant();

        assertThat(service.isFreshForLondonDay(fetchedAt, today)).isFalse();
    }

    @Test
    void syncHeadToHeadForUpcoming_skipsFreshRows() {
        Fixture fixture = Fixture.builder().id(1L).status("incomplete").build();
        FixtureHeadToHead existing = FixtureHeadToHead.builder()
                .fixtureId(1L)
                .previousMeetings(3)
                .fetchedAt(Instant.now())
                .build();

        when(headToHeadRepository.findById(1L)).thenReturn(Optional.of(existing));

        FixtureH2hService.SyncResult result = service.syncHeadToHeadForUpcoming(List.of(fixture));

        assertThat(result.skippedFresh()).isEqualTo(1);
        assertThat(result.synced()).isEqualTo(0);
        verify(apiClient, never()).fetchMatch(any());
        verify(headToHeadRepository, never()).save(any());
    }

    @Test
    void syncHeadToHeadForUpcoming_persistsWhenStale() {
        Fixture fixture = Fixture.builder().id(2L).status("incomplete").build();
        FixtureHeadToHead stale = FixtureHeadToHead.builder()
                .fixtureId(2L)
                .previousMeetings(1)
                .fetchedAt(LocalDate.now(LONDON).minusDays(2).atStartOfDay(LONDON).toInstant())
                .build();

        MatchDto.H2hDto h2h = new MatchDto.H2hDto();
        h2h.setPreviousMeetings(4);
        h2h.setTeamAWins(2);
        h2h.setTeamBWins(1);
        h2h.setDraws(1);
        h2h.setPreviousMatchIds(List.of(11L, 22L));

        MatchDto matchDto = new MatchDto();
        matchDto.setId(2L);
        matchDto.setH2h(h2h);

        when(headToHeadRepository.findById(2L)).thenReturn(Optional.of(stale));
        when(apiClient.fetchMatch(2L)).thenReturn(matchDto);
        when(headToHeadRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        FixtureH2hService.SyncResult result = service.syncHeadToHeadForUpcoming(List.of(fixture));

        assertThat(result.synced()).isEqualTo(1);
        assertThat(result.skippedFresh()).isEqualTo(0);

        ArgumentCaptor<FixtureHeadToHead> captor = ArgumentCaptor.forClass(FixtureHeadToHead.class);
        verify(headToHeadRepository).save(captor.capture());
        assertThat(captor.getValue().getPreviousMeetings()).isEqualTo(4);
        assertThat(captor.getValue().getHomeWins()).isEqualTo(2);
        assertThat(captor.getValue().getPreviousMatchIdsJson()).isEqualTo("[11,22]");
        verify(apiClient).fetchMatch(eq(2L));
    }
}
