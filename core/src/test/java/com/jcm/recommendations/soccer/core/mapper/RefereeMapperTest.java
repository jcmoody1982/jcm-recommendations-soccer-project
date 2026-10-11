package com.jcm.recommendations.soccer.core.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcm.recommendations.soccer.core.client.dto.RefereeDto;
import com.jcm.recommendations.soccer.domain.Referee;
import com.jcm.recommendations.soccer.domain.RefereeStats;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefereeMapperTest {

    private final RefereeMapper mapper = new RefereeMapper();

    private static RefereeDto parse(String json) throws Exception {
        return new ObjectMapper().readValue(json, RefereeDto.class);
    }

    @Test
    void nullDtoMapsToNull() {
        assertThat(mapper.toReferee(null, 1L)).isNull();
        assertThat(mapper.toRefereeStats(null, 1L)).isNull();
    }

    @Test
    void mapsIdentityAndSeasonFromApiJson() throws Exception {
        RefereeDto dto = parse("""
                {"id":393,"full_name":"Michael Oliver","known_as":"M. Oliver","unknown_field":1}
                """);

        Referee referee = mapper.toReferee(dto, 12325L);

        assertThat(referee.getId()).isEqualTo(393L);
        assertThat(referee.getFullName()).isEqualTo("Michael Oliver");
        assertThat(referee.getKnownAs()).isEqualTo("M. Oliver");
        assertThat(referee.getSeasonId()).isEqualTo(12325L);
    }

    @Test
    void mapsCardAndAppearanceStats() throws Exception {
        RefereeDto dto = parse("""
                {"id":393,"appearances_overall":14,"cards_per_match_overall":4.21}
                """);

        RefereeStats stats = mapper.toRefereeStats(dto, 12325L);

        assertThat(stats.getRefereeId()).isEqualTo(393L);
        assertThat(stats.getSeasonId()).isEqualTo(12325L);
        assertThat(stats.getAppearancesOverall()).isEqualTo(14);
        assertThat(stats.getCardsPerMatchOverall()).isEqualTo(4.21);
        assertThat(stats.getGoalsOverall()).isNull();
    }
}
