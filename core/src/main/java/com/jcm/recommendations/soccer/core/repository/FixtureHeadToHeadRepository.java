package com.jcm.recommendations.soccer.core.repository;

import com.jcm.recommendations.soccer.domain.FixtureHeadToHead;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FixtureHeadToHeadRepository extends JpaRepository<FixtureHeadToHead, Long> {
}
