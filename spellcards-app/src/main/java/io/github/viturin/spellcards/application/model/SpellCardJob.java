package io.github.viturin.spellcards.application.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SpellCardJob(
        UUID jobId,
        List<String> spellNames,
        SpellCardJobStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt,
        String errorMessage
) {
    public SpellCardJob {
        spellNames = List.copyOf(spellNames);
    }
}
