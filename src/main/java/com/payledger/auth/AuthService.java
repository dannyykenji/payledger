package com.payledger.auth;

import com.payledger.common.exception.ConflictException;
import com.payledger.common.exception.ResourceNotFoundException;
import com.payledger.config.JwtService;
import com.payledger.user.User;
import com.payledger.user.UserRepository;
import com.payledger.wallet.Wallet;
import com.payledger.wallet.WalletRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            WalletRepository walletRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Já existe um usuário cadastrado com este e-mail.");
        }
        if (userRepository.existsByDocument(request.document())) {
            throw new ConflictException("Já existe um usuário cadastrado com este CPF/CNPJ.");
        }

        User user = new User(
                request.name().trim(),
                normalizedEmail,
                request.document(),
                passwordEncoder.encode(request.password())
        );
        userRepository.save(user);

        Wallet wallet = new Wallet(user);
        walletRepository.save(wallet);

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(
                token,
                "Bearer",
                jwtService.getExpirationSeconds(),
                user.getId(),
                wallet.getId()
        );
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new BadCredentialsException("Credenciais inválidas."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Credenciais inválidas.");
        }

        Wallet wallet = walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Carteira não encontrada para o usuário."));

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(
                token,
                "Bearer",
                jwtService.getExpirationSeconds(),
                user.getId(),
                wallet.getId()
        );
    }
}
