package io.github.viturin.spellcards.adapters.out.filesystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.viturin.spellcards.application.model.SpellCardJob;
import io.github.viturin.spellcards.application.model.SpellCardJobStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSpellCardJobStoreTest {
    private static final Instant NOW = Instant.parse("2026-08-07T00:00:00Z");

    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsQueuedJobAndStateTransitions() {
        FileSpellCardJobStore store = newStore(Duration.ofHours(24));
        UUID jobId = UUID.randomUUID();

        SpellCardJob queued = store.createQueued(jobId, List.of("Daze", "Magic Missile"));

        assertEquals(SpellCardJobStatus.QUEUED, queued.status());
        assertEquals(queued, store.find(jobId).orElseThrow());
        assertTrue(Files.exists(temporaryDirectory.resolve(jobId.toString()).resolve("job.json")));

        assertEquals(SpellCardJobStatus.PROCESSING, store.markProcessing(jobId).status());
        assertEquals(SpellCardJobStatus.COMPLETED, store.markCompleted(jobId).status());
        assertEquals(SpellCardJobStatus.COMPLETED, store.find(jobId).orElseThrow().status());
    }

    @Test
    void expiresJobAndRemovesArtifactAfterRetentionPeriod() throws Exception {
        FileSpellCardJobStore store = newStore(Duration.ZERO);
        UUID jobId = UUID.randomUUID();
        store.createQueued(jobId, List.of("Daze"));
        Files.createDirectories(store.artifactPath(jobId).getParent());
        Files.writeString(store.artifactPath(jobId), "pdf");

        SpellCardJob expired = store.find(jobId).orElseThrow();

        assertEquals(SpellCardJobStatus.EXPIRED, expired.status());
        assertFalse(Files.exists(store.artifactPath(jobId)));
    }

    private FileSpellCardJobStore newStore(Duration retention) {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new FileSpellCardJobStore(
                temporaryDirectory,
                objectMapper,
                clock,
                retention,
                Duration.ofDays(7)
        );
    }
}
