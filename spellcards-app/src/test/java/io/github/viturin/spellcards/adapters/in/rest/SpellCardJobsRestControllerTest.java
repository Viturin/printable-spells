package io.github.viturin.spellcards.adapters.in.rest;

import io.github.viturin.spellcards.adapters.out.filesystem.FileSpellCardJobStore;
import io.github.viturin.spellcards.application.port.in.SpellCardJobSubmissionService;
import io.github.viturin.spellcards.application.service.SpellCardJobResults;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SpellCardJobsRestControllerTest {
    private static final String JOBS = "/api/v1/spellcards/jobs";
    @TempDir
    Path artifactDirectory;
    private final UUID id = UUID.randomUUID();
    private final SpellCardJobSubmissionService submission = mock(SpellCardJobSubmissionService.class);
    private FileSpellCardJobStore store;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        store = new FileSpellCardJobStore(artifactDirectory, 24);
        configureController(store);
    }

    @Test
    void returnsAcceptedWithUuidJobId() throws Exception {
        when(submission.submit(List.of("Daze", "Fireball"))).thenReturn(id);

        mvc.perform(post(JOBS).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"spellNames":["Daze","Fireball"]}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(id.toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void reportsQueuedAndProcessingButDoesNotOfferAnArtifact() throws Exception {
        store.createQueued(id, List.of("Daze"));
        mvc.perform(get(jobUrl())).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.downloadUrl").doesNotExist());
        mvc.perform(get(pdfUrl()).accept(MediaType.APPLICATION_PDF))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("JOB_NOT_DOWNLOADABLE"));

        store.markProcessing(id);
        mvc.perform(get(jobUrl())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSING"));
        mvc.perform(get(pdfUrl())).andExpect(status().isConflict());
    }

    @Test
    void offersACompletedPdfAsAnAttachment() throws Exception {
        byte[] pdf = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
        store.createQueued(id, List.of("Daze"));
        store.markProcessing(id);
        store.complete(id, output -> output.write(pdf));

        mvc.perform(get(jobUrl())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.downloadUrl").value(pdfUrl()));
        mvc.perform(get(pdfUrl()).accept(MediaType.APPLICATION_PDF))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"spellcards.pdf\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().bytes(pdf));
    }

    @Test
    void reportsFailedJobsWithTheirError() throws Exception {
        store.createQueued(id, List.of("Daze"));
        store.markFailed(id, "Unable to generate the spell cards PDF.");

        mvc.perform(get(jobUrl())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage").value("Unable to generate the spell cards PDF."));
        mvc.perform(get(pdfUrl())).andExpect(status().isConflict());
    }

    @Test
    void reportsExpiredJobsAndRejectsTheirDownload() throws Exception {
        var expiredStore = new FileSpellCardJobStore(artifactDirectory, 0);
        configureController(expiredStore);
        expiredStore.createQueued(id, List.of("Daze"));
        expiredStore.complete(id, output -> output.write(1));

        mvc.perform(get(jobUrl())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXPIRED"));
        mvc.perform(get(pdfUrl())).andExpect(status().isGone()).andExpect(jsonPath("$.code").value("JOB_EXPIRED"));
    }

    @Test
    void returnsNotFoundForUnknownJobs() throws Exception {
        mvc.perform(get(jobUrl())).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
        mvc.perform(get(pdfUrl())).andExpect(status().isNotFound());
    }

    private void configureController(FileSpellCardJobStore jobStore) {
        mvc = MockMvcBuilders.standaloneSetup(new SpellCardJobsRestController(submission, new SpellCardJobResults(jobStore)))
                .setControllerAdvice(new RestExceptionHandler()).build();
    }

    private String jobUrl() {
        return JOBS + "/" + id;
    }

    private String pdfUrl() {
        return jobUrl() + "/pdf";
    }
}
