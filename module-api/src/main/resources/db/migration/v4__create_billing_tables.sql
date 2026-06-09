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
    total_sessions    SMALLINT       NOT NULL       DEFAULT 0, -- Buổi dự kiến theo lịch
    completed_session SMALLINT       NOT NULL       DEFAULT 0, -- Buổi thực sự đã học
    absent_sessions   SMALLINT       NOT NULL       DEFAULT 0, -- Buổi nghỉ (cả GS + HS)
    makeup_sessions   SMALLINT       NOT NULL       DEFAULT 0, -- Buổi bù đã hoàn thành

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
        CHECK (total_session >=0
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