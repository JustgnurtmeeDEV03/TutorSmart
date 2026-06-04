package com.dossier.auth.service.impl;

import com.dossier.auth.config.JwtProperties;
import com.dossier.auth.domain.AuthProvider;
import com.dossier.auth.domain.RefreshToken;
import com.dossier.auth.domain.User;
import com.dossier.auth.domain.VerificationLevel;
import com.dossier.auth.dto.request.LoginRequest;
import com.dossier.auth.dto.request.RegisterRequest;
import com.dossier.auth.dto.response.AuthResponse;
import com.dossier.auth.dto.response.UserResponse;
import com.dossier.auth.exception.AuthException;
import com.dossier.auth.exception.TokenExpiredException;
import com.dossier.auth.exception.UserAlreadyExistsException;
import com.dossier.auth.repository.RefreshTokenRepository;
import com.dossier.auth.repository.UserRepository;
import com.dossier.auth.service.AuthService;
import com.dossier.auth.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional // Mặc định cho write operations
public class AuthServiceImpl implements AuthService {

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

    @Override public AuthResponse refreshToken(String rawRefreshToken, String userAgent, String ipAddress) {
        // Bước 1: Validate JWT structure và chữ ký
        if (!jwtService.isTokenValid(rawRefreshToken)) {
            throw new TokenExpiredException("Refresh token is expired or invalid");
        }

        // Bước 2: Tìm trong DB bằng hash
        String tokenHash = jwtService.hashToken(rawRefreshToken);
        RefreshToken storedToken = refreshTokenRepository
                .findByTokenHashAndRevokedFalse(tokenHash)
                .orElseThrow(() -> new AuthException("Refresh token not found or already revoked"));

        // Bước 3: Double-check expiry (DB là source of truth)
        if (storedToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            storedToken.setRevoked(true);
            storedToken.setRevokedAt(OffsetDateTime.now());
            refreshTokenRepository.save(storedToken);
            throw new TokenExpiredException("Refresh token has expired");
        }

        // Bước 4: Token Rotation - Revoke token cũ, issue cặp token mới
        // Token rotation đảm bảo mỗi refresh token chỉ dùng 1 lần
        // Nếu token cũ được dùng lại -> ai đó đang replay -> revoke all
        storedToken.setRevoked(true);
        storedToken.setRevokedAt(OffsetDateTime.now());
        refreshTokenRepository.save(storedToken);

        User user = storedToken.getUser();
        log.info("Token refreshed for user: id={}", user.getId());

        return buildAuthResponse(user, userAgent, ipAddress);
    }

    @Override
    public void logout(String rawRefreshToken) {
        String tokenHash = jwtService.hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    token.setRevokedAt(OffsetDateTime.now());
                    refreshTokenRepository.save(token);
                    log.info("Token revoked for user: id= {}", token.getUser().getId());
                });
        // Không throw exception nếu token không tìm thấy - idempotent operation
    }

    @Override
    public void logoutAllDevices(String rawAccessToken) {
        UUID userId = jwtService.extractUserId(rawAccessToken);
        refreshTokenRepository.revokeAllByUserId(userId, OffsetDateTime.now());
        log.info("All tokens revoked for user: id={}", userId);
    }

    // Private helper: tạo token pair và lưu refresh token vào DB
    private AuthResponse buildAuthResponse(User user, String userAgent, String ipAddress)
    {
        String accessToken = jwtService.generateAccessToken(
                user.getId(), user.getEmail()
        );

        String rawRefreshToken = jwtService.generateRefreshToken(
                user.getId(), user.getEmail()
        );

        // Chuyển milliseconds sang OffsetDateTime
        long refreshExpiryMs = jwtProperties.getRefreshTokenExpiry();
        OffsetDateTime expiresAt = OffsetDateTime.now()
                .plusNanos(refreshExpiryMs * 1_000_000L);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(jwtService.hashToken(rawRefreshToken))
                .userAgent(userAgent)
                .ipAddress(ipAddress)
                .expiresAt(expiresAt)
                .build();

        refreshTokenRepository.save(refreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .expiresIn(jwtProperties.getAccessTokenExpiry() / 1000L)
                .user(UserResponse.from(user))
                .build();
    }
}
