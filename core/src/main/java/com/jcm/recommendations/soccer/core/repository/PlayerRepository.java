package com.jcm.recommendations.soccer.core.repository;

import com.jcm.recommendations.soccer.domain.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {
}
