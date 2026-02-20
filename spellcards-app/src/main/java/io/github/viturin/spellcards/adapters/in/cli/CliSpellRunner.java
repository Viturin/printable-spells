package io.github.viturin.spellcards.adapters.in.cli;

import io.github.viturin.spellcards.application.port.in.SpellCardGenerationService;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class CliSpellRunner implements CommandLineRunner {
    private final CommandLineSpellRequestAdapter requestAdapter;
    private final SpellCardGenerationService spellCardGenerationService;

    public CliSpellRunner(CommandLineSpellRequestAdapter requestAdapter, SpellCardGenerationService spellCardGenerationService) {
        this.requestAdapter = requestAdapter;
        this.spellCardGenerationService = spellCardGenerationService;
    }

    @Override
    public void run(String @NonNull ... args) {
        spellCardGenerationService.generateForSpells(requestAdapter.parseSpellNames(args));
    }
}
