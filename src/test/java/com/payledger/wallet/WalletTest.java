package com.payledger.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.payledger.common.exception.BusinessException;
import com.payledger.user.User;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class WalletTest {

    @Test
    void shouldStartWithZeroBalance() {
        Wallet wallet = newWallet();
        assertThat(wallet.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldCreditAndDebitProperly() {
        Wallet wallet = newWallet();

        wallet.credit(new BigDecimal("150.50"));
        wallet.debit(new BigDecimal("49.25"));

        assertThat(wallet.getBalance()).isEqualByComparingTo("101.25");
    }

    @Test
    void shouldRejectDebitWhenBalanceIsInsufficient() {
        Wallet wallet = newWallet();
        wallet.credit(new BigDecimal("20.00"));

        assertThatThrownBy(() -> wallet.debit(new BigDecimal("20.01")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Saldo insuficiente");
    }

    @Test
    void shouldRejectZeroOrNegativeAmounts() {
        Wallet wallet = newWallet();

        assertThatThrownBy(() -> wallet.credit(BigDecimal.ZERO))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> wallet.credit(new BigDecimal("-10.00")))
                .isInstanceOf(BusinessException.class);
    }

    private Wallet newWallet() {
        User user = new User("Maria Silva", "maria@example.com", "12345678901", "hashed");
        return new Wallet(user);
    }
}
