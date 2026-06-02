package com.dossier.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class AuthResponse {
    @JsonProperty("access_token")
    private final String accessToken;

    @JsonProperty("refresh_token")
    private final String refreshToken;

    @JsonProperty("token_type")
    @Builder.Default
    private final String tokenType = "Bearer";

    // Số giây access token còn hiệu lực - frontend dùng để schedule refresh
    @JsonProperty("expires_in")
    private final long expiresIn;

    private final UserResponse user;
}

// =====================================================================================================================
/*
 * 1. Tư duy tuân thủ chuẩn OAuth2 (RFC 6749)
 *  - Chuẩn Java thích đặt tên biến là camelCase (accessToken, expiresIn).
    - Nhưng chuẩn OAuth2 quốc tế lại quy định JSON trả về bắt buộc phải là snake_case (access_token, expires_in).
    - "@JsonProperty" là phép màu giúp Java giữ chuẩn Java trong code,
      nhưng khi xuất ra JSON cho Client lại tự động biến hình thành chuẩn OAuth2.
      Frontend Dev (và các thư viện như Axios, Postman) sẽ rất thích điều này vì nó đúng chuẩn chung của ngành!

   2. @Builder.Default: Gán cứng tokenType = "Bearer".
      Frontend không cần phải tự đoán hay hardcode chữ "Bearer" khi gắn vào Header.

   3. expiresIn (Số giây token sống): Đây là "mật mã" để Frontend lập lịch Silent Refresh.
      Frontend sẽ tính toán: "Token sống 15 phút (900s).
      Mình sẽ đặt bộ đếm, đến phút thứ 14 tự động gọi API /refresh để xin token mới mà người dùng không hề hay biết".
 * */
// =====================================================================================================================