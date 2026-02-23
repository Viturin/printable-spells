package io.github.viturin.spellcards.application.port.in;

import java.util.List;
import java.util.UUID;

public interface SpellCardJobSubmissionService {
    UUID submit(List<String> spellNames);
}
