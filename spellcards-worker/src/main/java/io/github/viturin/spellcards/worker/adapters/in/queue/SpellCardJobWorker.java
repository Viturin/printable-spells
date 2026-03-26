package io.github.viturin.spellcards.worker.adapters.in.queue;

import io.github.viturin.spellcards.application.port.in.SpellCardGenerationService;
import io.github.viturin.spellcards.queue.model.SpellCardJobMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class SpellCardJobWorker {
    private static final Logger LOG = LoggerFactory.getLogger(SpellCardJobWorker.class);

    private final SpellCardGenerationService spellCardGenerationService;

    public SpellCardJobWorker(SpellCardGenerationService spellCardGenerationService) {
        this.spellCardGenerationService = spellCardGenerationService;
    }

    @RabbitListener(queues = "${spellcards.queue.name}")
    public void handle(SpellCardJobMessage jobMessage) {
        LOG.info("Consuming spell card job {} with {} spell(s)", jobMessage.jobId(), jobMessage.spellNames().size());
        spellCardGenerationService.generateForSpells(jobMessage.spellNames());
    }
}
