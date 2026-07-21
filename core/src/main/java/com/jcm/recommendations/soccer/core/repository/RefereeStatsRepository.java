package com.jcm.recommendations.soccer.core.repository;

import com.jcm.recommendations.soccer.domain.RefereeStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefereeStatsRepository extends JpaRepository<RefereeStats, Long> {

    Optional<RefereeStats> findByRefereeIdAndSeasonId(Long refereeId, Long seasonId);

    void deleteBySeasonId(Long seasonId);
}
