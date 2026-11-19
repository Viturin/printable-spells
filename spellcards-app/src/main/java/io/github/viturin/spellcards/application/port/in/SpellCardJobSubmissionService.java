package io.github.viturin.spellcards.application.port.in;

import io.github.viturin.spellcards.application.model.SpellCardPdfLayout;

import java.util.List;
import java.util.UUID;

public interface SpellCardJobSubmissionService {
    UUID submit(List<String> spellNames, SpellCardPdfLayout layout);
}
