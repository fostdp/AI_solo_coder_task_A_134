package com.astrohistory.armillary.repository;

import com.astrohistory.armillary.entity.SensorData;
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
public interface SensorDataRepository extends JpaRepository<SensorData, Long> {

    List<SensorData> findByInstrumentIdAndTimestampBetweenOrderByTimestampAsc(
            UUID instrumentId, LocalDateTime startTime, LocalDateTime endTime);

    List<SensorData> findByInstrumentIdAndAxisNameAndTimestampBetweenOrderByTimestampAsc(
            UUID instrumentId, String axisName, LocalDateTime startTime, LocalDateTime endTime);

    Page<SensorData> findByInstrumentIdOrderByTimestampDesc(UUID instrumentId, Pageable pageable);

    @Query("SELECT s FROM SensorData s WHERE s.instrument.id = :instrumentId AND s.axisName = :axisName " +
           "AND s.timestamp <= :timestamp ORDER BY s.timestamp DESC LIMIT 1")
    Optional<SensorData> findLatestByInstrumentIdAndAxisName(
            @Param("instrumentId") UUID instrumentId,
            @Param("axisName") String axisName,
            @Param("timestamp") LocalDateTime timestamp);

    @Query("SELECT DISTINCT s.axisName FROM SensorData s WHERE s.instrument.id = :instrumentId")
    List<String> findDistinctAxisNamesByInstrumentId(@Param("instrumentId") UUID instrumentId);

    @Query(value = "SELECT DISTINCT ON (instrument_id, axis_name) * FROM sensor_data " +
                   "WHERE instrument_id = :instrumentId ORDER BY instrument_id, axis_name, timestamp DESC",
           nativeQuery = true)
    List<SensorData> findLatestForAllAxes(@Param("instrumentId") UUID instrumentId);
}
