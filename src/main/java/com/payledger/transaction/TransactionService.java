package com.payledger.transaction;

import com.payledger.common.exception.BusinessException;
import com.payledger.common.exception.ConflictException;
import com.payledger.common.exception.ResourceNotFoundException;
import com.payledger.notification.TransferCompletedEvent;
import com.payledger.wallet.Wallet;
import com.payledger.wallet.WalletRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;
    private static final int MAX_PAGE_SIZE = 100;

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TransactionService(
            WalletRepository walletRepository,
            TransactionRepository transactionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public TransactionResponse deposit(UUID userId, String idempotencyKey, DepositRequest request) {
        String normalizedKey = validateIdempotencyKey(idempotencyKey);

        UUID walletId = walletRepository.findIdByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Carteira não encontrada."));

        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(normalizedKey);
        if (existing.isPresent()) {
            Transaction tx = existing.get();
            if (!tx.getPayeeWallet().getId().equals(walletId) || tx.getType() != TransactionType.DEPOSIT) {
                throw new ConflictException("Idempotency-Key já utilizada em outra operação.");
            }
            return TransactionResponse.from(tx);
        }

        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Carteira não encontrada."));

        wallet.credit(request.amount());

        Transaction transaction = Transaction.deposit(
                normalizedKey,
                wallet,
                request.amount(),
                request.description()
        );
        transactionRepository.save(transaction);

        LedgerEntry creditEntry = LedgerEntry.credit(
                transaction,
                wallet,
                request.amount(),
                wallet.getBalance()
        );
        ledgerEntryRepository.save(creditEntry);

        return TransactionResponse.from(transaction);
    }

    @Transactional
    public TransactionResponse transfer(UUID payerUserId, String idempotencyKey, TransferRequest request) {
        String normalizedKey = validateIdempotencyKey(idempotencyKey);

        UUID payerWalletId = walletRepository.findIdByUserId(payerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Carteira do pagador não encontrada."));

        UUID payeeWalletId = request.payeeWalletId();

        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(normalizedKey);
        if (existing.isPresent()) {
            Transaction tx = existing.get();
            if (tx.getPayerWallet() == null
                    || !tx.getPayerWallet().getId().equals(payerWalletId)
                    || !tx.getPayeeWallet().getId().equals(payeeWalletId)
                    || tx.getType() != TransactionType.TRANSFER) {
                throw new ConflictException("Idempotency-Key já utilizada em outra operação.");
            }
            return TransactionResponse.from(tx);
        }

        if (payerWalletId.equals(payeeWalletId)) {
            throw new BusinessException("Não é possível transferir para a própria carteira.");
        }

        // Adquire locks pessimistas em ordem determinística (menor UUID primeiro) para prevenir deadlocks
        Wallet payerWallet;
        Wallet payeeWallet;
        if (payerWalletId.compareTo(payeeWalletId) < 0) {
            payerWallet = lockWallet(payerWalletId, "Carteira do pagador não encontrada.");
            payeeWallet = lockWallet(payeeWalletId, "Carteira de destino não encontrada.");
        } else {
            payeeWallet = lockWallet(payeeWalletId, "Carteira de destino não encontrada.");
            payerWallet = lockWallet(payerWalletId, "Carteira do pagador não encontrada.");
        }

        payerWallet.debit(request.amount());
        payeeWallet.credit(request.amount());

        Transaction transaction = Transaction.transfer(
                normalizedKey,
                payerWallet,
                payeeWallet,
                request.amount(),
                request.description()
        );
        transactionRepository.save(transaction);

        // Partida dobrada (Double-Entry Ledger): um débito no pagador e um crédito no recebedor
        LedgerEntry debitEntry = LedgerEntry.debit(
                transaction,
                payerWallet,
                request.amount(),
                payerWallet.getBalance()
        );
        LedgerEntry creditEntry = LedgerEntry.credit(
                transaction,
                payeeWallet,
                request.amount(),
                payeeWallet.getBalance()
        );
        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);

        // Evento de domínio: será publicado no RabbitMQ somente após o COMMIT da transação no PostgreSQL
        eventPublisher.publishEvent(new TransferCompletedEvent(
                transaction.getId(),
                payerWallet.getId(),
                payeeWallet.getId(),
                transaction.getAmount(),
                transaction.getDescription(),
                transaction.getCreatedAt()
        ));

        return TransactionResponse.from(transaction);
    }

    @Transactional(readOnly = true)
    public StatementPageResponse getStatement(
            UUID userId,
            LedgerDirection direction,
            Instant from,
            Instant to,
            int page,
            int size
    ) {
        UUID walletId = walletRepository.findIdByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Carteira não encontrada."));

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<LedgerEntry> spec = (root, query, cb) -> cb.equal(root.get("wallet").get("id"), walletId);

        if (direction != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("direction"), direction));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }

        Page<LedgerEntry> entries = ledgerEntryRepository.findAll(spec, pageable);
        return StatementPageResponse.from(entries);
    }

    private Wallet lockWallet(UUID walletId, String notFoundMessage) {
        return walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new ResourceNotFoundException(notFoundMessage));
    }

    private String validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException("O header Idempotency-Key é obrigatório.");
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessException("O header Idempotency-Key deve ter no máximo 100 caracteres.");
        }
        return trimmed;
    }
}
