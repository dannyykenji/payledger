package com.payledger.transaction;

import com.payledger.wallet.Wallet;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "transactions")
public class Transaction implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payer_wallet_id")
    private Wallet payerWallet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payee_wallet_id", nullable = false)
    private Wallet payeeWallet;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected Transaction() {
    }

    private Transaction(
            String idempotencyKey,
            Wallet payerWallet,
            Wallet payeeWallet,
            BigDecimal amount,
            TransactionType type,
            TransactionStatus status,
            String description
    ) {
        this.id = UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.payerWallet = payerWallet;
        this.payeeWallet = payeeWallet;
        this.amount = amount.setScale(2, RoundingMode.HALF_EVEN);
        this.type = type;
        this.status = status;
        this.description = description;
        this.createdAt = Instant.now();
    }

    public static Transaction deposit(String idempotencyKey, Wallet payeeWallet, BigDecimal amount, String description) {
        return new Transaction(
                idempotencyKey,
                null,
                payeeWallet,
                amount,
                TransactionType.DEPOSIT,
                TransactionStatus.COMPLETED,
                description
        );
    }

    public static Transaction transfer(
            String idempotencyKey,
            Wallet payerWallet,
            Wallet payeeWallet,
            BigDecimal amount,
            String description
    ) {
        return new Transaction(
                idempotencyKey,
                payerWallet,
                payeeWallet,
                amount,
                TransactionType.TRANSFER,
                TransactionStatus.COMPLETED,
                description
        );
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    private void markNotNew() {
        this.isNew = false;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Wallet getPayerWallet() {
        return payerWallet;
    }

    public Wallet getPayeeWallet() {
        return payeeWallet;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
