package com.jcm.recommendations.soccer.core.repository;

import com.jcm.recommendations.soccer.domain.FixtureOdds;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FixtureOddsRepository extends JpaRepository<FixtureOdds, Long> {
}
