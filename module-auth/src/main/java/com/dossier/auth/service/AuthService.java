package com.dossier.auth.service;

import com.dossier.auth.dto.request.LoginRequest;
import com.dossier.auth.dto.request.RegisterRequest;
import com.dossier.auth.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse register (RegisterRequest request);
    AuthResponse login (LoginRequest request, String userAgent, String ipAddress);
    AuthResponse refreshToken(String rawRefreshToken, String UserAgent, String ipAddress);
    void logout(String rawRefreshToken);
    void logoutAllDevices(String rawAccessToken);
}
