package com.dossier.auth.exception;

public class AuthException extends RuntimeException{
    public AuthException(String message){
        super(message);
    }
}

// =====================================================================================================================
/*
 * 1. Kế thừa RuntimeException
 *    - "Unchecked Exception" cho phép ta "throw" ở tầng Service và để tầng cao hơn (Golbal Handler) bắt tập trung.
 *
 * 2. Tách ra 3 class riêng vì mỗi loại cần một "HTTP Status Code khác nhau:
 *    - UserAlreadyExists -> 409 Conflic (Xung đột dữ liệu)
 *    - AuthException -> 401 Unauthorized (Sai thông tin)
 *    - TokenExpired -> 401 Unauthorized (Nhưng FE cần biết chính xác là "expired" để tự động gọi API refresh token!)
 *
 * 3. Nếu chung 1 class, "GlobalExceptionHandler" không thể phân biệt được để trả về status code phù hợp.
 * */
// =====================================================================================================================