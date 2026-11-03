-- 商品佣金：平台按类目费率从商品成交款中抽佣
-- 适用范围：myShop-product（费率配置）/ myShop-order（快照、记账、冲销、账单）
--
-- 规则：
--   1. 费率配置在 product_category.commission_rate（%，0~100）；小类为空时继承大类，大类为空视为不抽佣
--   2. 下单时把商品类目快照到 order_item.category_id，结算以快照为准（历史订单为 0 时回查 product）
--   3. 订单确认收货 / 签收满 7 天自动完成时，逐商品行按其类目费率计佣，汇总为一笔订单佣金
--   4. 售后退款成功时自动生成一笔 REVERSAL 冲销流水，佣金净额归零

ALTER TABLE product_category
    ADD COLUMN commission_rate DECIMAL(5, 2) NULL DEFAULT NULL
        COMMENT '平台佣金费率（%），小类为空时继承大类，大类为空表示不抽佣' AFTER sort;

-- 存量大类先统一给 5%，可在「分类管理」页按类目调整
UPDATE product_category SET commission_rate = 5.00 WHERE level = 1 AND commission_rate IS NULL;

ALTER TABLE order_item
    ADD COLUMN category_id BIGINT NOT NULL DEFAULT 0 COMMENT '下单时快照的商品类目 id' AFTER product_id;

CREATE TABLE IF NOT EXISTS commission_record
(
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    order_no          VARCHAR(32)    NOT NULL COMMENT '订单号（一店一单）',
    type              VARCHAR(16)    NOT NULL DEFAULT 'COMMISSION' COMMENT 'COMMISSION 订单完成计佣 / REVERSAL 售后退款冲销',
    shop_id           BIGINT         NOT NULL DEFAULT 0,
    shop_name         VARCHAR(100)   NOT NULL DEFAULT '',
    goods_amount      DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '计入佣金的商品实收金额（冲销为负）',
    commission_rate   DECIMAL(5, 2)  NOT NULL DEFAULT 0.00 COMMENT '综合佣金费率%（记账时快照）',
    commission_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '平台佣金（冲销为负）',
    merchant_amount   DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '商家净得（冲销为负）',
    detail            VARCHAR(500)   NOT NULL DEFAULT '' COMMENT '各类目计佣明细',
    reason            VARCHAR(255)   NOT NULL DEFAULT '',
    created_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_type (order_no, type),
    KEY idx_shop (shop_id, created_at),
    KEY idx_created (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '商品佣金流水';
