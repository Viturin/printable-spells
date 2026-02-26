package io.github.viturin.spellcards.application.port.out;

import io.github.viturin.spellcards.queue.model.SpellCardJobMessage;

public interface SpellCardJobPublisher {
    void publish(SpellCardJobMessage message);
}
