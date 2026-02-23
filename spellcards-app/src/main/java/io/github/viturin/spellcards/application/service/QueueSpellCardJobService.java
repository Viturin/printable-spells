package io.github.viturin.spellcards.application.service;

import io.github.viturin.spellcards.application.model.SpellCardJobMessage;
import io.github.viturin.spellcards.application.port.in.SpellCardJobSubmissionService;
import io.github.viturin.spellcards.application.port.out.SpellCardJobPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class QueueSpellCardJobService implements SpellCardJobSubmissionService {
    private final SpellCardJobPublisher jobPublisher;

    public QueueSpellCardJobService(SpellCardJobPublisher jobPublisher) {
        this.jobPublisher = jobPublisher;
    }

    @Override
    public String submit(List<String> spellNames) {
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

        String jobId = UUID.randomUUID().toString();
        jobPublisher.publish(new SpellCardJobMessage(jobId, normalizedSpellNames));
        return jobId;
    }
}
