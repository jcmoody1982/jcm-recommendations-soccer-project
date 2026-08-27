package com.jcm.recommendations.soccer.core.repository;

import com.jcm.recommendations.soccer.domain.TeamSquadProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamSquadProfileRepository extends JpaRepository<TeamSquadProfile, Long> {
}
