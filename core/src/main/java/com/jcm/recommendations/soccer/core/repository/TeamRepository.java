package com.jcm.recommendations.soccer.core.repository;

import com.jcm.recommendations.soccer.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findBySeasonId(Long seasonId);

    List<Team> findByCountry(String country);
}
