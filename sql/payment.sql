-- 支付服务：支付单
-- 可导入 myshop（共库）或独立的支付库（分库），表名不含库名

CREATE TABLE IF NOT EXISTS payment_order (
    id         BIGINT         NOT NULL AUTO_INCREMENT,
    payment_no VARCHAR(32)    NOT NULL,
    order_no   VARCHAR(32)    NOT NULL,
    user_id    BIGINT         NOT NULL,
    amount     DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    channel    VARCHAR(16)    NOT NULL,
    status     VARCHAR(16)    NOT NULL DEFAULT 'PENDING',
    trade_no   VARCHAR(64)    NULL,
    paid_at    DATETIME       NULL,
    created_at DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_no (payment_no),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user (user_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '支付单';
