package com.jcm.recommendations.soccer.core.repository;

import com.jcm.recommendations.soccer.domain.CompletedMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CompletedMatchRepository extends JpaRepository<CompletedMatch, Long> {

    List<CompletedMatch> findByFixtureIdIn(Collection<Long> fixtureIds);
}
