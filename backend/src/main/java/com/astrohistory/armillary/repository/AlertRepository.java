package com.astrohistory.armillary.repository;

import com.astrohistory.armillary.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByInstrumentIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID instrumentId, LocalDateTime startTime, LocalDateTime endTime);

    List<Alert> findByInstrumentIdAndIsAcknowledgedFalseOrderByCreatedAtDesc(UUID instrumentId);

    Page<Alert> findByInstrumentIdOrderByCreatedAtDesc(UUID instrumentId, Pageable pageable);

    long countByInstrumentIdAndIsAcknowledgedFalse(UUID instrumentId);

    @Modifying
    @Transactional
    @Query("UPDATE Alert a SET a.isAcknowledged = true, a.acknowledgedAt = :acknowledgedAt WHERE a.id = :id")
    int acknowledgeAlert(@Param("id") Long id, @Param("acknowledgedAt") LocalDateTime acknowledgedAt);

    @Modifying
    @Transactional
    @Query("UPDATE Alert a SET a.isAcknowledged = true, a.acknowledgedAt = :acknowledgedAt " +
           "WHERE a.instrument.id = :instrumentId AND a.isAcknowledged = false")
    int acknowledgeAllAlerts(@Param("instrumentId") UUID instrumentId, @Param("acknowledgedAt") LocalDateTime acknowledgedAt);
}
