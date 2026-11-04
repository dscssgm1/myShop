-- 积分商城改版 + 下单积分抵现（2026-11-04）
-- 1) 积分商城从「积分换商品」改为「积分换优惠券」：coupon_template 增加积分价，
--    points_price > 0 的券只在积分商城兑换，不进免费领券中心。
-- 2) 下单积分抵现：订单/订单组记录抵扣积分与金额（计入平台承担 platform_bear_amount）。
-- 3) 配置项替换：mall-rate（积分商城倍数）下线，新增抵现比例与上限。
-- 用法：docker exec -i myshop-mysql mysql -uroot -proot --default-character-set=utf8mb4 myshop < sql/upgrade-20261104-points-coupon.sql

ALTER TABLE coupon_template
    ADD COLUMN points_price INT NULL COMMENT '积分兑换价：NULL/0 普通免费领取券；>0 仅在积分商城用积分兑换' AFTER per_user_limit;

ALTER TABLE order_info
    ADD COLUMN points_used      INT            NOT NULL DEFAULT 0 COMMENT '本单抵现消耗的积分' AFTER platform_bear_amount,
    ADD COLUMN points_deduction DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '本单积分抵现金额（平台承担）' AFTER points_used;

ALTER TABLE order_group
    ADD COLUMN points_used      INT            NOT NULL DEFAULT 0 COMMENT '整组抵现消耗的积分' AFTER platform_bear_amount,
    ADD COLUMN points_deduction DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '整组积分抵现金额（平台承担）' AFTER points_used;

-- 抵现配置（新库导入 platform.sql 已含；老库补种，幂等）
INSERT IGNORE INTO config_item (config_group, config_key, config_value, description) VALUES
('points-rule', 'deduct-points-per-yuan', '100', '下单抵现：多少积分抵 1 元（0 或未配置表示关闭抵现）'),
('points-rule', 'deduct-max-percent', '50', '下单抵现：最多抵应付金额的百分比');

-- 积分换商品已下线，其倍数配置移除
DELETE FROM config_item WHERE config_group = 'points-rule' AND config_key = 'mall-rate';
