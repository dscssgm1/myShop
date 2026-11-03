-- 物流分红：物流公司分红账户余额 + 分红流水表 + 平台分成比例配置
-- 适用范围：myShop-logistics / myShop-platform

ALTER TABLE logistics_company
    ADD COLUMN balance DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '分红账户余额' AFTER color;

CREATE TABLE IF NOT EXISTS dividend_record (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    tracking_no     VARCHAR(32)   NULL COMMENT '运单号（调账记录为 NULL）',
    type            VARCHAR(16)   NOT NULL DEFAULT 'DIVIDEND' COMMENT 'DIVIDEND 签收分红 / ADJUST 平台调账',
    order_no        VARCHAR(32)   NOT NULL DEFAULT '',
    shop_id         BIGINT        NOT NULL DEFAULT 0,
    shop_name       VARCHAR(100)  NOT NULL DEFAULT '',
    company_id      BIGINT        NOT NULL,
    company_name    VARCHAR(100)  NOT NULL DEFAULT '',
    freight_amount  DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '订单运费',
    platform_ratio  DECIMAL(5, 2) NOT NULL DEFAULT 0.00 COMMENT '平台分成比例%（记账时快照）',
    platform_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '平台分成金额',
    company_amount  DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '物流公司分成金额（调账可为负）',
    reason          VARCHAR(255)  NOT NULL DEFAULT '' COMMENT '调账原因',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tracking_no (tracking_no),
    KEY idx_company (company_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '物流分红流水';

INSERT IGNORE INTO config_item (config_group, config_key, config_value, description) VALUES
('dividend', 'platform-ratio', '15', '平台与物流公司的运费分成比例（平台占运费的百分比）');
