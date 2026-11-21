package com.payledger.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID transactionId,
        String idempotencyKey,
        UUID payerWalletId,
        UUID payeeWalletId,
        BigDecimal amount,
        TransactionType type,
        TransactionStatus status,
        String description,
        Instant createdAt
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getIdempotencyKey(),
                transaction.getPayerWallet() != null ? transaction.getPayerWallet().getId() : null,
                transaction.getPayeeWallet().getId(),
                transaction.getAmount(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getDescription(),
                transaction.getCreatedAt()
        );
    }
}
