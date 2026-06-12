
// Cây cầu nối DB

package com.tutorsmart.auth.security;

import com.tutorsmart.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
// Cung cấp thông tin user khi Spring Security cần
public class CustomUserDetailsService implements UserDetailsService {

    // Inject (tiêm) Repository vào để query xuống DB
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email)
        throws UsernameNotFoundException {
        // Optional API (Java 8)
        return userRepository.findByEmail(email)
                .map(UserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email
                ));
    }
}

// ===================================================================================================================================================
/*
 * 1. Spring Security có một cơ chế xác thực mặc định, nó không hề biết DB dùng JPA, MongoDB hay Redis,
 *    nó cũng không biết Entity "User" trông như thế nào.
 *    Spring Security cung cấp một interface tên là "UserDetailsService".
 *
 * 2. Tư duy thiết kế (Decoupling - Tách biệt): Việc này giúp Spring Security không bị phụ thuộc vào tầng DB.
 *     Sau này khi đổi từ MyQL sang MongoDB, ta chỉ cần sửa file này, toàn bộ hệ thống bảo mật trên vẫn hoat động bình thường.
 * */
// =====================================================================================================================================================