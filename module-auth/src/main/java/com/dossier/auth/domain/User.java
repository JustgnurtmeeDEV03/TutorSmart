package com.dossier.auth.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "passwordHash") // Không bao giờ log password hash


public class User {
    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    // NULL cho OAuth2 users
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AuthProvider provider = AuthProvider.LOCAL;

    // Google sub cliam -- NULL cho LOCAL users
    @Column(name = "provider_id", length = 255)
    private String providerId;

    @Column(length = 20)
    private String phone;

    @Column(name = "phone_verified", nullable = false)
    @Builder.Default
    private boolean phoneVerified = false;

    @Column(name = "identity_verified", nullable = false)
    @Builder.Default
    private boolean identityVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_level", nullable = false, length = 20)
    @Builder.Default
    private VerificationLevel verificationLevel = VerificationLevel.NONE;

    // Tên field là "active" — map xuống cột "is_active"
    // Dùng "active" thay vì "isActive" để tránh Lombok sinh method isIsActive()
    @Column(name = "is_active", nullable = false)
    @Column(name = "is_acitve", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column (name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    // Hibernate set khi INSERT - DB có DEFAULT NOW() làm fallback
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    // PostgreSQL trigger set giá trị này — Hibernate không được chạm vào
    // insertable=false: không include trong INSERT statement
    // updatable=false : không include trong UPDATE statement

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

}
