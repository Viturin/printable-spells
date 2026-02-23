package io.github.viturin.spellcards.adapters.out.queue;

import io.github.viturin.spellcards.application.model.SpellCardJobMessage;
import io.github.viturin.spellcards.application.port.out.SpellCardJobPublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
        rabbitTemplate.convertAndSend(queueName, message);
    }
}
