package com.dossier.auth.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity // Bật "công tắc" kích hoạt toàn bộ hệ thống Spring Security
@EnableMethodSecurity(prePostEnabled = true) // Cho phép dùng @PreAuthorize trên methods
@EnableGlobalMethodSecurity // Cho phép dùng @PreAuthorize trên methods
@RequiredArgsConstructor    // Inject "JwtAuthenticationFilter" vào để sử dụng bên dưới
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    // 🛡️ Chuỗi phòng thủ nhiều lớp
    @Bean // Hàm này trả về một Object
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // REST API không cần CSRF (Cross-Site Request Forgery - Cookie/Session) - dùng JWT (LocalStorage) thay thế
                .csrf(AbstractHttpConfigurer::disable)

                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Stateless: Không tạo HttpSession (lưu trạng thái đăng nhập) - mỗi request tự authenticate qua JWT
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Default Deny (Mặc định từ chối)
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints - không cần token
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/dossier/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()
                        // Tất cả endpoint còn lại yêu cầu authentication (JWT hợp lệ) - Least Privilege
                        .anyRequest().authenticated()
                )

                // Chạy JWT filter TRƯỚC filter xác thực username/password mặc định
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // Mã hóa mật khẩu
    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt với strenth=12 - đủ chậm để chống brute force, đủ nhanh để UX tốt
        // strength=10 (default) ~ 100ms, strength=12 ~ 400ms trên hardware hiện đại
        return new BCryptPasswordEncoder(12);
    }

    // Bộ máy xác thực
    // Nếu không khai báo Bean này, bạn sẽ không thể gọi authenticate() trong service login được
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // Cầu nối Frontend-Backend
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // localhost: 5173 = Vite dev server mặc định
        // Chỉ cho phép các Domain cụ thể gọi API
        config.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://localhost:3000"
        ));
        // Trình duyệt gửi trước một request OPTIONS (Preflight) để hỏi ý kiến server.
        // Sever -> OK -> request thật mới được gửi.
        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));
        config.setAllowedHeaders(List.of("*"));
        // Cho phép gửi cookie/Authorization header cross-origin
        config.setAllowCredentials(true);
        // Optimiztion request preflight gửi lên server  nhanh hơn
        config.setMaxAge(3600L); // Cache preflight 1 giờ

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

// =====================================================================================================================
/*
 * 1. Đây chính là "Trái tim chỉ huy" của toàn bộ hệ thống bảo mật "Security Config".
 *    Nếu các file trước là những "người lính" (Filter, Service, Principal) làm nhiệm vụ cụ thể,
 *    thì file này chính là "Vị tướng" điều phối tất cả: Ai được vào, ai bị chặn, mật khẩu mã hóa thế nào,
 *    và các filter phối hợp ra sao.
 *
 * 2. File "SecurityConfig" trả lời 4 câu hỏi sống còn của một hệ thống bảo mật:
 *    - Ai được vào? (Authorize requests - phân quyền Enpoint)
 *    - Vào bằng cách nào? (Stateless với JWT, không dùng Session)
 *    - Mật khẩu được bảo vệ ra sao? (BCrypt encoder)
 *    - Frontend nào được phép gọi API (CORS config)
 *
 * 3. Tư duy thiết kế (Declarative Security):
 *    - Thay vì viết code kiểm tra thủ công trong từng Controller,
 *      chúng ta khai báo luật bảo mật một lần ở trung tâm.
 *    - Code nghiệp vụ sạch sẽ, chỉ tập trung vào business logic.
 *
 * 4. Thứ tự ưu tiên trong Spring Security
 *    - CORS Configuration -> Chặn ngay nếu origin không được phép
 *    - JWT Filter  -> Xác thực token, nạp SecurityContext
 *    - authorizeHttpRequests -> Kiểm tra URL có được PermitAll không
 *    - @PreAuthorize -> Kiểm tả Role/Permission cụ thể trên từng method
 *
            ┌─────────────────────────────────────────────────────┐
            │                                                     │
            │  1. CLIENT gửi request + JWT trong Header           │
            │     Authorization: Bearer <jwt_token>               │
            └─────────────────┬───────────────────────────────────┘
                              ▼
            ┌─────────────────────────────────────────────────────┐
            │  2. CORS Filter (SecurityConfig)                    │
            │     ✅ Kiểm tra Origin có được phép không?          │
            │     ❌ Nếu không → 403 Forbidden                    │
            └─────────────────┬───────────────────────────────────┘
                              ▼
            ┌─────────────────────────────────────────────────────┐
            │  3. JwtAuthenticationFilter                         │
            │     ✅ Có JWT không? → Bóc token, giải mã           │
            │     ✅ Token hợp lệ? → Load User từ DB              │
            │     ✅ User active? → Tạo Authentication Object     │
            │     ✅ Nhét vào SecurityContextHolder               │
            │     ❌ Nếu sai → Log + cho đi tiếp (Context rỗng)   │
            └─────────────────┬───────────────────────────────────┘
                              ▼
            ┌─────────────────────────────────────────────────────┐
            │  4. authorizeHttpRequests (SecurityConfig)          │
            │     ✅ URL có trong permitAll? → Cho qua            │
            │     ✅ SecurityContext có Authentication? → Cho qua │
            │     ❌ Nếu không → 401 Unauthorized                 │
            └─────────────────┬───────────────────────────────────┘
                              ▼
            ┌─────────────────────────────────────────────────────┐
            │  5. Controller / Service                            │
            │     ✅ Có @PreAuthorize? → Kiểm tra Role            │
            │     ✅ Xử lý business logic                         │
            │     ✅ Trả về response                              │
            └─────────────────────────────────────────────────────┘
 * */
// =====================================================================================================================