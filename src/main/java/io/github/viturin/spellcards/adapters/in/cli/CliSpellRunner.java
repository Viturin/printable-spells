package io.github.viturin.spellcards.adapters.in.cli;

import io.github.viturin.spellcards.application.port.in.GenerateSpellCardsUseCase;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class CliSpellRunner implements CommandLineRunner {
    private final CommandLineSpellRequestAdapter requestAdapter;
    private final GenerateSpellCardsUseCase useCase;

    public CliSpellRunner(CommandLineSpellRequestAdapter requestAdapter, GenerateSpellCardsUseCase useCase) {
        this.requestAdapter = requestAdapter;
        this.useCase = useCase;
    }

    @Override
    public void run(String @NonNull ... args) {
        useCase.generateForSpells(requestAdapter.parseSpellNames(args));
    }
}
