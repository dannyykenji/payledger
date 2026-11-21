package com.payledger.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StatementEntryResponse(
        UUID entryId,
        UUID transactionId,
        TransactionType transactionType,
        LedgerDirection direction,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String description,
        Instant createdAt
) {
    public static StatementEntryResponse from(LedgerEntry entry) {
        return new StatementEntryResponse(
                entry.getId(),
                entry.getTransaction().getId(),
                entry.getTransaction().getType(),
                entry.getDirection(),
                entry.getAmount(),
                entry.getBalanceAfter(),
                entry.getTransaction().getDescription(),
                entry.getCreatedAt()
        );
    }
}
