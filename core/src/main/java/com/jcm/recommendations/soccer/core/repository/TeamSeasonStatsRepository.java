package com.jcm.recommendations.soccer.core.repository;

import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TeamSeasonStatsRepository extends JpaRepository<TeamSeasonStats, Long> {

    Optional<TeamSeasonStats> findByTeamIdAndSeasonId(Long teamId, Long seasonId);

    @Query("SELECT t FROM TeamSeasonStats t WHERE t.teamId IN :teamIds AND t.seasonId = :seasonId")
    List<TeamSeasonStats> findByTeamIdsAndSeasonId(@Param("teamIds") Collection<Long> teamIds, 
                                                    @Param("seasonId") Long seasonId);

    @Query("SELECT t FROM TeamSeasonStats t WHERE t.teamId IN :teamIds AND t.seasonId IN :seasonIds")
    List<TeamSeasonStats> findByTeamIdsAndSeasonIds(@Param("teamIds") Collection<Long> teamIds, 
                                                     @Param("seasonIds") Collection<Long> seasonIds);

    void deleteBySeasonId(Long seasonId);
}
