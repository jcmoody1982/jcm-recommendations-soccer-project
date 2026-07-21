package com.jcm.recommendations.soccer.core.repository;

import com.jcm.recommendations.soccer.domain.League;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeagueRepository extends JpaRepository<League, Long> {

    List<League> findByCountry(String country);
}
