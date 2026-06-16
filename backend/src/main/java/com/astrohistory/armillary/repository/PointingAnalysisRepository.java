package com.astrohistory.armillary.repository;

import com.astrohistory.armillary.entity.PointingAnalysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PointingAnalysisRepository extends JpaRepository<PointingAnalysis, Long> {

    List<PointingAnalysis> findByInstrumentIdAndAnalysisTimeBetweenOrderByAnalysisTimeAsc(
            UUID instrumentId, LocalDateTime startTime, LocalDateTime endTime);

    Page<PointingAnalysis> findByInstrumentIdOrderByAnalysisTimeDesc(UUID instrumentId, Pageable pageable);

    @Query("SELECT p FROM PointingAnalysis p WHERE p.instrument.id = :instrumentId " +
           "ORDER BY p.analysisTime DESC LIMIT 1")
    Optional<PointingAnalysis> findLatestByInstrumentId(@Param("instrumentId") UUID instrumentId);
}
