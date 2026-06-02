package com.dossier.auth.dto.response;

import com.dossier.auth.domain.User;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@Getter
public class UserResponse {

    // Chỉ trả về những gì Frontend cần hiển thị

    private final UUID id;
    private final String email;
    private final String displayName;
    private final String avatarUrl;
    private final String provider;
    private final String verificationLevel;
    private final boolean emailVerified;
    private final OffsetDateTime createdAt;

    // Factory method: Chuyển đổi từ domain entity sang DTO (from() - người phiên dịch từ Entity sang DTO)
    // Không expose entity ra ngoài API boundary
    // .name(): Chuyển đổi Enum trong Java thành chuỗi String
    public static UserResponse from(User user){
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .provider(user.getProvider().name())
                .verificationLevel(user.getVerificationLevel().name())
                .emailVerified(user.isEmailVerified())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
