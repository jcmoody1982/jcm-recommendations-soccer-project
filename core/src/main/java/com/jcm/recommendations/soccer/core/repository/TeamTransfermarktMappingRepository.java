package com.jcm.recommendations.soccer.core.repository;

import com.jcm.recommendations.soccer.domain.TeamTransfermarktMapping;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamTransfermarktMappingRepository extends JpaRepository<TeamTransfermarktMapping, Long> {
}
