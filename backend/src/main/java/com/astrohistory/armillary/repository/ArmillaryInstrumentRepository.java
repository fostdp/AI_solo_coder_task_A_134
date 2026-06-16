package com.astrohistory.armillary.repository;

import com.astrohistory.armillary.entity.ArmillaryInstrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ArmillaryInstrumentRepository extends JpaRepository<ArmillaryInstrument, UUID> {

    List<ArmillaryInstrument> findByStatus(String status);

    Optional<ArmillaryInstrument> findByName(String name);
}
