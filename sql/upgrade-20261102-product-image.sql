-- 存量库升级：商品增加图片字段（2026-11-02）
-- 新库直接导入 product.sql 即可，无需执行本脚本。
-- 用法：docker exec -i myshop-mysql mysql -uroot -proot --default-character-set=utf8mb4 myshop < sql/upgrade-20261102-product-image.sql

ALTER TABLE product
    ADD COLUMN cover_image VARCHAR(500)  NOT NULL DEFAULT '' COMMENT '主图 URL，空则前端用 emoji 占位' AFTER color,
    ADD COLUMN images      VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '图集 URL，逗号分隔' AFTER cover_image;
