package io.github.viturin.spellcards.adapters.out.filesystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.viturin.spellcards.application.model.SpellCardJob;
import io.github.viturin.spellcards.application.model.SpellCardJobStatus;
import io.github.viturin.spellcards.application.port.out.SpellCardJobStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class FileSpellCardJobStore implements SpellCardJobStore {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Path rootDirectory;
    private final Duration retention;

    public FileSpellCardJobStore(
            @Value("${spellcards.jobs.artifact-dir}") Path rootDirectory,
            @Value("${spellcards.jobs.retention-hours}") long retentionHours
    ) {
        this.rootDirectory = rootDirectory;
        this.retention = Duration.ofHours(retentionHours);
    }

    @Override
    public void createQueued(UUID jobId, List<String> spellNames) {
        Instant now = Instant.now();
        SpellCardJob job = new SpellCardJob(
                jobId, spellNames, SpellCardJobStatus.QUEUED, now, now, now.plus(retention), null
        );
        Path directory = jobDirectory(jobId);
        try {
            Files.createDirectories(rootDirectory);
            Files.createDirectory(directory);
            writeManifest(directory, job);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create spell card job " + jobId, exception);
        }
    }

    @Override
    public Optional<SpellCardJob> find(UUID jobId) {
        Path manifest = jobDirectory(jobId).resolve("job.json");
        try (var input = Files.newInputStream(manifest)) {
            return Optional.of(objectMapper.readValue(input, SpellCardJob.class));
        } catch (NoSuchFileException exception) {
            return Optional.empty();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read spell card job " + jobId, exception);
        }
    }

    @Override
    public void markFailed(UUID jobId, String errorMessage) {
        SpellCardJob current = find(jobId)
                .orElseThrow(() -> new IllegalStateException("Spell card job not found: " + jobId));
        SpellCardJob failed = new SpellCardJob(
                jobId, current.spellNames(), SpellCardJobStatus.FAILED, current.createdAt(),
                Instant.now(), current.expiresAt(), errorMessage
        );
        try {
            writeManifest(jobDirectory(jobId), failed);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to mark spell card job failed: " + jobId, exception);
        }
    }

    private Path jobDirectory(UUID jobId) {
        return rootDirectory.resolve(jobId.toString());
    }

    private void writeManifest(Path directory, SpellCardJob job) throws IOException {
        Path temporaryManifest = Files.createTempFile(directory, "job-", ".tmp");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporaryManifest.toFile(), job);
            Files.move(temporaryManifest, directory.resolve("job.json"),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporaryManifest);
        }
    }
}
