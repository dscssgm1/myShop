-- 存量库升级：物流签收联动订单（2026-11-03）
-- 正向运单签收后记录签收时间，签收满 7 天买家未确认则自动完成订单。
-- 新库直接导入 order.sql 即可，无需执行本脚本。
-- 用法：docker exec -i myshop-mysql mysql -uroot -proot --default-character-set=utf8mb4 myshop < sql/upgrade-20261103-logistics-linkage.sql

ALTER TABLE order_info
    ADD COLUMN signed_time DATETIME NULL COMMENT '物流签收时间，自动确认收货起算点' AFTER ship_time,
    ADD KEY idx_signed_complete (status, signed_time);
