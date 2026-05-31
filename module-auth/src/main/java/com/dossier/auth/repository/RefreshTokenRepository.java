package com.dossier.auth.repository;

import com.dossier.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    // Hot path: validate refresh token khi client gọi /auth/refresh
    Optional<RefreshToken> findByTokenHashAndRevokedFalse(String tokenHash); // <=> SQL: SELECT * FROM refresh_tokens WHERE token_hash = ? AND revoked = false


    // Logout all devices: revoke tất cả token của một user (Soft Delete)
    @Modifying
    @Query("""
          UPDATE RefreshToken rt 
          SET rt.revoked = true, rt.revokedAt = :now
          WHERE rt.user.id = :userId AND rt.revoked = false            
          """)
    void revokeAllByUserId(@Param("userId") UUID userId,
                           @Param("now") OffsetDateTime now);
    // <=> SQL: UPDATE refresh_tokens SET revoked = true, revoked_at = ? WHERE user_id = ? AND revoked = false


    // Scheduled cleanup: Xóa token đã hết hạn để bảng không phình to (Hard Delete)
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expireAt < :now")
    void deleteExpiredTokens(@Param("now") OffsetDateTime now);
    // <=> SQL: DELETE FROM refresh_tokens WHERE expires_at < ?
}


// ====================================================================================================
/*
* 1. Bảo mật: Lưu "tokenHash" thay vì raw token
* 2. Audit (Kiểm toán): Dùng  Soft Delete (revoke) thay vì xóa hẳn để lưu vêt "revokedAt"
* 3. Performance & Maintenance: Có sẵn hàm "deleteExpiredTokens" để bảo trì DB.
* 4. Clean Code: Kết hợp linh hoạt giữa Derived Query (cho câu đơn giản) và @Query (cho câu phức tạp).
* */
// =====================================================================================================