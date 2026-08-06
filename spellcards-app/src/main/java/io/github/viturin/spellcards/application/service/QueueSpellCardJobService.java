package io.github.viturin.spellcards.application.service;

import io.github.viturin.spellcards.application.port.in.SpellCardJobSubmissionService;
import io.github.viturin.spellcards.application.port.out.SpellCardJobPublisher;
import io.github.viturin.spellcards.application.port.out.SpellCardJobStore;
import io.github.viturin.spellcards.queue.model.SpellCardJobMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnBean(SpellCardJobPublisher.class)
public class QueueSpellCardJobService implements SpellCardJobSubmissionService {
    private final SpellCardJobPublisher jobPublisher;
    private final SpellCardJobStore jobStore;

    public QueueSpellCardJobService(SpellCardJobPublisher jobPublisher, SpellCardJobStore jobStore) {
        this.jobPublisher = jobPublisher;
        this.jobStore = jobStore;
    }

    @Override
    public UUID submit(List<String> spellNames) {
        List<String> normalizedSpellNames = spellNames == null
                ? List.of()
                : spellNames.stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();

        if (normalizedSpellNames.isEmpty()) {
            throw new IllegalArgumentException("At least one spell name must be provided");
        }

        UUID jobId = UUID.randomUUID();
        jobStore.createQueued(jobId, normalizedSpellNames);
        try {
            jobPublisher.publish(new SpellCardJobMessage(jobId, normalizedSpellNames));
        } catch (RuntimeException exception) {
            try {
                jobStore.markFailed(jobId, "Unable to queue spell card generation");
            } catch (RuntimeException storeException) {
                exception.addSuppressed(storeException);
            }
            throw exception;
        }
        return jobId;
    }
}
