package io.github.viturin.spellcards.application.port.out;

import io.github.viturin.spellcards.application.model.SpellCardJob;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpellCardJobStore {
    void createQueued(UUID jobId, List<String> spellNames);

    Optional<SpellCardJob> find(UUID jobId);

    void markProcessing(UUID jobId);

    void complete(UUID jobId, PdfWriter writer) throws IOException;

    void markFailed(UUID jobId, String errorMessage);

    void cleanupExpired(Duration manifestRetention);

    @FunctionalInterface
    interface PdfWriter {
        void write(OutputStream output) throws IOException;
    }
}
