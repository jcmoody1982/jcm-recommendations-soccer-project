package com.jcm.recommendations.soccer.core.repository;

import com.jcm.recommendations.soccer.domain.Fixture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FixtureRepository extends JpaRepository<Fixture, Long> {

    List<Fixture> findBySeasonId(Long seasonId);

    List<Fixture> findByStatus(String status);

    @Query("SELECT f FROM Fixture f WHERE f.dateUnix >= :startTime AND f.dateUnix <= :endTime")
    List<Fixture> findByDateRange(@Param("startTime") Long startTime, @Param("endTime") Long endTime);

    @Query("SELECT f FROM Fixture f WHERE f.dateUnix >= :startTime AND f.dateUnix <= :endTime AND f.status = :status")
    List<Fixture> findByDateRangeAndStatus(
            @Param("startTime") Long startTime,
            @Param("endTime") Long endTime,
            @Param("status") String status);

    List<Fixture> findByHomeTeamIdOrAwayTeamId(Long homeTeamId, Long awayTeamId);

    List<Fixture> findByRefereeId(Long refereeId);
}
