-- 商家规格库 + 商品富文本详情（2026-11-04）
-- 适用范围：myShop-product（规格库、商品详情）/ myShop-order（库存变更按 SKU 生效）
--
-- 规则：
--   1. 商家在「我的规格」中自行维护规格与规格值（merchant_spec，按店铺隔离），编辑商品时从这里选择
--   2. 平台配置中心「规格模板」保留为公共参考模板，名称/规格值输入时支持联想补全
--   3. 商品新增 detail 富文本详情字段，商品编辑器中原「商品描述」由富文本详情替换
--   4. 订单库存变更明细增加 sku_id：下单扣减 / 取消·退款归还均按（商品, SKU）生效

-- 商家规格库
CREATE TABLE IF NOT EXISTS merchant_spec (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    shop_id    BIGINT      NOT NULL COMMENT '所属店铺',
    name       VARCHAR(50) NOT NULL COMMENT '规格名称，如 颜色 / 尺码',
    options    TEXT        NOT NULL COMMENT '规格值 JSON 数组，如 ["红色","蓝色"]',
    sort       INT         NOT NULL DEFAULT 0,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_shop (shop_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商家规格库';

-- 商品富文本详情（执行一次；MySQL 不支持 ADD COLUMN IF NOT EXISTS）
ALTER TABLE product
    ADD COLUMN detail MEDIUMTEXT NULL COMMENT '商品详情（富文本 HTML）' AFTER description;

-- 存量商品把纯文本描述转成富文本详情，买家端商品详情展示不受影响
UPDATE product
   SET detail = CONCAT('<p>', description, '</p>')
 WHERE description IS NOT NULL AND description <> ''
   AND (detail IS NULL OR detail = '');

-- 示例规格库（按 seed 店铺预置，商家可在「我的规格」中自行调整）
INSERT IGNORE INTO merchant_spec (id, shop_id, name, options, sort) VALUES
(1, 1, '颜色', '["曜石黑","星河银","云白","深空灰"]', 1),
(2, 1, '容量', '["128GB","256GB","512GB"]', 2),
(3, 2, '规格', '["65英寸","55英寸","10KG"]', 1),
(4, 3, '颜色', '["复古蓝","黑白","灰蓝","酒红","白"]', 1),
(5, 3, '尺码', '["S","M","L","XL","XXL"]', 2),
(6, 4, '规格', '["5KG/箱","150g×10片"]', 1);
