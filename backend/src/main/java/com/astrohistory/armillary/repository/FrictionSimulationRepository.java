package com.astrohistory.armillary.repository;

import com.astrohistory.armillary.entity.FrictionSimulation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FrictionSimulationRepository extends JpaRepository<FrictionSimulation, Long> {

    List<FrictionSimulation> findByInstrumentIdAndSimulationTimeBetweenOrderBySimulationTimeAsc(
            UUID instrumentId, LocalDateTime startTime, LocalDateTime endTime);

    List<FrictionSimulation> findByInstrumentIdAndAxisNameAndSimulationTimeBetweenOrderBySimulationTimeAsc(
            UUID instrumentId, String axisName, LocalDateTime startTime, LocalDateTime endTime);

    @Query("SELECT f FROM FrictionSimulation f WHERE f.instrument.id = :instrumentId AND f.axisName = :axisName " +
           "ORDER BY f.simulationTime DESC LIMIT 1")
    Optional<FrictionSimulation> findLatestByInstrumentIdAndAxisName(
            @Param("instrumentId") UUID instrumentId,
            @Param("axisName") String axisName);

    @Query(value = "SELECT DISTINCT ON (instrument_id, axis_name) * FROM friction_simulation " +
                   "WHERE instrument_id = :instrumentId ORDER BY instrument_id, axis_name, simulation_time DESC",
           nativeQuery = true)
    List<FrictionSimulation> findLatestForAllAxes(@Param("instrumentId") UUID instrumentId);
}
