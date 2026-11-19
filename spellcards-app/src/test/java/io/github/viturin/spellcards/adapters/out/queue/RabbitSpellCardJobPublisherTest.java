package io.github.viturin.spellcards.adapters.out.queue;

import io.github.viturin.spellcards.application.port.out.SpellCardJobPublicationException;
import io.github.viturin.spellcards.queue.model.SpellCardJobMessage;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class RabbitSpellCardJobPublisherTest {
    private static final String QUEUE = "spellcards.pdf.jobs";
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final RabbitSpellCardJobPublisher publisher = new RabbitSpellCardJobPublisher(rabbitTemplate, QUEUE);
    private final SpellCardJobMessage message = new SpellCardJobMessage(UUID.randomUUID(), List.of("Daze"));

    @Test
    void acceptsBrokerAcknowledgement() {
        respond(new CorrelationData.Confirm(true, null), null);

        publisher.publish(message);
    }

    @Test
    void considersAnUnroutableMessageRejectedEvenWithAnAcknowledgement() {
        ReturnedMessage returned = new ReturnedMessage(new Message(new byte[0]), 312, "NO_ROUTE", "", QUEUE);
        respond(new CorrelationData.Confirm(true, null), returned);

        var failure = assertThrows(SpellCardJobPublicationException.class, () -> publisher.publish(message));

        assertTrue(failure.isRejected());
    }

    @Test
    void distinguishesConnectionClosureFromBrokerRejection() {
        respond(new CorrelationData.Confirm(false, "Connection closed"), null);

        var failure = assertThrows(SpellCardJobPublicationException.class, () -> publisher.publish(message));

        assertFalse(failure.isRejected());
    }

    private void respond(CorrelationData.Confirm confirm, ReturnedMessage returned) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            if (returned != null) {
                correlation.setReturned(returned);
            }
            correlation.getFuture().complete(confirm);
            return null;
        }).when(rabbitTemplate).convertAndSend(eq(""), eq(QUEUE), eq(message), any(CorrelationData.class));
    }
}
