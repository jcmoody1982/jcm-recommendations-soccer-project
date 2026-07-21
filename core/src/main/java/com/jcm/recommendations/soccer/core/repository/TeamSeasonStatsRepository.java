package com.jcm.recommendations.soccer.core.repository;

import com.jcm.recommendations.soccer.domain.TeamSeasonStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TeamSeasonStatsRepository extends JpaRepository<TeamSeasonStats, Long> {

    Optional<TeamSeasonStats> findByTeamIdAndSeasonId(Long teamId, Long seasonId);

    void deleteBySeasonId(Long seasonId);
}
