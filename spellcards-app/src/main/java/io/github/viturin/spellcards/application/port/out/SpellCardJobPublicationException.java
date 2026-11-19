package io.github.viturin.spellcards.application.port.out;

public class SpellCardJobPublicationException extends RuntimeException {
    private final boolean rejected;

    public SpellCardJobPublicationException(boolean rejected, Throwable cause) {
        super(rejected ? "Unable to queue spell card generation" : "Unable to confirm spell card generation was queued",
                cause);
        this.rejected = rejected;
    }

    public boolean isRejected() {
        return rejected;
    }
}
