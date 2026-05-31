package com.dossier.auth.service;


import com.dossier.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Tự động tạo ra một biến log để ghi "log" (ví dụ: log.debug(...)) mà không cần khởi tạo "LoggerFactory".
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {
    private final JwtProperties jwtProperties;

    // Tạo khóa bí mật (Signing Key)
    // Chuyển Base64 secret string -> SecretKey object cho HMAC-SHA256
    // Gọi mỗi lần thay vì cache để tránh key leak trong memory dump
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // Nhà máy sản xuất TOKEN
    public String generateAccessToken(UUID userId, String email) {
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId.toString())
                .claim("type","ACCESS") // Phân biệt access vs refresh
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getAccessTokenExpiry()))
                .signWith(getSigningKey()) // JJWT 0.12.x: tự suy ra HS256 từ key type
                .compact();
    }

    public String generateRefreshToken(UUID userId, String email) {
        return Jwts.builder()
                .subject(email)
                .claim("type", "REFRESH")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getRefreshTokenExpiry()))
                .signWith(getSigningKey())
                .compact();
    }

    // Giải mã & Trích xuất thông tin
    public Claims extractAllClaims(String token) {
        // JJWT 0.12.x: parseClaimsJws() đổi thành parseSignedClaims()
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(extractAllClaims(token).get("userId", String.class));
    }

    // Kiểm tra để đảm bảo chữ ký hợp lệ và chưa hết hạn
    public boolean isTokenValid(String token){
        try {
            Claims claims = extractAllClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch(JwtException | IllegalArgumentException e){
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean isAccessToken(String token) {
        try {
            return "ACCESS".equals(extractAllClaims(token).get("type", String.class));
        } catch (JwtException e) {
            return false;
        }
    }

    // SHA-256 hash raw refresh token trước khi lưu vào DB
    // Không thể reverse: attacker leak DB chỉ lấy được hash
    public String hashToken(String rawToken){
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch(NoSuchAlgorithmException e){
            // SHA-256 có trong mọi JVM theo chuẩn - không bao giờ xảy ra
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}



// ====================================================================================================
/*
 * "JwtService" là một Service class chịu trách nhiệm toàn bộ vòng đời của JWT:
 * 1. Tạo (Generate): Sinh ra Access Token và Refresh Token.
 * 2. Giải mã (Extract): Đọc thông tin (email, userId) từ token.
 * 3. Kiểm tra (Validate): Xác minh chữ ký, thời hạn và loại token.
 * 4. Bảo mật (Hash): Băm (hash) Refresh Token trước khi lưu vào Database.
 *
 * - Cập nhật: Sử dụng đúng cú pháp mới nhất của JJWT 0.12.x.
 * - Bảo mật chiều sâu (Defense in Depth): Phân biệt rõ Access/Refresh token qua custom claim, hash token trước khi lưu DB, và hạn chế lưu key object trong memory.
 * - Clean Code: Tách biệt rõ ràng các hàm extract, validate, generate. Sử dụng Lombok để code gọn gàng.
 * */
// =====================================================================================================