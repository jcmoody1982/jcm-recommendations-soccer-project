package com.jcm.recommendations.soccer.core.repository;

import com.jcm.recommendations.soccer.domain.PickOutcome;
import com.jcm.recommendations.soccer.domain.RecommendationSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecommendationSnapshotRepository extends JpaRepository<RecommendationSnapshot, Long> {

    Optional<RecommendationSnapshot> findBySnapshotDateAndFixtureIdAndType(
            LocalDate snapshotDate, Long fixtureId, String type);

    List<RecommendationSnapshot> findBySnapshotDateOrderByMatchDateUnixAscIdAsc(LocalDate snapshotDate);

    List<RecommendationSnapshot> findByOutcome(PickOutcome outcome);

    List<RecommendationSnapshot> findByOutcomeAndSnapshotDateGreaterThanEqual(
            PickOutcome outcome, LocalDate minSnapshotDate);

    @Query("SELECT DISTINCT s.fixtureId FROM RecommendationSnapshot s "
            + "WHERE s.outcome = :outcome AND s.snapshotDate = :date")
    List<Long> findDistinctFixtureIdsByOutcomeAndSnapshotDate(
            @Param("outcome") PickOutcome outcome,
            @Param("date") LocalDate date);

    @Query("SELECT DISTINCT s.fixtureId FROM RecommendationSnapshot s "
            + "WHERE s.outcome = :outcome AND s.snapshotDate >= :minDate")
    List<Long> findDistinctFixtureIdsByOutcomeAndSnapshotDateGreaterThanEqual(
            @Param("outcome") PickOutcome outcome,
            @Param("minDate") LocalDate minDate);

    @Query("SELECT DISTINCT s.snapshotDate FROM RecommendationSnapshot s "
            + "WHERE s.outcome = :outcome AND s.snapshotDate >= :minDate")
    List<LocalDate> findDistinctSnapshotDatesByOutcomeAndSnapshotDateGreaterThanEqual(
            @Param("outcome") PickOutcome outcome,
            @Param("minDate") LocalDate minDate);

    List<RecommendationSnapshot> findByFixtureIdInAndOutcome(
            Collection<Long> fixtureIds, PickOutcome outcome);

    @Query("SELECT DISTINCT s.snapshotDate FROM RecommendationSnapshot s ORDER BY s.snapshotDate DESC")
    List<LocalDate> findDistinctSnapshotDatesOrderBySnapshotDateDesc();

    @Query("SELECT s FROM RecommendationSnapshot s WHERE s.snapshotDate >= :fromDate AND s.snapshotDate <= :toDate")
    List<RecommendationSnapshot> findBySnapshotDateBetweenInclusive(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);

    @Query("SELECT s FROM RecommendationSnapshot s WHERE s.snapshotDate <= :toDate")
    List<RecommendationSnapshot> findBySnapshotDateLessThanEqual(@Param("toDate") LocalDate toDate);
}
