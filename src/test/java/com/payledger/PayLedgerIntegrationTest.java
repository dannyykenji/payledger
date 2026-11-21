package com.payledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.payledger.auth.AuthResponse;
import com.payledger.auth.RegisterRequest;
import com.payledger.transaction.DepositRequest;
import com.payledger.transaction.StatementPageResponse;
import com.payledger.transaction.TransactionResponse;
import com.payledger.transaction.TransferRequest;
import com.payledger.wallet.WalletResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestPropertySource(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class PayLedgerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldCompleteFullFlowFromRegistrationToStatement() {
        AuthResponse alice = registerUser("Alice E2E", "alice.e2e@payledger.dev", "10000000001");
        AuthResponse bob = registerUser("Bob E2E", "bob.e2e@payledger.dev", "10000000002");

        // Depósito de 300.00 para Alice
        ResponseEntity<TransactionResponse> depositResp = deposit(
                alice.accessToken(),
                "e2e-dep-1",
                new BigDecimal("300.00")
        );
        assertThat(depositResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Transferência de 120.50 de Alice para Bob
        ResponseEntity<TransactionResponse> transferResp = transfer(
                alice.accessToken(),
                "e2e-trf-1",
                bob.walletId(),
                new BigDecimal("120.50")
        );
        assertThat(transferResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Valida saldos finais
        assertThat(getWallet(alice.accessToken()).balance()).isEqualByComparingTo("179.50");
        assertThat(getWallet(bob.accessToken()).balance()).isEqualByComparingTo("120.50");

        // Valida extrato da Alice (2 entradas: crédito de 300.00 e débito de 120.50)
        StatementPageResponse statement = getStatement(alice.accessToken());
        assertThat(statement.totalElements()).isEqualTo(2);
        assertThat(statement.content().getFirst().balanceAfter()).isEqualByComparingTo("179.50");
    }

    @Test
    void shouldBeIdempotentWhenSameIdempotencyKeyIsSentTwice() {
        AuthResponse alice = registerUser("Alice Idemp", "alice.idemp@payledger.dev", "20000000001");
        AuthResponse bob = registerUser("Bob Idemp", "bob.idemp@payledger.dev", "20000000002");

        deposit(alice.accessToken(), "idemp-dep-1", new BigDecimal("200.00"));

        ResponseEntity<TransactionResponse> first = transfer(
                alice.accessToken(),
                "same-key-001",
                bob.walletId(),
                new BigDecimal("50.00")
        );
        ResponseEntity<TransactionResponse> second = transfer(
                alice.accessToken(),
                "same-key-001",
                bob.walletId(),
                new BigDecimal("50.00")
        );

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody().transactionId()).isEqualTo(first.getBody().transactionId());
        assertThat(getWallet(alice.accessToken()).balance()).isEqualByComparingTo("150.00");
        assertThat(getWallet(bob.accessToken()).balance()).isEqualByComparingTo("50.00");
    }

    @Test
    void shouldPreventOverspendingUnderHighConcurrency() throws Exception {
        AuthResponse alice = registerUser("Alice Conc", "alice.conc@payledger.dev", "30000000001");
        AuthResponse bob = registerUser("Bob Conc", "bob.conc@payledger.dev", "30000000002");

        // Saldo inicial: 100.00
        deposit(alice.accessToken(), "conc-dep-1", new BigDecimal("100.00"));

        // 10 threads simultâneas tentando transferir 20.00 cada (total tentado: 200.00)
        int totalThreads = 10;
        CountDownLatch readyLatch = new CountDownLatch(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);

        List<Future<HttpStatus>> futures = new ArrayList<>();
        for (int i = 0; i < totalThreads; i++) {
            String key = "conc-trf-" + UUID.randomUUID();
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                ResponseEntity<String> response = restTemplate.exchange(
                        "/api/v1/transfers",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                new TransferRequest(bob.walletId(), new BigDecimal("20.00"), "Concorrência"),
                                bearerHeadersWithIdempotency(alice.accessToken(), key)
                        ),
                        String.class
                );
                return HttpStatus.valueOf(response.getStatusCode().value());
            }));
        }

        readyLatch.await();
        startLatch.countDown();

        int succeeded = 0;
        int rejected = 0;
        for (Future<HttpStatus> future : futures) {
            HttpStatus status = future.get();
            if (status == HttpStatus.CREATED) {
                succeeded++;
            } else if (status == HttpStatus.UNPROCESSABLE_ENTITY) {
                rejected++;
            }
        }
        executor.shutdown();

        // Exatamente 5 transferências de 20.00 podem passar; as outras 5 devem falhar por saldo insuficiente
        assertThat(succeeded).isEqualTo(5);
        assertThat(rejected).isEqualTo(5);
        assertThat(getWallet(alice.accessToken()).balance()).isEqualByComparingTo("0.00");
        assertThat(getWallet(bob.accessToken()).balance()).isEqualByComparingTo("100.00");
    }

    private AuthResponse registerUser(String name, String email, String document) {
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "/api/v1/auth/register",
                new RegisterRequest(name, email, document, "senhaSegura123"),
                AuthResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private ResponseEntity<TransactionResponse> deposit(String token, String idempotencyKey, BigDecimal amount) {
        return restTemplate.exchange(
                "/api/v1/wallets/deposit",
                HttpMethod.POST,
                new HttpEntity<>(
                        new DepositRequest(amount, "Depósito teste"),
                        bearerHeadersWithIdempotency(token, idempotencyKey)
                ),
                TransactionResponse.class
        );
    }

    private ResponseEntity<TransactionResponse> transfer(
            String token,
            String idempotencyKey,
            UUID payeeWalletId,
            BigDecimal amount
    ) {
        return restTemplate.exchange(
                "/api/v1/transfers",
                HttpMethod.POST,
                new HttpEntity<>(
                        new TransferRequest(payeeWalletId, amount, "Transferência teste"),
                        bearerHeadersWithIdempotency(token, idempotencyKey)
                ),
                TransactionResponse.class
        );
    }

    private WalletResponse getWallet(String token) {
        ResponseEntity<WalletResponse> response = restTemplate.exchange(
                "/api/v1/wallets/me",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)),
                WalletResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private StatementPageResponse getStatement(String token) {
        ResponseEntity<StatementPageResponse> response = restTemplate.exchange(
                "/api/v1/wallets/me/statement",
                HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(token)),
                StatementPageResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpHeaders bearerHeadersWithIdempotency(String token, String idempotencyKey) {
        HttpHeaders headers = bearerHeaders(token);
        headers.set("Idempotency-Key", idempotencyKey);
        return headers;
    }
}
