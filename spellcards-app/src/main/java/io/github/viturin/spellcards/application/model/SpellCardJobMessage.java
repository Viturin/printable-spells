package io.github.viturin.spellcards.application.model;

import java.util.List;

public record SpellCardJobMessage(String jobId, List<String> spellNames) {
}
