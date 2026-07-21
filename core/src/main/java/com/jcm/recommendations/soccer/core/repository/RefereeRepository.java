package com.jcm.recommendations.soccer.core.repository;

import com.jcm.recommendations.soccer.domain.Referee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RefereeRepository extends JpaRepository<Referee, Long> {

    List<Referee> findBySeasonId(Long seasonId);
}
