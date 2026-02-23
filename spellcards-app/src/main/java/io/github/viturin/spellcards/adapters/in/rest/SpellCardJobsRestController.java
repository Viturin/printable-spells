package io.github.viturin.spellcards.adapters.in.rest;

import io.github.viturin.spellcards.application.port.in.SpellCardJobSubmissionService;
import io.github.viturin.spellcards.generated.api.SpellCardJobsApi;
import io.github.viturin.spellcards.generated.model.CreateSpellCardJobRequest;
import io.github.viturin.spellcards.generated.model.CreateSpellCardJobResponse;
import io.github.viturin.spellcards.generated.model.SpellCardJobStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class SpellCardJobsRestController implements SpellCardJobsApi {
    private final SpellCardJobSubmissionService spellCardJobSubmissionService;

    public SpellCardJobsRestController(SpellCardJobSubmissionService spellCardJobSubmissionService) {
        this.spellCardJobSubmissionService = spellCardJobSubmissionService;
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
}
