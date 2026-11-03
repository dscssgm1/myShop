-- 存量库升级：服务保障纳入配置中心，商品与服务保障 key 关联（2026-11-03）
-- 新库直接导入 product.sql / platform.sql 即可，无需执行本脚本。
-- 用法：docker exec -i myshop-mysql mysql -uroot -proot --default-character-set=utf8mb4 myshop < sql/upgrade-20261103-product-service-guarantee.sql

-- 商品 ↔ 配置中心服务保障 key 关联表
CREATE TABLE IF NOT EXISTS product_config_rel (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    product_id  BIGINT       NOT NULL,
    config_key  VARCHAR(64)  NOT NULL COMMENT 'config_item 中的服务保障 key（config_group=service-guarantee）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_config (product_id, config_key),
    KEY idx_config_key (config_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品服务保障关联';

-- 服务保障默认配置（按 key 一项一条，与支付方式同模型）
INSERT IGNORE INTO config_item (config_group, config_key, config_value, description) VALUES
('service-guarantee', 'seven-day-return', '{"name":"七天无理由退货","icon":"↩️","enabled":true}', '七天无理由退货'),
('service-guarantee', 'authentic-guarantee', '{"name":"正品保障","icon":"✅","enabled":true}', '正品保障'),
('service-guarantee', 'national-warranty', '{"name":"全国联保","icon":"🛡️","enabled":true}', '全国联保'),
('service-guarantee', 'fast-delivery', '{"name":"极速发货","icon":"🚀","enabled":true}', '极速发货');
