package com.payledger.wallet;

import com.payledger.common.exception.ResourceNotFoundException;
import com.payledger.config.AuthenticatedUser;
import com.payledger.transaction.DepositRequest;
import com.payledger.transaction.LedgerDirection;
import com.payledger.transaction.StatementPageResponse;
import com.payledger.transaction.TransactionResponse;
import com.payledger.transaction.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets")
@Tag(name = "Wallets", description = "Consulta de carteira, depósito e extrato via ledger")
@SecurityRequirement(name = "bearerAuth")
public class WalletController {

    private final WalletRepository walletRepository;
    private final TransactionService transactionService;

    public WalletController(WalletRepository walletRepository, TransactionService transactionService) {
        this.walletRepository = walletRepository;
        this.transactionService = transactionService;
    }

    @GetMapping("/me")
    @Operation(summary = "Retorna a carteira e o saldo do usuário autenticado")
    public WalletResponse getMyWallet(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        Wallet wallet = walletRepository.findByUserId(currentUser.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Carteira não encontrada."));
        return WalletResponse.from(wallet);
    }

    @PostMapping("/deposit")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Realiza um depósito na carteira do usuário autenticado")
    public TransactionResponse deposit(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Parameter(description = "Chave única para garantir idempotência (ex: UUID)", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody DepositRequest request
    ) {
        return transactionService.deposit(currentUser.userId(), idempotencyKey, request);
    }

    @GetMapping("/me/statement")
    @Operation(summary = "Retorna o extrato paginado da carteira a partir do ledger imutável")
    public StatementPageResponse getStatement(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) LedgerDirection direction,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return transactionService.getStatement(currentUser.userId(), direction, from, to, page, size);
    }
}
