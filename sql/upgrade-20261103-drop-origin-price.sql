-- 2026-11-03 移除商品原价：只保留售价（price）与促销价（discount_price）
-- 原价（origin_price）此前仅用于前端划线展示，不参与任何交易计算，随本版删除
ALTER TABLE product DROP COLUMN origin_price;
ALTER TABLE product_sku DROP COLUMN origin_price;
