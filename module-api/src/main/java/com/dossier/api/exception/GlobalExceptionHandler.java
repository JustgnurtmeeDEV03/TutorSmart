
// Trạm y tế toàn cục

package com.dossier.api.exception;

import com.dossier.auth.exception.AuthException;
import com.dossier.auth.exception.TokenExpiredException;
import com.dossier.auth.exception.UserAlreadyExistsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // 👨🏻‍⚕️ Bác sĩ "Xung đột" - Xử lý User tồn tại
    // 409 - status chuẩn cho trường hợp resource đã tồn tại (Email trùng khi đăng ký)
    @ExceptionHandler(UserAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleUserAlreadyExists(UserAlreadyExistsException ex) {
        return ErrorResponse.of(HttpStatus.CONFLICT, ex.getMessage());
    }

    // 👨🏻‍⚕️ Bác sĩ "Bảo mật" - Xử lý lỗi Authentication
    // Luôn trả về 1 thông điệp chung "Authentication failed" / "Invalid email or password"
    // -> Hacker không thể phân biệt email có tồn tại hay không.
    @ExceptionHandler({AuthException.class, UsernameNotFoundException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleAuthException(RuntimeException ex) {
        // Không Expose chi tiết lỗi - tránh user enumeration attack
        return ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Authentication failed");
    }

    // 👨🏻‍⚕️ Bác sĩ "Token" - Xử lý Token hết hạn
    // Silent refresh Token để user không biết và giúp FE phân biết với lỗi "Sai Token".
    @ExceptionHandler(TokenExpiredException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleTokenExpired(TokenExpiredException ex) {
        return ErrorResponse.of(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    // 👨🏻‍⚕️ Bác sĩ "Form" - Xử lý Validation Error
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult()
                .getFieldErrors()   // Lấy ra danh sách các trường bị lỗi
                .stream()
                .map(FieldError::getDefaultMessage)
                .toList();
        return ErrorResponse.of(HttpStatus.BAD_REQUEST, "Validation failed:", details);
    }

    // 👨🏻‍⚕️ Bác sĩ "Đa khoa" - Catch-all (Lưới an toàn cuối cùng)
    // Catch-all: Log đầy đủ nhưng không expose stack trace ra ngoài
    // HTTP 500: Báo cho FE biết đây là lỗi Server, không phải lỗi người dùng.
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }
}


// =====================================================================================================================
/*
 * 1. @RestControllerAdvice
 *   - @ControllerAdvice: Đánh dấu class này sẽ "bắt" exception từ TẤT CẢ các Controller trong ứng dụng.
 *   - ResponseBody: Tự động serialize object trả về thành JSON (không cần @ResponseBody trên từng hàm).
 *
 * 2. Chỉ cần khai báo 1 lần, mọi lỗi trong toàn bộ module đều được xử lý tập trung.
 *    Không cần try-catch rải rác ở từng Controller!
 *
 * 3. Luồng dữ liệu hoàn chỉnh:
 *     -🚪 Client gửi POST /api/v1/auth/register với { email, password }.
 *     -🛡️ SecurityFilterChain cho qua (vì là endpoint public).
 *     -📥 Controller nhận request, @Valid kiểm tra RegisterRequest → OK.
 *     -⚙️ AuthService gọi UserRepository.existsByEmail() → TRUE!
 *     -🚨 AuthService throw new UserAlreadyExistsException("Email already exists").
 *     -🏥 GlobalExceptionHandler bắt được → Gọi handleUserAlreadyExists().
 *     -📦 ErrorResponse được build với status=409, message="Email already exists".
 *     -📤 Controller trả về JSON:
 *      {
 *         "status": 409,
 *         "error": "Conflict",
 *         "message": "Email already exists",
 *         "timestamp": "2026-06-03T10:30:00Z"
 *       }
 * */
// =====================================================================================================================
