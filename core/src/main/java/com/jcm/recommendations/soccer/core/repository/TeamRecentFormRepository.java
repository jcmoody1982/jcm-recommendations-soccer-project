package com.jcm.recommendations.soccer.core.repository;

import com.jcm.recommendations.soccer.domain.TeamRecentForm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TeamRecentFormRepository extends JpaRepository<TeamRecentForm, Long> {

    Optional<TeamRecentForm> findByTeamId(Long teamId);

    void deleteByTeamId(Long teamId);
}
