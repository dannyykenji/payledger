package com.payledger.notification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferCompletedEvent(
        UUID transactionId,
        UUID payerWalletId,
        UUID payeeWalletId,
        BigDecimal amount,
        String description,
        Instant occurredAt
) {
}
