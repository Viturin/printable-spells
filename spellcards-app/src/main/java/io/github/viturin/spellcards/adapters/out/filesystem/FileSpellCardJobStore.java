package io.github.viturin.spellcards.adapters.out.filesystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.viturin.spellcards.application.model.SpellCardJob;
import io.github.viturin.spellcards.application.model.SpellCardJobStatus;
import io.github.viturin.spellcards.application.model.SpellCardPdfLayout;
import io.github.viturin.spellcards.application.port.out.SpellCardJobStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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
    private static final String MANIFEST_FILE = "job.json";
    private static final String PDF_FILE = "spellcards.pdf";
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
    public void createQueued(UUID jobId, List<String> spellNames, SpellCardPdfLayout layout) {
        Instant now = Instant.now();
        SpellCardJob job = new SpellCardJob(
                jobId, spellNames, layout, SpellCardJobStatus.QUEUED, now, now, now.plus(retention), null
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
        return readManifest(jobDirectory(jobId)).map(job -> expire(job, Instant.now()));
    }

    @Override
    public Optional<InputStream> openPdf(UUID jobId) throws IOException {
        try {
            return Optional.of(Files.newInputStream(jobDirectory(jobId).resolve(PDF_FILE)));
        } catch (NoSuchFileException exception) {
            return Optional.empty();
        }
    }

    private Optional<SpellCardJob> readManifest(Path directory) {
        Path manifest = directory.resolve(MANIFEST_FILE);
        try (var input = Files.newInputStream(manifest)) {
            return Optional.of(objectMapper.readValue(input, SpellCardJob.class));
        } catch (NoSuchFileException exception) {
            return Optional.empty();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read spell card job " + directory.getFileName(), exception);
        }
    }

    @Override
    public void markProcessing(UUID jobId) {
        updateStatus(jobId, SpellCardJobStatus.PROCESSING, null);
    }

    @Override
    public void complete(UUID jobId, PdfWriter writer) throws IOException {
        Path directory = jobDirectory(jobId);
        Path stagedPdf = Files.createTempFile(directory, "spellcards-", ".tmp");
        try {
            try (var output = Files.newOutputStream(stagedPdf)) {
                writer.write(output);
            }
            Files.move(stagedPdf, directory.resolve(PDF_FILE),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            updateStatus(jobId, SpellCardJobStatus.COMPLETED, null);
        } finally {
            Files.deleteIfExists(stagedPdf);
        }
    }

    @Override
    public void markFailed(UUID jobId, String errorMessage) {
        updateStatus(jobId, SpellCardJobStatus.FAILED, errorMessage);
    }

    private void updateStatus(UUID jobId, SpellCardJobStatus status, String errorMessage) {
        Path directory = jobDirectory(jobId);
        SpellCardJob current = readManifest(directory)
                .orElseThrow(() -> new IllegalStateException("Spell card job not found: " + jobId));
        Instant now = Instant.now();
        SpellCardJob updated = new SpellCardJob(
                jobId, current.spellNames(), current.layout(), status, current.createdAt(), now,
                status == SpellCardJobStatus.COMPLETED ? now.plus(retention) : current.expiresAt(), errorMessage
        );
        try {
            writeManifest(directory, updated);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to update spell card job " + jobId, exception);
        }
    }

    @Override
    public void cleanupExpired(Duration manifestRetention) {
        Instant now = Instant.now();
        try (var directories = Files.newDirectoryStream(rootDirectory)) {
            for (Path directory : directories) {
                if (Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                    cleanupJob(directory, now, manifestRetention);
                }
            }
        } catch (NoSuchFileException exception) {
            if (Files.exists(rootDirectory)) {
                throw new IllegalStateException("Unable to clean expired spell card jobs", exception);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to clean expired spell card jobs", exception);
        }
    }

    private void cleanupJob(Path directory, Instant now, Duration manifestRetention) throws IOException {
        var stored = readManifest(directory);
        if (stored.isEmpty() || stored.get().expiresAt().isAfter(now)) {
            return;
        }
        SpellCardJob original = stored.get();
        Files.deleteIfExists(directory.resolve(PDF_FILE));
        try (var temporaryFiles = Files.newDirectoryStream(directory, "{job,spellcards}-*.tmp")) {
            for (Path temporaryFile : temporaryFiles) {
                Files.deleteIfExists(temporaryFile);
            }
        }
        if (!original.expiresAt().plus(manifestRetention).isAfter(now)) {
            Files.deleteIfExists(directory.resolve(MANIFEST_FILE));
            Files.deleteIfExists(directory);
        } else if (original.status() != SpellCardJobStatus.EXPIRED) {
            writeManifest(directory, expire(original, now));
        }
    }

    private SpellCardJob expire(SpellCardJob job, Instant now) {
        if (job.status() == SpellCardJobStatus.EXPIRED || job.expiresAt().isAfter(now)) {
            return job;
        }
        return new SpellCardJob(job.jobId(), job.spellNames(), job.layout(), SpellCardJobStatus.EXPIRED,
                job.createdAt(), job.expiresAt(), job.expiresAt(), null);
    }

    private Path jobDirectory(UUID jobId) {
        return rootDirectory.resolve(jobId.toString());
    }

    private void writeManifest(Path directory, SpellCardJob job) throws IOException {
        Path temporaryManifest = Files.createTempFile(directory, "job-", ".tmp");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporaryManifest.toFile(), job);
            Files.move(temporaryManifest, directory.resolve(MANIFEST_FILE),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporaryManifest);
        }
    }
}
