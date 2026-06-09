package com.dossier.api.controller;


import com.dossier.auth.dto.request.LoginRequest;
import com.dossier.auth.dto.request.RefreshTokenRequest;
import com.dossier.auth.dto.request.RegisterRequest;
import com.dossier.auth.dto.response.AuthResponse;
import com.dossier.auth.dto.response.UserResponse;
import com.dossier.auth.security.UserPrincipal;
import com.dossier.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, token management")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new account")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password")
    public AuthResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest httpRequest
    ) {
        return authService.login(
                request,
                httpRequest.getHeader("User-Agent"),
                extractClientIp(httpRequest)
        );
    }

    @PostMapping("/refresh")
    @Operation(summary = "Issue new token pair using refresh token")
    public AuthResponse refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest
    ) {
        return authService.refreshToken(
                request.refreshToken(),
                httpRequest.getHeader("User-Agent"),
                extractClientIp(httpRequest)
        );
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke refresh token (current device)")
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Revoke all refresh tokens (all devices)")
    public void logoutAllDevices(
            @RequestHeader("Authorization") String authHeader
    ) {
        String rawToken = authHeader.substring(7);
        authService.logoutAllDevices(rawToken);
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get current authenticated user info")
    public UserResponse getCurrentUser(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return UserResponse.from(principal.getUser());
    }

    // X-Forwarded-For: khi đứng sau load balancer/proxy
    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
public class AuthController {
}
