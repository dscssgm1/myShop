-- 折扣服务：促销活动、优惠券模板、用户优惠券
-- 可导入 myshop（共库）或独立的折扣库（分库），表名不含库名

CREATE TABLE IF NOT EXISTS promotion (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    title            VARCHAR(100)   NOT NULL,
    type             VARCHAR(32)    NOT NULL DEFAULT '平台大促',
    rule_type        VARCHAR(32)    NOT NULL,
    threshold_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    per_every        INT            NOT NULL DEFAULT 0,
    discount_amount  DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    discount_rate    DECIMAL(4, 2)  NULL,
    max_discount     DECIMAL(10, 2) NULL,
    scope_type       VARCHAR(16)    NOT NULL DEFAULT 'ALL',
    scope_ids        VARCHAR(500)   NOT NULL DEFAULT '',
    coupon_stackable TINYINT        NOT NULL DEFAULT 0,
    start_time       DATETIME       NULL,
    end_time         DATETIME       NULL,
    status           VARCHAR(16)    NOT NULL DEFAULT 'ACTIVE',
    description      VARCHAR(500)   NOT NULL DEFAULT '',
    color            VARCHAR(255)   NOT NULL DEFAULT '',
    created_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted          TINYINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_status_time (status, start_time, end_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '促销活动';

INSERT IGNORE INTO promotion
(id, title, type, rule_type, threshold_amount, per_every, discount_amount, discount_rate, max_discount,
 scope_type, scope_ids, coupon_stackable, start_time, end_time, status, description, color) VALUES
(1, '双 11 全球狂欢节', '平台大促', 'FULL_REDUCTION', 0.00, 300, 50.00, NULL, NULL,
 'ALL', '', 1, '2026-11-01 00:00:00', '2026-11-11 23:59:59', 'ACTIVE',
 '全场跨店每满 300 减 50，上不封顶', 'linear-gradient(90deg,#ff416c,#ff4b2b)'),
(2, '数码焕新季', '品类促销', 'FULL_REDUCTION', 2000.00, 0, 200.00, NULL, NULL,
 'CATEGORY', '1', 0, '2026-10-20 00:00:00', '2026-11-05 23:59:59', 'ACTIVE',
 '手机数码专区满 2000 减 200，晒单返 50 元 E 卡', 'linear-gradient(90deg,#36d1dc,#5b86e5)'),
(3, '生鲜秒杀日', '限时秒杀', 'DISCOUNT', 0.00, 0, 0.00, 0.50, 500.00,
 'CATEGORY', '4', 1, '2026-10-25 00:00:00', '2026-10-31 23:59:59', 'ACTIVE',
 '每日 10 点 / 20 点，生鲜爆款 5 折起', 'linear-gradient(90deg,#f83600,#f9d423)'),
(4, '新人专享礼包', '领券中心', 'FULL_REDUCTION', 188.00, 0, 18.00, NULL, NULL,
 'ALL', '', 1, '2026-01-01 00:00:00', NULL, 'ACTIVE',
 '新用户注册立领 188 元大礼包', 'linear-gradient(90deg,#43cea2,#185a9d)');

CREATE TABLE IF NOT EXISTS coupon_template (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    title            VARCHAR(100)   NOT NULL,
    threshold_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    discount_amount  DECIMAL(10, 2) NOT NULL,
    scope_type       VARCHAR(16)    NOT NULL DEFAULT 'ALL',
    scope_ids        VARCHAR(500)   NOT NULL DEFAULT '',
    total_stock      INT            NOT NULL DEFAULT 0,
    claimed_count    INT            NOT NULL DEFAULT 0,
    per_user_limit   INT            NOT NULL DEFAULT 1,
    valid_start      DATETIME       NULL,
    valid_end        DATETIME       NULL,
    status           VARCHAR(16)    NOT NULL DEFAULT 'ACTIVE',
    created_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted          TINYINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_status_time (status, valid_start, valid_end)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '优惠券模板';

INSERT IGNORE INTO coupon_template
(id, title, threshold_amount, discount_amount, scope_type, scope_ids, total_stock, claimed_count,
 per_user_limit, valid_start, valid_end, status) VALUES
(1, '满 100 减 10', 100.00, 10.00, 'ALL', '', 1000, 0, 2, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 'ACTIVE'),
(2, '满 300 减 40', 300.00, 40.00, 'ALL', '', 1000, 1, 2, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 'ACTIVE'),
(3, '满 1000 减 120', 1000.00, 120.00, 'CATEGORY', '1', 500, 0, 1, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 'ACTIVE'),
(4, '满 200 减 30', 200.00, 30.00, 'CATEGORY', '3', 500, 0, 1, '2026-01-01 00:00:00', '2027-12-31 23:59:59', 'ACTIVE');

CREATE TABLE IF NOT EXISTS user_coupon (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    template_id BIGINT       NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'UNUSED',
    order_no    VARCHAR(32)  NULL,
    claimed_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at     DATETIME     NULL,
    expired_at  DATETIME     NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_status (user_id, status),
    KEY idx_template (template_id),
    KEY idx_order_no (order_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户优惠券';

-- 演示用户 1 预置一张「满 300 减 40」，方便直接验证算价与核销
INSERT IGNORE INTO user_coupon (id, user_id, template_id, status, claimed_at, expired_at) VALUES
(1, 1, 2, 'UNUSED', '2026-10-20 10:00:00', '2027-12-31 23:59:59');
