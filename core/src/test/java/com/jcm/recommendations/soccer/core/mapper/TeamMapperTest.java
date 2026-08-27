package com.jcm.recommendations.soccer.core.mapper;

import com.jcm.recommendations.soccer.core.client.dto.TeamDto;
import com.jcm.recommendations.soccer.core.client.dto.TeamStatsDto;
import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TeamMapperTest {

    private final TeamMapper mapper = new TeamMapper();

    @Test
    void toTeamRecentForm_mapsScoredConcededAndFoulsAverages() {
        TeamStatsDto stats = new TeamStatsDto();
        stats.setScoredAvgOverall(1.8);
        stats.setScoredAvgHome(2.1);
        stats.setScoredAvgAway(1.4);
        stats.setConcededAvgOverall(1.1);
        stats.setConcededAvgHome(0.9);
        stats.setConcededAvgAway(1.3);
        stats.setFoulsAvgOverall(11.2);
        stats.setFoulsAvgHome(10.5);
        stats.setFoulsAvgAway(12.0);

        TeamDto dto = new TeamDto();
        dto.setId(42L);
        dto.setStats(stats);

        TeamRecentForm form = mapper.toTeamRecentForm(dto);

        assertThat(form.getTeamId()).isEqualTo(42L);
        assertThat(form.getScoredAvgOverall()).isEqualTo(1.8);
        assertThat(form.getScoredAvgHome()).isEqualTo(2.1);
        assertThat(form.getScoredAvgAway()).isEqualTo(1.4);
        assertThat(form.getConcededAvgOverall()).isEqualTo(1.1);
        assertThat(form.getConcededAvgHome()).isEqualTo(0.9);
        assertThat(form.getConcededAvgAway()).isEqualTo(1.3);
        assertThat(form.getFoulsAvgOverall()).isEqualTo(11.2);
        assertThat(form.getFoulsAvgHome()).isEqualTo(10.5);
        assertThat(form.getFoulsAvgAway()).isEqualTo(12.0);
    }

    @Test
    void toTeamSeasonStats_mapsHalfShotsAndFoulsAverages() {
        TeamStatsDto stats = new TeamStatsDto();
        stats.setScoredAvgHtOverall(0.7);
        stats.setScoredAvgHtHome(0.8);
        stats.setScoredAvgHtAway(0.6);
        stats.setConcededAvgHtOverall(0.5);
        stats.setConcededAvgHtHome(0.4);
        stats.setConcededAvgHtAway(0.6);
        stats.setScoredAvg2hOverall(0.9);
        stats.setScoredAvg2hHome(1.0);
        stats.setScoredAvg2hAway(0.8);
        stats.setConcededAvg2hOverall(0.7);
        stats.setConcededAvg2hHome(0.6);
        stats.setConcededAvg2hAway(0.8);
        stats.setBttsFhgPercentageOverall(35.0);
        stats.setBttsFhgPercentageHome(38.0);
        stats.setBttsFhgPercentageAway(32.0);
        stats.setBtts2hgPercentageOverall(42.0);
        stats.setBtts2hgPercentageHome(45.0);
        stats.setBtts2hgPercentageAway(40.0);
        stats.setShotsAvgOverall(12.5);
        stats.setShotsAvgHome(13.2);
        stats.setShotsAvgAway(11.8);
        stats.setFoulsAvgOverall(10.1);
        stats.setFoulsAvgHome(9.5);
        stats.setFoulsAvgAway(10.7);

        TeamDto dto = new TeamDto();
        dto.setId(7L);
        dto.setStats(stats);

        TeamSeasonStats seasonStats = mapper.toTeamSeasonStats(dto, 100L);

        assertThat(seasonStats.getTeamId()).isEqualTo(7L);
        assertThat(seasonStats.getSeasonId()).isEqualTo(100L);
        assertThat(seasonStats.getScoredAvgHtHome()).isEqualTo(0.8);
        assertThat(seasonStats.getConcededAvgHtAway()).isEqualTo(0.6);
        assertThat(seasonStats.getScoredAvg2hOverall()).isEqualTo(0.9);
        assertThat(seasonStats.getConcededAvg2hHome()).isEqualTo(0.6);
        assertThat(seasonStats.getBttsFhgPercentageHome()).isEqualTo(38.0);
        assertThat(seasonStats.getBtts2hgPercentageAway()).isEqualTo(40.0);
        assertThat(seasonStats.getShotsAvgOverall()).isEqualTo(12.5);
        assertThat(seasonStats.getFoulsAvgAway()).isEqualTo(10.7);
    }
}
