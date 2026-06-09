-- ================================================================
-- Trigger function dùng chung cho tất cả bảng có updated_at
-- Định nghĩa 1 lần ở đây, các migration sau dùng lại

-- V1: Authentication tables
-- users         : core identity, supports LOCAL + OAuth2 Google
-- refresh_tokens: JWT refresh token store with device context
-- user_settings: per-user preferences and defaults
-- ================================================================

CREATE OR REPLACE FUNCTION set_updated_at()
       RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION set_updated_at() IS
        'Trigger function tự động cập nhật updated_at. Dùng chung cho mọi bảng.'

-- ----------------------------------------------------------------

CREATE TABLE users
(
    id                 UUID             NOT NULL DEFAULT gen_random_uuid(),
    email              VARCHAR(255)     NOT NULL,
    password_hash      VARCHAR(255),    -- NULL Cho OAuth2 users
    display_name       VARCHAR(100)     NOT NULL,
    avatar_url         VARCHAR(500),
    provider           VARCHAR(20)      NOT NULL DEFAULT 'LOCAL',
    provider_id        VARCHAR(255),    -- Google subclaim, NULL cho LOCAL
    phone              VARCHAR(20),
    phone_verified     BOOLEAN          NOT NULL DEFAULT FALSE,
    identity_verified  BOOLEAN          NOT NULL DEFAULT FALSE,
    -- Verification ladder: NONE → EMAIL → PHONE → IDENTITY
    -- Controls feature access: e.g. persona creation requires PHONE+
    verification_level VARCHAR(20)      NOT NULL DEFAULT 'NONE',
    is_active          BOOLEAN          NOT NULL DEFAULT TRUE,
    email_verified     BOOLEAN          NOT NULL DEFAULT FALSE,
    last_login_at      TIMESTAMPTZ,
    created_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_users
        PRIMARY KEY (id),

    CONSTRAINT users_email_unique
        UNIQUE (email),

    -- Chỉ cho phép các provider đã định nghĩa
    CONSTRAINT users_provider_check
        CHECK ( provider IN ('LOCAL', 'GOOGLE')),

    CONSTRAINT user_verification_level_check
        CHECK (verification_level IN('NONE','EMAIL','PHONE','IDENTITY')),

    -- LOCAL user bắt buộc phải có password
    CONSTRAINT users_local_requires_password
        CHECK ( provider != 'LOCAL' OR password_hash IS NOT NULL ),
        -- Hoặc user không phải LOCAL, hoặc nếu là LOCAL thì phải có password.

    -- OAuth2 user bắt buộc phải có provider_id
    CONSTRAINT users_oauth_requires_provider_id
        CHECK (provider = 'LOCAL' OR provider_id IS NOT NULL),

    -- Phone verified chỉ khi phone đã được set
    CONSTRAINT users_phone_verified_requires_phone
        CHECK (phone_verified = FALSE OR phone IS NOT NULL),

    -- Đảm bảo identity_verified nhất quán với verification_level
    CONSTRAINT users_identity_verified_consistency
        CHECK (identity_verified = FALSE OR verification_level = 'IDENTITY'),

    -- Đảm bảo phone_verified nhất quán với verification_level
    CONSTRAINT users_phone_verified_consistency
        CHECK (phone_verified = FALSE OR verification_level IN ('PHONE', 'IDENTITY'))
);

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE users
        IS 'Core user accounts - supports LOCAL password and OAuth2 Google authentication';
COMMENT ON COLUMN users.password_hash
        IS 'BCrypt hash, NULL for OAuth2 users - never store plaintext';
COMMENT ON COLUMN users.provider_id
        IS 'External provider unique ID, e.g. Google sub claim from JWT';
COMMENT ON COLUMN users.phone
    IS 'E.164 format: +84901234567 — NULL until user adds it';
COMMENT ON COLUMN users.verification_level
    IS 'NONE=registered only | EMAIL=email confirmed | PHONE=identity linked | IDENTITY=document verified';
COMMENT ON COLUMN users.is_active
        IS 'FALSE = account suspended, data preserved - not a delete flag';

-- ----------------------------------------------------------------

CREATE TABLE refresh_tokens
(
    id              UUID                NOT NULL  DEFAULT gen_random_uuid(),
    user_id         UUID                NOT NULL,
    token_hash      VARCHAR(255)        NOT NULL, -- SHA-256 hash, không lưu raw token
    user_agent      TEXT,               -- browser/device   context
    ip_address      VARCHAR(45),        -- VARCHAR(45): đủ cho IPv4(15) + IPv6(39)
    expires_at      TIMESTAMPTZ         NOT NULL,
    revoked         BOOLEAN             NOT NULL DEFAULT FALSE,
    revoked_at      TIMESTAMPTZ,        -- NULL khi chưa revoke, set khi revoke
    created_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_refresh_tokens
        PRIMARY KEY (id),

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE -- xóa user -> tự động xóa hết tokens
);

COMMENT ON TABLE refresh_tokens
    IS 'JWT refresh tokens with device context for multi-session management';
COMMENT ON COLUMN refresh_tokens.token_hash
    IS 'SHA-256 hash of the raw token - validate by hashing incoming token and comparing';
COMMENT ON COLUMN refresh_tokens.ip_address
    IS 'Supports IPv4 (max 15 chars), IPv6 (max 39 chars), IPv4-mapped IPv6 (max 45 chars)';
COMMENT ON COLUMN refresh_tokens.revoked_at
    IS 'Timestamp when token was revoked — NULL means still active. Useful for security audit';

-- ----------------------------------------------------------------

CREATE TABLE user_settings
(
    id                      UUID                NOT NULL DEFAULT gen_random_uuid(),
    user_id                 UUID                NOT NULL,
    -- Dossier mặc định private khi tạo mới
    default_dossier_public  BOOLEAN             NOT NULL DEFAULT FALSE,
    email_notification      BOOLEAN             NOT NULL DEFAULT TRUE,
    theme                   VARCHAR(20)         NOT NULL DEFAULT 'SYSTEM',
    -- Ngôn ngữ giao diện
    language                VARCHAR(10)         NOT NULL DEFAULT 'vi',
    timezone                VARCHAR(50)         NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    created_at              TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ         NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_user_settings
        PRIMARY KEY (id),

    CONSTRAINT user_settings_user_unique
        UNIQUE (user_id),

    CONSTRAINT fk_user_settings_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT user_settings_theme_check
        CHECK (theme IN ('LIGHT', 'DARK', 'SYSTEM')),

    CONSTRAINT user_settings_language_check
        CHECK (language IN ('vi', 'en', 'ja', 'ko', 'zh'))
);

CREATE TRIGGER trg_user_settings_updated_at
    BEFORE UPDATE ON user_settings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE user_settings
    IS 'Per-user preferences — created automatically on registration';

COMMENT ON COLUMN user_settings.default_dossier_public
    IS 'Default privacy for newly created dossier sections';

COMMENT ON COLUMN user_settings.timezone
    IS 'IANA timezone, e.g. Asia/Ho_Chi_Minh — used for all date/time display';