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
@Table(name = "ledger_entries")
public class LedgerEntry implements Persistable<UUID> {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LedgerDirection direction;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected LedgerEntry() {
    }

    private LedgerEntry(
            Transaction transaction,
            Wallet wallet,
            LedgerDirection direction,
            BigDecimal amount,
            BigDecimal balanceAfter
    ) {
        this.id = UUID.randomUUID();
        this.transaction = transaction;
        this.wallet = wallet;
        this.direction = direction;
        this.amount = amount.setScale(2, RoundingMode.HALF_EVEN);
        this.balanceAfter = balanceAfter.setScale(2, RoundingMode.HALF_EVEN);
        this.createdAt = Instant.now();
    }

    public static LedgerEntry debit(Transaction transaction, Wallet wallet, BigDecimal amount, BigDecimal balanceAfter) {
        return new LedgerEntry(transaction, wallet, LedgerDirection.DEBIT, amount, balanceAfter);
    }

    public static LedgerEntry credit(Transaction transaction, Wallet wallet, BigDecimal amount, BigDecimal balanceAfter) {
        return new LedgerEntry(transaction, wallet, LedgerDirection.CREDIT, amount, balanceAfter);
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

    public Transaction getTransaction() {
        return transaction;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public LedgerDirection getDirection() {
        return direction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
