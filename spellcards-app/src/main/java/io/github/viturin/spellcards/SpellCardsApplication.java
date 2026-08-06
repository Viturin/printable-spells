package io.github.viturin.spellcards;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SpellCardsApplication {
    static void main(String[] args) {
        SpringApplication.run(SpellCardsApplication.class, args);
    }
}
