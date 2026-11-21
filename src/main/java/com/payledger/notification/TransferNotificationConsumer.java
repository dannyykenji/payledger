package com.payledger.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class TransferNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransferNotificationConsumer.class);

    @RabbitListener(queues = RabbitMQConfig.TRANSFER_NOTIFICATION_QUEUE)
    public void handleTransferNotification(TransferCompletedEvent event) {
        log.info(
                "Processando notificação de transferência: transactionId={}, payerWalletId={}, payeeWalletId={}, amount={}",
                event.transactionId(),
                event.payerWalletId(),
                event.payeeWalletId(),
                event.amount()
        );

        // Permite simular falha controlada em ambiente de demonstração para validar Retry + DLQ
        if (event.description() != null && event.description().contains("[FAIL_NOTIFICATION]")) {
            throw new IllegalStateException("Falha simulada no serviço externo de notificação");
        }

        log.info("Notificação enviada com sucesso para transactionId={}", event.transactionId());
    }
}
