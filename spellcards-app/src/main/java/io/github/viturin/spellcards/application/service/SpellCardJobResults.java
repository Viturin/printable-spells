package io.github.viturin.spellcards.application.service;

import io.github.viturin.spellcards.application.model.SpellCardJob;
import io.github.viturin.spellcards.application.model.SpellCardJobStatus;
import io.github.viturin.spellcards.application.port.out.SpellCardJobStore;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
public class SpellCardJobResults {
    private final SpellCardJobStore jobStore;

    public SpellCardJobResults(SpellCardJobStore jobStore) {
        this.jobStore = jobStore;
    }

    public SpellCardJob getJob(UUID jobId) {
        return jobStore.find(jobId)
                .orElseThrow(() -> new ResultException(Problem.JOB_NOT_FOUND, "Spell card job not found."));
    }

    public InputStream download(UUID jobId) {
        SpellCardJob job = getJob(jobId);
        requireDownloadable(job);
        try {
            var pdf = jobStore.openPdf(jobId);
            if (pdf.isPresent()) {
                return pdf.get();
            }
        } catch (IOException exception) {
            throw new ResultException(Problem.ARTIFACT_UNAVAILABLE, "The spell cards PDF could not be read.", exception);
        }

        // Cleanup may have expired the job between reading its manifest and opening the PDF.
        requireDownloadable(getJob(jobId));
        throw new ResultException(Problem.ARTIFACT_UNAVAILABLE, "The spell cards PDF is unavailable.");
    }

    private void requireDownloadable(SpellCardJob job) {
        if (job.status() == SpellCardJobStatus.EXPIRED) {
            throw new ResultException(Problem.JOB_EXPIRED, "The spell cards PDF has expired.");
        }
        if (job.status() != SpellCardJobStatus.COMPLETED) {
            throw new ResultException(Problem.JOB_NOT_DOWNLOADABLE, "The job has no completed PDF.");
        }
    }

    public enum Problem {
        JOB_NOT_FOUND,
        JOB_NOT_DOWNLOADABLE,
        JOB_EXPIRED,
        ARTIFACT_UNAVAILABLE
    }

    public static class ResultException extends RuntimeException {
        private final Problem problem;

        private ResultException(Problem problem, String message) {
            this(problem, message, null);
        }

        private ResultException(Problem problem, String message, Throwable cause) {
            super(message, cause);
            this.problem = problem;
        }

        public Problem problem() {
            return problem;
        }
    }
}
