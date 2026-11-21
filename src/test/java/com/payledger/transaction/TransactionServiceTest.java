package com.payledger.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.payledger.common.exception.BusinessException;
import com.payledger.common.exception.ConflictException;
import com.payledger.notification.TransferCompletedEvent;
import com.payledger.user.User;
import com.payledger.wallet.Wallet;
import com.payledger.wallet.WalletRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(
                walletRepository,
                transactionRepository,
                ledgerEntryRepository,
                eventPublisher
        );
    }

    @Test
    void shouldDepositAndCreateCreditLedgerEntry() {
        User user = new User("Alice", "alice@example.com", "11111111111", "hash");
        Wallet wallet = new Wallet(user);

        when(walletRepository.findIdByUserId(user.getId())).thenReturn(Optional.of(wallet.getId()));
        when(transactionRepository.findByIdempotencyKey("dep-001")).thenReturn(Optional.empty());
        when(walletRepository.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));

        TransactionResponse response = transactionService.deposit(
                user.getId(),
                "dep-001",
                new DepositRequest(new BigDecimal("250.00"), "Depósito inicial")
        );

        assertThat(response.type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(response.amount()).isEqualByComparingTo("250.00");
        assertThat(wallet.getBalance()).isEqualByComparingTo("250.00");
        verify(transactionRepository).save(any(Transaction.class));
        verify(ledgerEntryRepository).save(any(LedgerEntry.class));
    }

    @Test
    void shouldReturnExistingTransactionWhenIdempotencyKeyMatches() {
        User user = new User("Alice", "alice@example.com", "11111111111", "hash");
        Wallet wallet = new Wallet(user);
        Transaction existing = Transaction.deposit("dep-001", wallet, new BigDecimal("100.00"), "Depósito");

        when(walletRepository.findIdByUserId(user.getId())).thenReturn(Optional.of(wallet.getId()));
        when(transactionRepository.findByIdempotencyKey("dep-001")).thenReturn(Optional.of(existing));

        TransactionResponse response = transactionService.deposit(
                user.getId(),
                "dep-001",
                new DepositRequest(new BigDecimal("100.00"), "Depósito")
        );

        assertThat(response.transactionId()).isEqualTo(existing.getId());
        verify(walletRepository, never()).findByIdForUpdate(any());
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    void shouldTransferWithDeterministicLockOrderAndDoubleEntryLedger() {
        User alice = new User("Alice", "alice@example.com", "11111111111", "hash");
        User bob = new User("Bob", "bob@example.com", "22222222222", "hash");
        Wallet aliceWallet = new Wallet(alice);
        Wallet bobWallet = new Wallet(bob);
        aliceWallet.credit(new BigDecimal("500.00"));

        when(walletRepository.findIdByUserId(alice.getId())).thenReturn(Optional.of(aliceWallet.getId()));
        when(transactionRepository.findByIdempotencyKey("trf-001")).thenReturn(Optional.empty());
        when(walletRepository.findByIdForUpdate(aliceWallet.getId())).thenReturn(Optional.of(aliceWallet));
        when(walletRepository.findByIdForUpdate(bobWallet.getId())).thenReturn(Optional.of(bobWallet));

        TransactionResponse response = transactionService.transfer(
                alice.getId(),
                "trf-001",
                new TransferRequest(bobWallet.getId(), new BigDecimal("125.50"), "Almoço")
        );

        assertThat(response.type()).isEqualTo(TransactionType.TRANSFER);
        assertThat(aliceWallet.getBalance()).isEqualByComparingTo("374.50");
        assertThat(bobWallet.getBalance()).isEqualByComparingTo("125.50");

        // Verifica que os locks foram adquiridos na ordem do menor UUID para o maior (anti-deadlock)
        UUID firstId = aliceWallet.getId().compareTo(bobWallet.getId()) < 0
                ? aliceWallet.getId()
                : bobWallet.getId();
        UUID secondId = firstId.equals(aliceWallet.getId()) ? bobWallet.getId() : aliceWallet.getId();

        InOrder inOrder = inOrder(walletRepository);
        inOrder.verify(walletRepository).findByIdForUpdate(firstId);
        inOrder.verify(walletRepository).findByIdForUpdate(secondId);

        verify(transactionRepository).save(any(Transaction.class));
        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
        verify(eventPublisher).publishEvent(any(TransferCompletedEvent.class));
    }

    @Test
    void shouldRejectSelfTransfer() {
        User alice = new User("Alice", "alice@example.com", "11111111111", "hash");
        Wallet aliceWallet = new Wallet(alice);

        when(walletRepository.findIdByUserId(alice.getId())).thenReturn(Optional.of(aliceWallet.getId()));
        when(transactionRepository.findByIdempotencyKey("trf-self")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.transfer(
                alice.getId(),
                "trf-self",
                new TransferRequest(aliceWallet.getId(), new BigDecimal("10.00"), "Teste")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("própria carteira");

        verify(walletRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void shouldRejectIdempotencyKeyUsedByAnotherOperation() {
        User alice = new User("Alice", "alice@example.com", "11111111111", "hash");
        User bob = new User("Bob", "bob@example.com", "22222222222", "hash");
        Wallet aliceWallet = new Wallet(alice);
        Wallet bobWallet = new Wallet(bob);
        Transaction bobDeposit = Transaction.deposit("shared-key", bobWallet, new BigDecimal("50.00"), "Bob");

        when(walletRepository.findIdByUserId(alice.getId())).thenReturn(Optional.of(aliceWallet.getId()));
        when(transactionRepository.findByIdempotencyKey("shared-key")).thenReturn(Optional.of(bobDeposit));

        assertThatThrownBy(() -> transactionService.deposit(
                alice.getId(),
                "shared-key",
                new DepositRequest(new BigDecimal("50.00"), "Alice")
        ))
                .isInstanceOf(ConflictException.class);
    }
}
