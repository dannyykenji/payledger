package com.payledger.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.payledger.common.exception.ConflictException;
import com.payledger.config.JwtService;
import com.payledger.user.User;
import com.payledger.user.UserRepository;
import com.payledger.wallet.Wallet;
import com.payledger.wallet.WalletRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                "cGF5bGVkZ2VyLXRlc3Qtc2VjcmV0LWtleS13aXRoLWF0LWxlYXN0LTMyLWJ5dGVz",
                86400000L
        );
        authService = new AuthService(userRepository, walletRepository, passwordEncoder, jwtService);
    }

    @Test
    void shouldRegisterUserAndCreateWallet() {
        RegisterRequest request = new RegisterRequest(
                "João Souza",
                "Joao@Example.com",
                "12345678901",
                "senhaSegura123"
        );

        when(userRepository.existsByEmail("joao@example.com")).thenReturn(false);
        when(userRepository.existsByDocument("12345678901")).thenReturn(false);
        when(passwordEncoder.encode("senhaSegura123")).thenReturn("bcrypt_hash");

        AuthResponse response = authService.register(request);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(jwtService.parseToken(response.accessToken())).isPresent();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(86400L);
        assertThat(response.userId()).isNotNull();
        assertThat(response.walletId()).isNotNull();
        verify(userRepository).save(any(User.class));
        verify(walletRepository).save(any(Wallet.class));
    }

    @Test
    void shouldRejectDuplicateEmail() {
        RegisterRequest request = new RegisterRequest(
                "João Souza",
                "joao@example.com",
                "12345678901",
                "senhaSegura123"
        );

        when(userRepository.existsByEmail("joao@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("e-mail");

        verify(walletRepository, never()).save(any());
    }

    @Test
    void shouldRejectInvalidPasswordOnLogin() {
        User user = new User("João Souza", "joao@example.com", "12345678901", "bcrypt_hash");
        when(userRepository.findByEmail("joao@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("errada", "bcrypt_hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("joao@example.com", "errada")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
