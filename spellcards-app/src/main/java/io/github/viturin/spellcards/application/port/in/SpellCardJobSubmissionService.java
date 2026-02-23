package io.github.viturin.spellcards.application.port.in;

import java.util.List;

public interface SpellCardJobSubmissionService {
    String submit(List<String> spellNames);
}
