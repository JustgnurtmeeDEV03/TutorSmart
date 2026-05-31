package com.dossier.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Cầu nối -> Spring Boot tìm giá trị trong file "application.yml"
@ConfigurationProperties(prefix = "app.jwt")
@Getter
@Setter
public class JwtProperties {
    private String secret;
    private long accessTokenExpiry; // milliseconds: 900000
    private long refreshTokenExpiry;    // milliseconds: 604800000 = 7 ngày
}



// ====================================================================================================
/*
 * 1. Gom nhóm cấu hình: Dùng "@ConfigurationProperties" để gom các biến liên quan vào một Class, thay vì dùng "@Value" rải rác.
 * 2. Tự đóng gói (Encapsulation): Đặt class Config (AuthModuleConfig) ngay trong module tính năng (auth) để module đó độc lập, dễ dàng tái sử dụng hoặc tách thành Microservice sau này.
 * 3. Clean Code: Kết hợp Lombok (@Getter, @Setter) để giảm thiểu code thừa, giúp file cấu hình chỉ còn lại những gì tinh túy nhất.
 * */
// =====================================================================================================