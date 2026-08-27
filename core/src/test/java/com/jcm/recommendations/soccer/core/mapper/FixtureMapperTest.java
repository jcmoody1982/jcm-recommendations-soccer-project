package com.jcm.recommendations.soccer.core.mapper;

import com.jcm.recommendations.soccer.core.client.dto.MatchDto;
import com.jcm.recommendations.soccer.domain.FixtureHeadToHead;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FixtureMapperTest {

    private final FixtureMapper mapper = new FixtureMapper();

    @Test
    void toFixtureHeadToHead_mapsAggregatesAndMatchIds() {
        MatchDto.H2hDto h2h = new MatchDto.H2hDto();
        h2h.setPreviousMeetings(4);
        h2h.setTeamAWins(2);
        h2h.setTeamBWins(1);
        h2h.setDraws(1);
        h2h.setPreviousMatchIds(List.of(10L, 20L, -1L));

        MatchDto dto = new MatchDto();
        dto.setId(99L);
        dto.setH2h(h2h);

        Instant fetchedAt = Instant.parse("2026-08-26T12:00:00Z");
        FixtureHeadToHead mapped = mapper.toFixtureHeadToHead(dto, fetchedAt);

        assertThat(mapped.getFixtureId()).isEqualTo(99L);
        assertThat(mapped.getPreviousMeetings()).isEqualTo(4);
        assertThat(mapped.getHomeWins()).isEqualTo(2);
        assertThat(mapped.getAwayWins()).isEqualTo(1);
        assertThat(mapped.getDraws()).isEqualTo(1);
        assertThat(mapped.getPreviousMatchIdsJson()).isEqualTo("[10,20]");
        assertThat(mapped.getFetchedAt()).isEqualTo(fetchedAt);
    }

    @Test
    void toFixtureHeadToHead_returnsNullWhenMissing() {
        MatchDto dto = new MatchDto();
        dto.setId(1L);

        assertThat(mapper.toFixtureHeadToHead(dto, Instant.now())).isNull();
        assertThat(mapper.toFixtureHeadToHead(null, Instant.now())).isNull();
    }
}
