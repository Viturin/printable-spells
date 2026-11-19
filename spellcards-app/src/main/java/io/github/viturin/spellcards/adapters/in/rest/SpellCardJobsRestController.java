package io.github.viturin.spellcards.adapters.in.rest;

import io.github.viturin.spellcards.application.port.in.SpellCardJobSubmissionService;
import io.github.viturin.spellcards.application.service.SpellCardJobResults;
import io.github.viturin.spellcards.generated.api.SpellCardJobsApi;
import io.github.viturin.spellcards.generated.model.CreateSpellCardJobRequest;
import io.github.viturin.spellcards.generated.model.CreateSpellCardJobResponse;
import io.github.viturin.spellcards.generated.model.SpellCardJobResponse;
import io.github.viturin.spellcards.generated.model.SpellCardJobStatus;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class SpellCardJobsRestController implements SpellCardJobsApi {
    private final SpellCardJobSubmissionService spellCardJobSubmissionService;
    private final SpellCardJobResults jobResults;

    public SpellCardJobsRestController(SpellCardJobSubmissionService spellCardJobSubmissionService,
                                       SpellCardJobResults jobResults) {
        this.spellCardJobSubmissionService = spellCardJobSubmissionService;
        this.jobResults = jobResults;
    }

    @Override
    public ResponseEntity<CreateSpellCardJobResponse> createSpellCardJob(CreateSpellCardJobRequest createSpellCardJobRequest) {
        UUID jobId = spellCardJobSubmissionService.submit(createSpellCardJobRequest.getSpellNames());
        CreateSpellCardJobResponse response = new CreateSpellCardJobResponse(
                jobId,
                SpellCardJobStatus.QUEUED
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @Override
    public ResponseEntity<SpellCardJobResponse> getSpellCardJob(UUID jobId) {
        var job = jobResults.getJob(jobId);
        var status = SpellCardJobStatus.valueOf(job.status().name());
        var response = new SpellCardJobResponse(jobId, status);
        if (status == SpellCardJobStatus.COMPLETED) {
            response.setDownloadUrl("/api/v1/spellcards/jobs/" + jobId + "/pdf");
        } else if (status == SpellCardJobStatus.FAILED) {
            response.setErrorMessage(job.errorMessage());
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @Override
    public ResponseEntity<Resource> downloadSpellCardJobPdf(UUID jobId) {
        var resource = new InputStreamResource(jobResults.download(jobId));
        var disposition = ContentDisposition.attachment().filename("spellcards.pdf").build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }
}
