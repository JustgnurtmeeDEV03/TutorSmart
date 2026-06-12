
// Adapter Pattern

package com.tutorsmart.auth.security;

import com.tutorsmart.auth.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class UserPrincipal implements UserDetails {

    private final User user;

    // Expose để controller lấy thông tin user hiện tại (UserPrincipal) - không cần query lại DB
    public UUID getUserId() {
        return user.getId();
    }

    public User getUser() {
        return user;
    }

    // Hàm bắt buộc phải có khi "implements UserDetails"
    // Phiên dịch để Spring hiểu: "Username chính là Email"
    @Override
    public String getUsername() {
        return user.getEmail(); // Spring Security dùng email làm username
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    // Trả về danh sách "Quyền hạn (Roles/Authorities) của user"
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Hiện tại một role duy nhất - mở rộng sau khi có phân quyền
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    // Các chốt chặn bảo mật (Account Status Flags)
    @Override
    public boolean isAccountNonExpired() {
        return true;  // Tài khoản không có hạn sử dụng
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.isActive(); // false = account bị suspend
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // Mật khẩu không bắt buộc phải đổi định kỳ
    }

    // Phải hoạt động và xác thực Email thì mới cho đăng nhập
    @Override
    public boolean isEnabled() {
        return user.isActive() && user.isEmailVerified();
    }
}


// ===================================================================================================================================================
/*
 * 1. "UserPrincipal" đóng vai trò là "lớp vỏ bọc" (Wrapper/Adapter).
 *     Nó bọc Entity "User" bên trong và "phiên dịch" các thông tin User sang ngôn ngữ mà Spring Security có thể hiểu được.
 *
 * 2. Tư duy thiết kế: Giữ cho Entity "User" trong sạch (POJO - Plain Old Java Object), không dính dáng đến code của Spring Security.
 *    Mọi Logic bảo mật do "UserPrincipal" gánh vác. Entity "User" không bị "ô nhiễm" bởi các interface của Spring Security.
 *
 * 3. Luồng hoạt động thực tế khi User đăng nhập (form Email + Password)
 *    ->Spring Security gọi "UserDetailsService" để tìm User trong DB dựa trên Email.
 *      -> "UserDetailsService" tìm thấy Entity "User", bọc nó vào "UserPrincipal" và trả về cho Spring Security.
 *        -> Spring Security gọi UserPrincial.getPassword() để lấy Hash trong DB.
 *          -> Spring Security gọi UserPrincipal.isAccountNonLocked() và isEnabled() để kiểm tra xem tài khoản có bị khóa hay chưa verify email không.
 *             -> Nếu mọi thứ ổn, Spring Security băm mật khẩu user vừa nhập, so sánh với Hash. Khớp -> Cấp quyền truy cập!
 * */
// =====================================================================================================================================================
