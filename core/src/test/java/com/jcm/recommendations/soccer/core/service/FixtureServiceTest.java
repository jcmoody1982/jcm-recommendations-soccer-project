package com.jcm.recommendations.soccer.core.service;

import com.jcm.recommendations.soccer.core.client.FootyStatsApiClient;
import com.jcm.recommendations.soccer.core.client.dto.MatchDto;
import com.jcm.recommendations.soccer.core.mapper.FixtureMapper;
import com.jcm.recommendations.soccer.core.repository.FixtureOddsRepository;
import com.jcm.recommendations.soccer.core.repository.FixturePotentialsRepository;
import com.jcm.recommendations.soccer.core.repository.FixtureRepository;
import com.jcm.recommendations.soccer.domain.Fixture;
import com.jcm.recommendations.soccer.domain.FixtureOdds;
import com.jcm.recommendations.soccer.domain.FixturePotentials;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FixtureServiceTest {

    private static final long DAY = 86_400L;

    @Mock
    private FootyStatsApiClient apiClient;
    @Mock
    private FixtureRepository fixtureRepository;
    @Mock
    private FixtureOddsRepository fixtureOddsRepository;
    @Mock
    private FixturePotentialsRepository fixturePotentialsRepository;
    @Mock
    private FixtureMapper fixtureMapper;

    @InjectMocks
    private FixtureService fixtureService;

    private static MatchDto match(long id, Long dateUnix) {
        MatchDto dto = new MatchDto();
        dto.setId(id);
        dto.setDateUnix(dateUnix);
        return dto;
    }

    @Test
    void syncPersistsOnlyTheNextSevenDaysAndCountsNewVersusUpdated() {
        long now = Instant.now().getEpochSecond();
        MatchDto past = match(1, now - DAY);
        MatchDto newSoon = match(2, now + DAY);
        MatchDto existingSoon = match(3, now + 2 * DAY);
        MatchDto tooFar = match(4, now + 9 * DAY);
        MatchDto undated = match(5, null);
        when(apiClient.fetchMatches(100L)).thenReturn(List.of(past, newSoon, existingSoon, tooFar, undated));
        when(fixtureRepository.existsById(2L)).thenReturn(false);
        when(fixtureRepository.existsById(3L)).thenReturn(true);
        when(fixtureMapper.toFixture(any(), eq(100L))).thenAnswer(inv -> Fixture.builder()
                .id(((MatchDto) inv.getArgument(0)).getId()).build());
        when(fixtureMapper.toFixtureOdds(any())).thenReturn(new FixtureOdds());
        when(fixtureMapper.toFixturePotentials(any())).thenReturn(new FixturePotentials());

        FixtureService.SyncResult result = fixtureService.syncFixturesForSeason(100L, "Premier League");

        assertThat(result.newCount()).isEqualTo(1);
        assertThat(result.updatedCount()).isEqualTo(1);
        ArgumentCaptor<Fixture> saved = ArgumentCaptor.forClass(Fixture.class);
        verify(fixtureRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Fixture::getId).containsExactly(2L, 3L);
        verify(fixtureOddsRepository, times(2)).save(any());
        verify(fixturePotentialsRepository, times(2)).save(any());
    }

    @Test
    void emptyFeedPersistsNothing() {
        when(apiClient.fetchMatches(100L)).thenReturn(List.of());

        FixtureService.SyncResult result = fixtureService.syncFixturesForSeason(100L, "Premier League");

        assertThat(result).isEqualTo(new FixtureService.SyncResult(0, 0, 0));
        verify(fixtureRepository, never()).save(any());
    }

    @Test
    void upcomingFixturesUseFractionalDayWindowOnIncompleteFixtures() {
        when(fixtureRepository.findByDateRangeAndStatus(anyLong(), anyLong(), eq("incomplete"))).thenReturn(List.of());

        fixtureService.getUpcomingFixtures(0.5);

        ArgumentCaptor<Long> start = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> end = ArgumentCaptor.forClass(Long.class);
        verify(fixtureRepository).findByDateRangeAndStatus(start.capture(), end.capture(), eq("incomplete"));
        assertThat(end.getValue() - start.getValue()).isEqualTo(DAY / 2);
    }

    @Test
    void lookupsReturnNullWhenMissing() {
        when(fixtureRepository.findById(1L)).thenReturn(Optional.empty());
        when(fixtureOddsRepository.findById(1L)).thenReturn(Optional.empty());
        when(fixturePotentialsRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(fixtureService.getFixtureById(1L)).isNull();
        assertThat(fixtureService.getFixtureOdds(1L)).isNull();
        assertThat(fixtureService.getFixturePotentials(1L)).isNull();
    }
}
