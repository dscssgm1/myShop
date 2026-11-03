-- 快照表补商品主图：订单行 / 积分商城商品 / 积分兑换记录
-- 历史数据按商品关联回填，所有列表页统一显示图片（无图商品前端回退 emoji 占位）

ALTER TABLE order_item
  ADD COLUMN product_cover_image VARCHAR(512) NULL COMMENT '商品主图快照' AFTER product_color;
UPDATE order_item oi JOIN product p ON p.id = oi.product_id
SET oi.product_cover_image = p.cover_image
WHERE oi.product_cover_image IS NULL OR oi.product_cover_image = '';

ALTER TABLE sys_points_mall_item
  ADD COLUMN product_cover_image VARCHAR(512) NULL COMMENT '商品主图快照' AFTER product_color;
UPDATE sys_points_mall_item pm JOIN product p ON p.id = pm.product_id
SET pm.product_cover_image = p.cover_image
WHERE pm.product_cover_image IS NULL OR pm.product_cover_image = '';

ALTER TABLE sys_points_exchange
  ADD COLUMN item_cover VARCHAR(512) NULL COMMENT '商品主图快照' AFTER item_emoji;
UPDATE sys_points_exchange pe JOIN sys_points_mall_item pm ON pm.id = pe.item_id
SET pe.item_cover = pm.product_cover_image
WHERE pe.item_cover IS NULL OR pe.item_cover = '';
