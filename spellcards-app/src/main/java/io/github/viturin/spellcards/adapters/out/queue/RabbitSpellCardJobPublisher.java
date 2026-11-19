package io.github.viturin.spellcards.adapters.out.queue;

import io.github.viturin.spellcards.application.port.out.SpellCardJobPublicationException;
import io.github.viturin.spellcards.application.port.out.SpellCardJobPublisher;
import io.github.viturin.spellcards.queue.model.SpellCardJobMessage;
import org.springframework.amqp.AmqpAuthenticationException;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class RabbitSpellCardJobPublisher implements SpellCardJobPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final String queueName;

    public RabbitSpellCardJobPublisher(RabbitTemplate rabbitTemplate,
                                       @Value("${spellcards.queue.name}") String queueName) {
        this.rabbitTemplate = rabbitTemplate;
        this.queueName = queueName;
    }

    @Override
    public void publish(SpellCardJobMessage message) {
        CorrelationData correlation = new CorrelationData(message.jobId().toString());
        CorrelationData.Confirm confirm;
        try {
            rabbitTemplate.convertAndSend("", queueName, message, correlation);
            confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
        } catch (AmqpAuthenticationException | AmqpConnectException exception) {
            throw new SpellCardJobPublicationException(true, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SpellCardJobPublicationException(false, exception);
        } catch (AmqpException | ExecutionException | TimeoutException exception) {
            throw new SpellCardJobPublicationException(false, exception);
        }

        if (correlation.getReturned() != null || (!confirm.ack() && confirm.reason() == null)) {
            throw new SpellCardJobPublicationException(true, null);
        }
        if (!confirm.ack()) {
            // An outstanding confirm can be nacked locally when the connection closes.
            throw new SpellCardJobPublicationException(false, null);
        }
    }
}
