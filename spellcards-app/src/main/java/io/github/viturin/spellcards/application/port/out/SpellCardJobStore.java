package io.github.viturin.spellcards.application.port.out;

import io.github.viturin.spellcards.application.model.SpellCardJob;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpellCardJobStore {
    SpellCardJob createQueued(UUID jobId, List<String> spellNames);

    Optional<SpellCardJob> find(UUID jobId);

    SpellCardJob markProcessing(UUID jobId);

    SpellCardJob markCompleted(UUID jobId);

    SpellCardJob markFailed(UUID jobId, String errorMessage);

    Path artifactPath(UUID jobId);

    void cleanupExpired();
}
