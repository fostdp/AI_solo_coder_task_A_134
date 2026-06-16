package com.astrohistory.armillary.controller;

import com.astrohistory.armillary.entity.ArmillaryInstrument;
import com.astrohistory.armillary.repository.ArmillaryInstrumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/instruments")
@RequiredArgsConstructor
public class InstrumentController {

    private final ArmillaryInstrumentRepository instrumentRepository;

    @GetMapping
    public ResponseEntity<List<ArmillaryInstrument>> getAllInstruments() {
        return ResponseEntity.ok(instrumentRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ArmillaryInstrument> getInstrumentById(@PathVariable UUID id) {
        return instrumentRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ArmillaryInstrument> createInstrument(
            @RequestBody ArmillaryInstrument instrument) {
        instrument.setId(null);
        return ResponseEntity.ok(instrumentRepository.save(instrument));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ArmillaryInstrument> updateInstrument(
            @PathVariable UUID id,
            @RequestBody ArmillaryInstrument instrument) {
        return instrumentRepository.findById(id)
                .map(existing -> {
                    instrument.setId(id);
                    return ResponseEntity.ok(instrumentRepository.save(instrument));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInstrument(@PathVariable UUID id) {
        return instrumentRepository.findById(id)
                .map(instrument -> {
                    instrumentRepository.delete(instrument);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
