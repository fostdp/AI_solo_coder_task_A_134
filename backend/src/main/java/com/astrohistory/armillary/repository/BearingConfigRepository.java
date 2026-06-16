package com.astrohistory.armillary.repository;

import com.astrohistory.armillary.entity.BearingConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BearingConfigRepository extends JpaRepository<BearingConfig, UUID> {

    List<BearingConfig> findByInstrumentId(UUID instrumentId);

    Optional<BearingConfig> findByInstrumentIdAndAxisName(UUID instrumentId, String axisName);
}
