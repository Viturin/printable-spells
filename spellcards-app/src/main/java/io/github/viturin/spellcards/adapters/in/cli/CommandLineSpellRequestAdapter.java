package io.github.viturin.spellcards.adapters.in.cli;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class CommandLineSpellRequestAdapter {
    public List<String> parseSpellNames(String[] args) {
        if (args == null || args.length == 0) {
            return List.of("Daze");
        }
        return Arrays.stream(args)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}
