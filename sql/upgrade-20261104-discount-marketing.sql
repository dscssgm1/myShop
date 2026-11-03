-- 营销折扣体系升级：商家活动 / 平台招商活动（报名审核）/ 商家优惠券 / 多券叠加 / 优惠费用分摊
-- 适用范围：myShop-discount（活动、券、报名、算价）/ myShop-order（多券核销、承担金额记账）
--
-- 规则：
--   1. promotion.shop_id = 0 为平台活动；> 0 为商家自建活动（仅限本店商品，发布即生效）
--   2. enroll_required = 1 的平台活动需商家报名：商家提交本店在售商品 → 平台审核通过后参与活动
--   3. platform_bear_rate 为平台承担比例%（0~100），商家活动固定 0（商家全额承担）
--   4. coupon_template.shop_id = 0 为平台券；> 0 为商家券（商家全额承担），范围限本店
--   5. 全局叠加开关在 config_item discount 组：promotion-coupon-stackable / multi-coupon-enabled / max-coupons-per-order
--   6. 订单落库记录 merchant_bear_amount / platform_bear_amount 与实际使用的券明细（order_coupon）

ALTER TABLE promotion
    ADD COLUMN shop_id BIGINT NOT NULL DEFAULT 0 COMMENT '0 平台活动；>0 商家自建活动' AFTER id,
    ADD COLUMN enroll_required TINYINT NOT NULL DEFAULT 0 COMMENT '是否需要商家报名（仅平台活动）' AFTER coupon_stackable,
    ADD COLUMN platform_bear_rate DECIMAL(5, 2) NULL DEFAULT NULL COMMENT '平台承担优惠比例（%），空/0 表示商家全额承担' AFTER enroll_required,
    ADD KEY idx_shop (shop_id, status);

ALTER TABLE coupon_template
    ADD COLUMN shop_id BIGINT NOT NULL DEFAULT 0 COMMENT '0 平台券；>0 商家券' AFTER id,
    ADD KEY idx_shop (shop_id, status);

CREATE TABLE IF NOT EXISTS promotion_enrollment
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    promotion_id BIGINT       NOT NULL COMMENT '活动 id',
    shop_id      BIGINT       NOT NULL,
    shop_name    VARCHAR(100) NOT NULL DEFAULT '',
    product_id   BIGINT       NOT NULL,
    product_name VARCHAR(200) NOT NULL DEFAULT '',
    status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING 待审核 / APPROVED 通过 / REJECTED 驳回',
    reason       VARCHAR(255) NOT NULL DEFAULT '' COMMENT '驳回原因',
    applied_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at  DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_promotion_product (promotion_id, product_id),
    KEY idx_shop (shop_id, applied_at),
    KEY idx_promo_status (promotion_id, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '平台活动商家报名';

INSERT IGNORE INTO config_item (config_group, config_key, config_value, description) VALUES
('discount', 'promotion-coupon-stackable', 'true', '活动与优惠券是否允许同时使用（活动级开关可单独收紧）'),
('discount', 'multi-coupon-enabled', 'true', '是否允许一单叠加多张优惠券'),
('discount', 'max-coupons-per-order', '3', '单笔订单最多叠加的优惠券张数');

ALTER TABLE order_info
    ADD COLUMN merchant_bear_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '商家承担的优惠金额' AFTER coupon_discount,
    ADD COLUMN platform_bear_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '平台承担的优惠金额（补贴）' AFTER merchant_bear_amount;

CREATE TABLE IF NOT EXISTS order_coupon
(
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    order_no    VARCHAR(32)   NOT NULL,
    coupon_id   BIGINT        NOT NULL COMMENT 'user_coupon.id',
    title       VARCHAR(100)  NOT NULL DEFAULT '',
    discount    DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '该券在本订单抵扣的金额',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_coupon (order_no, coupon_id),
    KEY idx_coupon (coupon_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '订单使用的优惠券明细（一单多券）';
