package io.github.viturin.spellcards.worker;

import io.github.viturin.spellcards.adapters.out.foundry.FoundryJsonSpellRepository;
import io.github.viturin.spellcards.adapters.out.filesystem.FileSpellCardJobStore;
import io.github.viturin.spellcards.application.service.GenerateSpellCardsService;
import io.github.viturin.spellcards.queue.config.SpellCardQueueConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackageClasses = {
        SpellCardsWorkerApplication.class,
        GenerateSpellCardsService.class,
        FoundryJsonSpellRepository.class,
        FileSpellCardJobStore.class,
        SpellCardQueueConfiguration.class
})
@EnableRabbit
@EnableScheduling
public class SpellCardsWorkerApplication {
    static void main(String[] args) {
        SpringApplication.run(SpellCardsWorkerApplication.class, args);
    }
}
