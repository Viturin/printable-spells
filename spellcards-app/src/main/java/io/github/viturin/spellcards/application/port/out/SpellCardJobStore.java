package io.github.viturin.spellcards.application.port.out;

import io.github.viturin.spellcards.application.model.SpellCardJob;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpellCardJobStore {
    void createQueued(UUID jobId, List<String> spellNames);

    Optional<SpellCardJob> find(UUID jobId);

    void markFailed(UUID jobId, String errorMessage);
}
