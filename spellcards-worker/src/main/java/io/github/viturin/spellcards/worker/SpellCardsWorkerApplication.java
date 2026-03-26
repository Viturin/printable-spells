package io.github.viturin.spellcards.worker;

import io.github.viturin.spellcards.adapters.out.foundry.FoundryJsonSpellRepository;
import io.github.viturin.spellcards.application.service.GenerateSpellCardsService;
import io.github.viturin.spellcards.queue.config.SpellCardQueueConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

@SpringBootApplication(scanBasePackageClasses = {
        SpellCardsWorkerApplication.class,
        GenerateSpellCardsService.class,
        FoundryJsonSpellRepository.class,
        SpellCardQueueConfiguration.class
})
@EnableRabbit
public class SpellCardsWorkerApplication {
    static void main(String[] args) {
        SpringApplication.run(SpellCardsWorkerApplication.class, args);
    }
}
