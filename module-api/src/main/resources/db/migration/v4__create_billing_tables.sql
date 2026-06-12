-- =============================================================================
-- MIGRATION V4: Billing Domain — Học Phí Tháng & Lịch Sử Thanh Toán
-- Tác giả  : TutorSmart Admin
-- Phụ thuộc: V2 (tutor_profiles, students), V3 (schedule_sessions)
--
-- Thiết kế 2 tầng:
--   tuition_records       → Tổng kết học phí THEO THÁNG (1 record/học sinh/tháng)
--   payment_transactions  → Từng lần thu tiền thực tế (1 record = 1 lần thu)
--
-- Lý do tách 2 tầng:
--   - Phụ huynh hay trả học phí làm nhiều lần (trả 500k trước, còn lại cuối tháng)
--   - Cần audit trail đầy đủ: thu tiền lúc nào, qua kênh nào, ai ghi nhận
--   - 1 tuition_record.amount_paid = SUM(payment_transactions.amount) luôn đúng
-- =============================================================================


-- =============================================================================
-- BẢNG: tuition_records
-- Tổng kết học phí mỗi học sinh cho mỗi tháng.
-- Được tạo tự động (khi close tháng) hoặc thủ công khi gia sư tạo invoice.
-- =============================================================================
CREATE TABLE tuition_records
(
    -- Primary key
    id                UUID PRIMARY KEY        DEFAULT gen_random_uuid(),

    -- Quan hệ chính
    tutor_id          UUID           NOT NULL,
    student_id        UUID           NOT NULL,

    -- Kỳ tính học phí
    -- billing_month LUÔN là ngày 1 của tháng (enforced bởi CHECK constraint)
    -- Dùng DATE (không phải VARCHAR) để: sort, filter range, date arithmetic
    -- Ví dụ: 2024-12-01 -> Học phí tháng 12/2024
    billing_month     DATE           NOT NULL,

    -- Thống kê buổi học trong tháng
    -- Các trường này được tính từ schedule_sessions và có thể recalculate
    total_sessions     SMALLINT       NOT NULL       DEFAULT 0, -- Buổi dự kiến theo lịch
    completed_sessions SMALLINT       NOT NULL       DEFAULT 0, -- Buổi thực sự đã học
    absent_sessions    SMALLINT       NOT NULL       DEFAULT 0, -- Buổi nghỉ (cả GS + HS)
    makeup_sessions    SMALLINT       NOT NULL       DEFAULT 0, -- Buổi bù đã hoàn thành

    -- Học phí
    -- amount_paid được tính = SUM(payment_transactions.amount) của record này
    amount_due        NUMERIC(12, 0) NOT NULL       DEFAULT 0, -- Phải trả (VNĐ)
    amount_paid       NUMERIC(12, 0) NOT NULL       DEFAULT 0, -- Đã trả (VNĐ)
    due_date          DATE,                                    -- Hạn đóng tiền

    -- Trạng thái học phí
    -- Vòng đời: PENDING -> PARTIAL -> PAID
    --                        ↘ OVERDUE (nếu quá due_date mà chưa trả đủ)
    --  WAIVED: Miễn học phí tháng này (học sinh ốm, gia đình khó khăn, v.v.)

    status             VARCHAR(20)    NOT NULL       DEFAULT 'PENDING',
    -- Ghi chú của gia sư (ví dụ: "Tháng này HS ốm nhiều, giảm 50%")
    notes               TEXT,

    -- Timestamps
    created_at          TIMESTAMPTZ   NOT NULL       DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL       DEFAULT NOW(),

    -- =========================================================================
    -- CONSTRAINTS
    -- =========================================================================
    CONSTRAINT fk_tuition_tutor
        FOREIGN KEY (tutor_id)
            REFERENCES tutor_profiles (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_tuition_student
        FOREIGN KEY (student_id)
            REFERENCES students (id)
            ON DELETE CASCADE,

    -- MỖI học sinh chỉ có ĐÚNG 1 tution_record cho MỖI tháng
    -- Đây là business rule quan trọng nhất của billing domain
    CONSTRAINT uq_tuition_student_month
        UNIQUE (student_id, billing_month),

    CONSTRAINT chk_tuition_status
        CHECK (status IN ('PENDING', 'PARTIAL', 'PAID', 'OVERDUE', 'WAIVED')),

    -- Bắt buộc billing_month phải là ngày 1 của tháng
    -- Loại bỏ data invalid như 2024-12-15 hay 2024-12-31
    CONSTRAINT chk_tuition_billing_month_first_day
        CHECK (EXTRACT(DAY FROM billing_month) = 1),

    -- Số tiền không được âm
    CONSTRAINT chk_tuition_amounts_non_negative
        CHECK (amount_due >=0 AND amount_paid >=0),

    --Số buổi không được âm
    CONSTRAINT chk_tuition_sessions_non_negative
        CHECK (total_sessions >=0
            AND completed_sessions >=0
            AND absent_sessions >=0
            AND makeup_sessions >=0),

    -- Số buổi đã trả không được vượt quá số phải trả
    -- Cho phép overpayment nếu amount_due = 0 (buổi miễn phí)
    CONSTRAINT chk_tuition_no_overpay
        CHECK (amount_due = 0 OR amount_paid <= amount_due * 2) -- *2: cho phép trả trước tháng sau
);

-- Trigger
CREATE TRIGGER trg_tuition_records_updated_at
    BEFORE UPDATE
    ON tuition_records
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

-- -------------------------------------------------------------------------
-- INDEXES cho tuition_records
-- -------------------------------------------------------------------------

-- [THƯỜNG DÙNG NHẤT] Dashboard gia sư: "Học phí tháng này của tất cả học sinh"
CREATE INDEX idx_tuition_tutor_month
    ON tuition_records (tutor_id, billing_month DESC);

-- Lịch sử học phí của 1 học sinh cụ thể (student detail page)
CREATE INDEX idx_tuition_student_month
    ON tuition_records (student_id, billing_month DESC);

-- Tìm học phí chưa đóng/quá hạn để nhắc nhở (notification job)
-- Partial index: chỉ index các record cần xử lý, bỏ qua PAID/WAIVED
CREATE INDEX idx_tuition_pending_overdue
    ON tuition_records (tutor_id, due_date, status)
    WHERE status IN ('PENDING', 'PARTIAL', 'OVERDUE');

-- -------------------------------------------------------------------------
-- COMMENTS
-- -------------------------------------------------------------------------
COMMENT ON TABLE tuition_records IS
    'Tổng kết học phí theo tháng. 1 record = 1 học sinh × 1 tháng. '
    'Unique constraint (student_id, billing_month) enforced at DB level.';
COMMENT ON COLUMN tuition_records.billing_month IS
    'Ngày 1 của tháng tính học phí. Ví dụ: 2024-12-01 = tháng 12/2024. '
    'CHECK constraint đảm bảo luôn là ngày 1.';
COMMENT ON COLUMN tuition_records.total_sessions IS
    'Tổng số buổi theo lịch dự kiến trong tháng.';
COMMENT ON COLUMN tuition_records.completed_sessions IS
    'Số buổi thực sự đã học (status=COMPLETED), kể cả buổi bù.';
COMMENT ON COLUMN tuition_records.absent_sessions IS
    'Số buổi nghỉ (STUDENT_ABSENT + TUTOR_ABSENT + CANCELLED).';
COMMENT ON COLUMN tuition_records.makeup_sessions IS
    'Số buổi bù (MAKEUP) đã được dạy trong tháng.';
COMMENT ON COLUMN tuition_records.amount_due IS
    'Học phí phải thu tháng này (VNĐ). Tính dựa trên fee_type của student.';
COMMENT ON COLUMN tuition_records.amount_paid IS
    'Tổng số tiền đã nhận. PHẢI = SUM(payment_transactions.amount). '
    'Application layer chịu trách nhiệm sync giá trị này.';
COMMENT ON COLUMN tuition_records.status IS
    'PENDING (chưa đến hạn) | PARTIAL (trả 1 phần) | PAID (đủ) | '
    'OVERDUE (quá hạn) | WAIVED (miễn).';

-- =============================================================================
-- BẢNG: payment_transactions
-- Lịch sử từng lần thu tiền học phí. Audit trail bất biến.
-- Một tuition_record có thể có nhiều payment_transactions (trả nhiều lần).
--
-- QUAN TRỌNG: Không UPDATE/DELETE record này — append-only để đảm bảo audit trail.
-- Nếu thu nhầm: tạo record âm (refund) thay vì xóa.
-- =============================================================================
CREATE TABLE payment_transactions
(
    -- Primary key
    id              UUID                PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Thuộc học phí tháng nào
    tuition_id      UUID                NOT NULL,

    -- -------------------------------------------------------------------------
    -- Chi tiết giao dịch
    -- -------------------------------------------------------------------------
    -- Số tiền phải > 0 (mỗi giao dịch là 1 lần thu dương)
    -- Refund: tạo record mới với amount âm (hoặc note "hoàn tiền")
    amount          NUMERIC(12,0)       NOT NULL,

    -- Ngày thu tiền (do gia sư nhập, có thể là ngày trong quá khứ)
    payment_date    DATE                NOT NULL                DEFAULT CURRENT_DATE,

    -- Kênh thanh toán
    payment_method  VARCHAR(20)         NOT NULL                DEFAULT 'CASH',

    -- Ghi chú: số GD ngân hàng, nội dung chuyển khoản, tên người chuyển
    note            TEXT,

    -- -------------------------------------------------------------------------
    -- Audit: ai ghi nhận giao dịch này và lúc nào
    -- recorded_by: FK → users.id (là user_id của gia sư, không phải tutor_profile_id)
    -- Dùng users.id trực tiếp vì đây là audit log, cần biết account nào ghi nhận
    -- -------------------------------------------------------------------------
    recorded_by     UUID,               -- FK -> users.id (nullable để an toàn)

    -- -------------------------------------------------------------------------
    -- Timestamps (created_at đủ rồi, payment_transactions không nên UPDATE)
    -- -------------------------------------------------------------------------
    created_at       TIMESTAMPTZ         NOT NULL                DEFAULT NOW(),
    updated_at       TIMESTAMPTZ         NOT NULL                DEFAULT NOW(),

    -- =========================================================================
    -- CONSTRAINTS
    -- =========================================================================
    -- CASCADE DELETE: xóa tuition_record → xóa toàn bộ giao dịch liên quan
    CONSTRAINT fk_payment_tuition
        FOREIGN KEY (tuition_id)
            REFERENCES tuition_records (id)
            ON DELETE CASCADE,

    -- SET NULL khi user bị xóa → vẫn giữ lịch sử giao dịch, chỉ mất thông tin "ai ghi"
    CONSTRAINT fk_payment_recorded_by
        FOREIGN KEY (recorded_by)
            REFERENCES users (id)
            ON DELETE SET NULL,

    CONSTRAINT chk_payment_method
        CHECK (payment_method IN ('CASH', 'BANK_TRANSFER', 'MOMO', 'ZALO_PAY', 'OTHER')),

    -- Mỗi giao dịch phải có giá trị (không cho phép giao dịch 0đ)
    CONSTRAINT  chk_payment_amount_nonzero
        CHECK (amount <> 0),

    -- Ngày giao dịch không được ở tương lai (không cho phép "pre-record")
    -- Cho phép 1 ngày buffer để xử lý timezone (VN = UTC+7)
    CONSTRAINT chk_payment_date_not_future
        CHECK (payment_date <= CURRENT_DATE + INTERVAL '1 day')
);

-- Trigger (giữ để đồng nhất, dù không khuyến khích UPDATE bảng này)
CREATE TRIGGER trg_payment_transactions_updated_at
    BEFORE UPDATE
    ON payment_transactions
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

-- -------------------------------------------------------------------------
-- INDEXES cho payment_transactions
-- -------------------------------------------------------------------------

-- [THƯỜNG DÙNG NHẤT] Xem tất cả lần thanh toán cho 1 tuition_record
CREATE INDEX idx_payment_tuition_id
    ON payment_transactions (tuition_id, payment_date DESC);

-- Xem lịch sử thu tiền gần đây của gia sư (activity feed)
-- JOIN qua tuition_records để lấy tutor_id - cần index tuition_id phía trên
CREATE INDEX idx_payment_date_desc
    ON payment_transactions (payment_date DESC);

-- -------------------------------------------------------------------------
-- COMMENTS
-- -------------------------------------------------------------------------
COMMENT ON TABLE payment_transactions IS
    'Audit log bất biến của từng lần thu học phí. Append-only. '
    'Không DELETE/UPDATE — nếu sai thì tạo record bù (refund).';

COMMENT ON COLUMN payment_transactions.tuition_id IS
    'FK → tuition_records.id. Giao dịch này thuộc học phí tháng nào.';
COMMENT ON COLUMN payment_transactions.amount IS
    'Số tiền giao dịch (VNĐ). Phải khác 0. Âm = hoàn tiền (refund).';
COMMENT ON COLUMN payment_transactions.payment_date IS
    'Ngày thực tế thu tiền (do gia sư nhập, có thể là ngày trước). '
    'Không được ở tương lai (CHECK constraint).';
COMMENT ON COLUMN payment_transactions.payment_method IS
    'CASH | BANK_TRANSFER | MOMO | ZALO_PAY | OTHER.';
COMMENT ON COLUMN payment_transactions.recorded_by IS
    'FK → users.id — tài khoản gia sư ghi nhận giao dịch này. '
    'Dùng users.id (không phải tutor_profile_id) vì đây là audit field.';