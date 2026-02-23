package io.github.viturin.spellcards.application.model;

import java.util.List;
import java.util.UUID;

public record SpellCardJobMessage(UUID jobId, List<String> spellNames) {
}
