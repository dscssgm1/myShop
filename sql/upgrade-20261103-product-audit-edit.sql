-- 存量库升级：商品修改需平台审核（上架中商品审核期间展示原内容）+ 驳回原因（2026-11-03）
-- 新库直接导入 product.sql 即可，无需执行本脚本。
-- 用法：docker exec -i myshop-mysql mysql -uroot -proot --default-character-set=utf8mb4 myshop < sql/upgrade-20261103-product-audit-edit.sql

ALTER TABLE product
    ADD COLUMN audit_reason VARCHAR(200) NOT NULL DEFAULT '' COMMENT '最近一次审核驳回原因' AFTER audit_status,
    ADD COLUMN pending_data TEXT NULL COMMENT '上架中商品的待审核修改（JSON，审核通过后应用，驳回即丢弃）' AFTER audit_reason;
