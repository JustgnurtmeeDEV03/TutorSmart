package com.dossier.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// Kích hoạt binding jwtProperties từ application.yml (đọc file cấu hình cho class "JwtProperties" vào file "MainApplication.java")
// Đặt ở đây thay vì main class để module-auth tự quản lý config của mình
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class AuthModuleConfig {
}

