package io.github.viturin.spellcards.worker.adapters.in.queue;

import io.github.viturin.spellcards.application.model.SpellCardJobStatus;
import io.github.viturin.spellcards.application.port.out.SpellCardJobStore;
import io.github.viturin.spellcards.domain.model.Spell;
import io.github.viturin.spellcards.domain.port.SpellRepository;
import io.github.viturin.spellcards.queue.model.SpellCardJobMessage;
import io.github.viturin.spellcards.worker.pdf.SpellCardPdfRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class SpellCardJobWorker {
    private static final Logger LOG = LoggerFactory.getLogger(SpellCardJobWorker.class);

    private final SpellCardJobStore jobStore;
    private final SpellRepository spellRepository;
    private final SpellCardPdfRenderer renderer;
    private final Duration manifestRetention;

    public SpellCardJobWorker(SpellCardJobStore jobStore, SpellRepository spellRepository, SpellCardPdfRenderer renderer,
                              @Value("${spellcards.jobs.manifest-retention-days}") long manifestRetentionDays) {
        this.jobStore = jobStore;
        this.spellRepository = spellRepository;
        this.renderer = renderer;
        this.manifestRetention = Duration.ofDays(manifestRetentionDays);
    }

    @RabbitListener(queues = "${spellcards.queue.name}")
    public synchronized void handle(SpellCardJobMessage jobMessage) {
        var jobId = jobMessage.jobId();
        var stored = jobStore.find(jobId);
        if (stored.isEmpty()) {
            LOG.warn("Acknowledging spell card job {} without a manifest", jobId);
            return;
        }
        var job = stored.get();
        if (job.status() != SpellCardJobStatus.QUEUED && job.status() != SpellCardJobStatus.PROCESSING) {
            return;
        }

        jobStore.markProcessing(jobId);
        try {
            List<Spell> spells = new ArrayList<>();
            for (String name : job.spellNames()) {
                var spell = spellRepository.findByName(name);
                if (spell.isEmpty()) {
                    jobStore.markFailed(jobId, "Spell not found: " + name);
                    return;
                }
                spells.add(spell.get());
            }
            jobStore.complete(jobId, output -> renderer.render(spells, job.layout(), output));
        } catch (IOException | RuntimeException exception) {
            LOG.error("Unable to generate spell cards for job {}", jobId, exception);
            jobStore.markFailed(jobId, "Unable to generate the spell cards PDF.");
        }
    }

    @Scheduled(fixedDelayString = "${spellcards.jobs.cleanup-interval-ms}")
    public synchronized void cleanupExpiredJobs() {
        jobStore.cleanupExpired(manifestRetention);
    }
}
