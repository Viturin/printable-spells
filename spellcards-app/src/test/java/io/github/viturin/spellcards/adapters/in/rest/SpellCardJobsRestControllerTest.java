package io.github.viturin.spellcards.adapters.in.rest;

import io.github.viturin.spellcards.application.port.in.SpellCardJobSubmissionService;
import io.github.viturin.spellcards.generated.model.CreateSpellCardJobRequest;
import io.github.viturin.spellcards.generated.model.CreateSpellCardJobResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpellCardJobsRestControllerTest {

    @Test
    void returnsAcceptedWithUuidJobId() {
        SpellCardJobSubmissionService submissionService = mock(SpellCardJobSubmissionService.class);
        CreateSpellCardJobRequest request = mock(CreateSpellCardJobRequest.class);
        UUID expectedJobId = UUID.fromString("f90e08a1-c2f0-4532-b2d8-56f4b0576e1a");

        when(request.getSpellNames()).thenReturn(List.of("Daze", "Magic Missile"));
        when(submissionService.submit(List.of("Daze", "Magic Missile"))).thenReturn(expectedJobId);

        SpellCardJobsRestController controller = new SpellCardJobsRestController(submissionService);

        ResponseEntity<CreateSpellCardJobResponse> response = controller.createSpellCardJob(request);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(expectedJobId.toString(), response.getBody().getJobId().toString());
        assertEquals("QUEUED", response.getBody().getStatus().toString());
    }
}
