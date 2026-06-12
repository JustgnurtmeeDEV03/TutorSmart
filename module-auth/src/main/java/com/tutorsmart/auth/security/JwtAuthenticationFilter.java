
// Người gác cổng

package com.tutorsmart.auth.security;


import com.tutorsmart.auth.service.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j

// Đảm bảo Filter này thực thi đúng 1 lần cho mỗi request
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // Inject 2 "vũ khí" chính để giải mã token và lấy thông tin user từ DB.
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");

        // Không có Bearer token -> bỏ qua, để SecurityConfig xử lý
        if(authHeader == null || !authHeader.startsWith("Bearer ")){
            filterChain.doFilter(request, response);
            return;
        }

        // Cắt bỏ chữ "Bearer" để lấy chuỗi JWT thực sự
        final String token = authHeader.substring(7); // Bỏ "Bearer "

        // 🛡️ Chốt chặn bảo mật số 1:
        try {
            // Refresh token không được dùng để authenticate request thông thường
            // Chỉ dùng ở endpoint /auth/refresh
            if (!jwtService.isAccessToken(token)) {
                filterChain.doFilter(request, response);
                return;
            }

            final String email = jwtService.extractEmail(token);

            // Chỉ authenticate nếu chưa có authentication trong context
            // Tránh xử lý lại mỗi request cùng session
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                // 🛡️ Chốt chặn bảo mật số 2: Kiểm tra chữ ký và thời hạn của Token
                if (jwtService.isTokenValid(token)) {
                    // 🎫 Tạo "thẻ thông hành" + tránh memory dump
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,   // credentials = null sau khi authenticated
                                userDetails.getAuthorities()
                            );
                    // Đính kèm request details (IP, session) vào authentication (thẻ thông hành)
                    // Ném thẻ thông hành vào "SecurityContextHolder" -> Spring Security công nhận là User đã đăng nhập
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch(JwtException e){
            // Token invalid, expired, wrong format nhưng throw exception ở đây
            // Request tiếp tục với SecurityContext rỗng -> Request đi tới SecurityConfig
            // .authenticated() trong SecurityConfig sẽ từ chối request -> tự động trả về lỗi 401
            log.debug("JWT validation failed for request {}: {}",
            request.getRequestURI(), e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}

// ===================================================================================================================================================
/*
 * 1. JWT là cơ chế Stateless (Không trạng thái). Server không lưu session.
 *    Do đó, MỌI request gửi lên server đều phải tự chứng minh "Tôi là ai" bằng cách đính kèm JWT
 *    -> Chúng ta tạo ra một Filter (Bộ lọc) chặn đứng mọi request ngày từ "cổng thành",
 *       bóc tách JWT, kiểm tra tính hợp lệ, và nếu OK thì "cấp thẻ thông hành" (Authentication Object) để request
 *       được đi tiếp vào Controller.
 *
 * 2. "SecurityContextHolder" như một chiếc cặp táp đi theo request từ lúc vào "server" đến lúc ra về.
 *    - Filter kiểm tra Token -> Tạo Authentication -> Bỏ vào cặp táp
 *    - Controller mở cặp táp ra -> Lấy "UserPrincipal" -> Lấy "userId để query data"
 *    - Cuối request, Spring Security tự động dọn dẹp cặp táp (clear context)
 *      để tránh rò rỉ dữ liệu sang request của user khác (quan trọng trong môi trường Multi-threading của Tomcat)
 *
 * 3. Nguyên tắc "Fail-Safe" (An toàn khi lỗi)
 *    - Ở phần "catch(JwtException e)" trong Filter, ta chọn cách im lặng (chỉ log debug) và chuyển tiếp request.
 *      Filter không đủ thầm quyền quyết định request này có bị chặn hay không (vì có những API public không cần token).
 *      -> Trao quyền quyết định lại cho SecurityConfig. (linh hoạt)
 *
 * 4. Luồng xác thực (Authentication Flow - CustomUserDetailsService.java + JwtAuthenticationFilter.java)
 *    -> Request đến -> Filter chặn lại, bóc JWT
 *      -> Filter nhờ Service giải mã JWT (JwtService.java) -> Lấy ra Email
 *         -> Filter nhờ UserDetailsService xuống DB kiểm tra xem Email có tồn tại, có bị khóa không.
 *           -> Mọi thứ hợp lệ -> Filter cấp quyền và lưu vào Context.
 *             -> Request đi vào Controller -> Xử lý nghiệp vụ -> Trả về response.
 * */
// =====================================================================================================================================================