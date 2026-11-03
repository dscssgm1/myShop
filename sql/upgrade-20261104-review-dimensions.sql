-- 评价按原型拆成三个维度：商品质量 / 服务态度 / 物流速度（支持半星，综合分 = 三者平均）
-- 存量评价三维度回填为原综合分，历史数据展示不受影响

ALTER TABLE review
  ADD COLUMN quality_rating   DECIMAL(2,1) NULL COMMENT '商品质量评分（1-5，支持半星）' AFTER rating,
  ADD COLUMN service_rating   DECIMAL(2,1) NULL COMMENT '服务态度评分（1-5，支持半星）' AFTER quality_rating,
  ADD COLUMN logistics_rating DECIMAL(2,1) NULL COMMENT '物流速度评分（1-5，支持半星）' AFTER service_rating;

UPDATE review
SET quality_rating = rating,
    service_rating = rating,
    logistics_rating = rating
WHERE quality_rating IS NULL;
