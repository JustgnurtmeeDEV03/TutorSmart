package com.dossier.auth.service.impl;

import com.dossier.auth.config.JwtProperties;
import com.dossier.auth.domain.AuthProvider;
import com.dossier.auth.domain.User;
import com.dossier.auth.domain.VerificationLevel;
import com.dossier.auth.dto.request.LoginRequest;
import com.dossier.auth.dto.request.RegisterRequest;
import com.dossier.auth.dto.response.AuthResponse;
import com.dossier.auth.exception.AuthException;
import com.dossier.auth.exception.UserAlreadyExistsException;
import com.dossier.auth.repository.RefreshTokenRepository;
import com.dossier.auth.repository.UserRepository;
import com.dossier.auth.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional // Mặc định cho write operations
public class AuthServiceImpl {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private final JwtProperties jwtProperties;

    @Override
    public AuthResponse register (RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException(
                    "Email already registered: " + request.email()
            );
        }

        User user = User.builder()
                .email(request.email().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName().trim())
                .provider(AuthProvider.LOCAL)
                .verificationLevel(VerificationLevel.NONE)
                .active(true)
                // Simplified: Bỏ qua email verification flow ở giai đoạn này
                // TODO: Gửi verfication email, set emaillVerified = false
                .emailVerified(false)
                .phoneVerified(false)
                .identityVerified(false)
                .build();

        user = userRepository.save(user);
        log.info("New user registered: id={}, email={}", user.getId(), user.getEmail());

        return buildAuthResponse(user, null, null);
    }

    @Override
    public AuthResponse login(LoginRequest request, String userAgent, String ipAddress){
        User user = userRepository.findByEmail(request.email().toLowerCase().trim())
                .orElseThrow( () -> new AuthException("Invalid credentials"));
        if (!user.isActive()) {
            throw new AuthException("Account has been suspended");
        }

        // Thông báo lỗi chung "Invalid credentials" thay vì "Wrong password"
        // để tránh user enumeration attack
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthException("Invalid credentials");
        }

        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);

        log.info("User logged in: id={}", user.getId());
        return buildAuthResponse(user, userAgent, ipAddress);
    }

}
