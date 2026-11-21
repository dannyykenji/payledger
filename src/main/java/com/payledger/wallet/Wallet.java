package com.payledger.wallet;

import com.payledger.common.exception.BusinessException;
import com.payledger.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Wallet() {
    }

    public Wallet(User user) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.balance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void credit(BigDecimal amount) {
        validatePositive(amount);
        this.balance = this.balance.add(normalize(amount));
        this.updatedAt = Instant.now();
    }

    public void debit(BigDecimal amount) {
        validatePositive(amount);
        BigDecimal normalized = normalize(amount);
        if (this.balance.compareTo(normalized) < 0) {
            throw new BusinessException("Saldo insuficiente para realizar a operação.");
        }
        this.balance = this.balance.subtract(normalized);
        this.updatedAt = Instant.now();
    }

    private void validatePositive(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("O valor da operação deve ser maior que zero.");
        }
    }

    private BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_EVEN);
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
