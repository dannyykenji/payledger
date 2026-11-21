package com.payledger.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TransferEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TransferEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public TransferEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransferCompleted(TransferCompletedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EVENTS_EXCHANGE,
                    RabbitMQConfig.TRANSFER_COMPLETED_ROUTING_KEY,
                    event
            );
            log.info("Evento TransferCompletedEvent publicado no RabbitMQ: transactionId={}", event.transactionId());
        } catch (Exception ex) {
            log.error(
                    "Falha ao publicar TransferCompletedEvent no RabbitMQ: transactionId={}",
                    event.transactionId(),
                    ex
            );
        }
    }
}
