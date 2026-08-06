package io.github.viturin.spellcards.adapters.out.filesystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.viturin.spellcards.application.model.SpellCardJob;
import io.github.viturin.spellcards.application.model.SpellCardJobStatus;
import io.github.viturin.spellcards.application.port.out.SpellCardJobStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class FileSpellCardJobStore implements SpellCardJobStore {
    private static final String MANIFEST_FILE = "job.json";
    private static final String ARTIFACT_FILE = "spellcards.pdf";

    private final Path rootDirectory;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration retention;
    private final Duration manifestRetention;

    @Autowired
    public FileSpellCardJobStore(
            @Value("${spellcards.jobs.artifact-dir}") String rootDirectory,
            @Value("${spellcards.jobs.retention-hours:24}") long retentionHours,
            @Value("${spellcards.jobs.manifest-retention-days:7}") long manifestRetentionDays
    ) {
        this(
                Path.of(rootDirectory),
                new ObjectMapper().findAndRegisterModules(),
                Clock.systemUTC(),
                Duration.ofHours(retentionHours),
                Duration.ofDays(manifestRetentionDays)
        );
    }

    public FileSpellCardJobStore(
            Path rootDirectory,
            ObjectMapper objectMapper,
            Clock clock,
            Duration retention,
            Duration manifestRetention
    ) {
        this.rootDirectory = rootDirectory;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.retention = retention;
        this.manifestRetention = manifestRetention;
    }

    @Override
    public SpellCardJob createQueued(UUID jobId, List<String> spellNames) {
        Instant now = clock.instant();
        SpellCardJob job = new SpellCardJob(
                jobId,
                spellNames,
                SpellCardJobStatus.QUEUED,
                now,
                now,
                now.plus(retention),
                null
        );

        Path manifest = manifestPath(jobId);
        if (Files.exists(manifest)) {
            throw new IllegalStateException("Job already exists: " + jobId);
        }
        writeManifest(job);
        return job;
    }

    @Override
    public Optional<SpellCardJob> find(UUID jobId) {
        Optional<SpellCardJob> storedJob = readManifest(jobId);
        if (storedJob.isEmpty()) {
            return Optional.empty();
        }

        SpellCardJob job = storedJob.get();
        if (job.status() != SpellCardJobStatus.EXPIRED && !job.expiresAt().isAfter(clock.instant())) {
            return Optional.of(expire(job));
        }
        return Optional.of(job);
    }

    @Override
    public SpellCardJob markProcessing(UUID jobId) {
        return updateStatus(jobId, SpellCardJobStatus.PROCESSING, null);
    }

    @Override
    public SpellCardJob markCompleted(UUID jobId) {
        return updateStatus(jobId, SpellCardJobStatus.COMPLETED, null);
    }

    @Override
    public SpellCardJob markFailed(UUID jobId, String errorMessage) {
        return updateStatus(jobId, SpellCardJobStatus.FAILED, errorMessage);
    }

    @Override
    public Path artifactPath(UUID jobId) {
        return jobDirectory(jobId).resolve(ARTIFACT_FILE);
    }

    @Override
    @Scheduled(fixedDelayString = "${spellcards.jobs.cleanup-interval-ms:3600000}")
    public void cleanupExpired() {
        if (!Files.isDirectory(rootDirectory)) {
            return;
        }

        try (DirectoryStream<Path> jobDirectories = Files.newDirectoryStream(rootDirectory)) {
            for (Path jobDirectory : jobDirectories) {
                if (!Files.isDirectory(jobDirectory)) {
                    continue;
                }
                cleanupJobDirectory(jobDirectory);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to clean spellcard jobs", exception);
        }
    }

    private SpellCardJob updateStatus(UUID jobId, SpellCardJobStatus status, String errorMessage) {
        SpellCardJob current = readManifest(jobId)
                .orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));
        if (current.status() == SpellCardJobStatus.EXPIRED) {
            throw new IllegalStateException("Job has expired: " + jobId);
        }

        SpellCardJob updated = new SpellCardJob(
                current.jobId(),
                current.spellNames(),
                status,
                current.createdAt(),
                clock.instant(),
                current.expiresAt(),
                errorMessage
        );
        writeManifest(updated);
        return updated;
    }

    private SpellCardJob expire(SpellCardJob job) {
        try {
            Files.deleteIfExists(artifactPath(job.jobId()));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to remove expired artifact: " + job.jobId(), exception);
        }

        SpellCardJob expired = new SpellCardJob(
                job.jobId(),
                job.spellNames(),
                SpellCardJobStatus.EXPIRED,
                job.createdAt(),
                clock.instant(),
                job.expiresAt(),
                job.errorMessage()
        );
        writeManifest(expired);
        return expired;
    }

    private void cleanupJobDirectory(Path jobDirectory) {
        Path manifest = jobDirectory.resolve(MANIFEST_FILE);
        Optional<SpellCardJob> job = readManifest(manifest);
        if (job.isEmpty()) {
            return;
        }

        Instant now = clock.instant();
        if (job.get().status() != SpellCardJobStatus.EXPIRED && !job.get().expiresAt().isAfter(now)) {
            expire(job.get());
            return;
        }

        if (job.get().status() == SpellCardJobStatus.EXPIRED
                && !job.get().updatedAt().plus(manifestRetention).isAfter(now)) {
            try {
                Files.deleteIfExists(manifest);
                Files.deleteIfExists(jobDirectory);
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to remove expired job: " + job.get().jobId(), exception);
            }
        }
    }

    private Optional<SpellCardJob> readManifest(UUID jobId) {
        return readManifest(manifestPath(jobId));
    }

    private Optional<SpellCardJob> readManifest(Path manifest) {
        if (!Files.exists(manifest)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(manifest.toFile(), SpellCardJob.class));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read job manifest: " + manifest, exception);
        }
    }

    private void writeManifest(SpellCardJob job) {
        Path directory = jobDirectory(job.jobId());
        Path manifest = directory.resolve(MANIFEST_FILE);
        Path temporaryManifest = directory.resolve(MANIFEST_FILE + ".tmp");
        try {
            Files.createDirectories(directory);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporaryManifest.toFile(), job);
            moveAtomically(temporaryManifest, manifest);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write job manifest: " + manifest, exception);
        }
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path jobDirectory(UUID jobId) {
        return rootDirectory.resolve(jobId.toString());
    }

    private Path manifestPath(UUID jobId) {
        return jobDirectory(jobId).resolve(MANIFEST_FILE);
    }
}
