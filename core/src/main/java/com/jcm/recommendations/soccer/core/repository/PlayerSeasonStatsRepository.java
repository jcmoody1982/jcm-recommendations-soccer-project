package com.jcm.recommendations.soccer.core.repository;

import com.jcm.recommendations.soccer.domain.PlayerSeasonStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerSeasonStatsRepository extends JpaRepository<PlayerSeasonStats, Long> {

    Optional<PlayerSeasonStats> findByPlayerIdAndSeasonId(Long playerId, Long seasonId);

    @Query("""
            SELECT p FROM PlayerSeasonStats p
            WHERE p.seasonId IN :seasonIds
              AND (p.clubTeamId IN :teamIds OR p.clubTeam2Id IN :teamIds)
            """)
    List<PlayerSeasonStats> findByTeamIdsAndSeasonIds(
            @Param("teamIds") Collection<Long> teamIds,
            @Param("seasonIds") Collection<Long> seasonIds);
}
