-- ============================================================
-- 评价模块（review）
-- 商品评分 = 该商品所有展示中评价的均值
-- 店铺评分 = 该店铺所有商品所有展示中评价的均值
-- ============================================================

CREATE TABLE IF NOT EXISTS review (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    product_id    BIGINT        NOT NULL,
    product_name  VARCHAR(128)  NOT NULL,
    shop_id       BIGINT        NOT NULL,
    order_no      VARCHAR(32)   NOT NULL COMMENT '来源订单（用于校验已购与防重复）',
    user_id       BIGINT        NOT NULL,
    nickname      VARCHAR(64)   NOT NULL DEFAULT '',
    rating        TINYINT       NOT NULL COMMENT '综合评分（三维度平均取整）',
    quality_rating   DECIMAL(2,1) NULL COMMENT '商品质量评分（1-5，支持半星）',
    service_rating   DECIMAL(2,1) NULL COMMENT '服务态度评分（1-5，支持半星）',
    logistics_rating DECIMAL(2,1) NULL COMMENT '物流速度评分（1-5，支持半星）',
    content       VARCHAR(1000) NOT NULL,
    images        TEXT          NULL COMMENT '评价图片 URL JSON 数组',
    reply         TEXT          NULL COMMENT '商家回复',
    reply_at      DATETIME      NULL COMMENT '商家回复时间',
    status        VARCHAR(16)   NOT NULL DEFAULT 'VISIBLE' COMMENT 'VISIBLE 展示 / PENDING 待审核 / HIDDEN 已隐藏',
    reported      TINYINT       NOT NULL DEFAULT 0 COMMENT '商家是否举报 1 是',
    report_reason VARCHAR(255)  NULL,
    report_by     BIGINT        NULL,
    reported_at   DATETIME      NULL,
    audit_reason  VARCHAR(255)  NULL COMMENT '平台审核意见',
    audited_by    BIGINT        NULL,
    audited_at    DATETIME      NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_order_product (user_id, order_no, product_id),
    KEY idx_product (product_id, status),
    KEY idx_shop (shop_id, status),
    KEY idx_user (user_id),
    KEY idx_status (status, reported)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品评价';

-- 演示评价：买家「购物达人」对已购买商品的真实风格评价
INSERT IGNORE INTO review
(id, product_id, product_name, shop_id, order_no, user_id, nickname, rating, content, status, reported, created_at, updated_at) VALUES
(1, 1, '星辰 X5 Pro 5G 智能手机 12GB+256GB', 1, 'MS202610100001', 2, '购物达人', 5,
 '手机手感很好，屏幕显示细腻，拍夜景也很清晰，店家发货速度快，五星好评。', 'VISIBLE', 0,
 '2026-10-25 20:10:00', '2026-10-25 20:10:00'),
(2, 6, '轻便缓震跑步鞋 男女同款运动鞋', 3, 'MS202610200001', 2, '购物达人', 4,
 '鞋子很轻，缓震不错，日常跑步完全够用，尺码偏大半码，建议按提示选。', 'VISIBLE', 0,
 '2026-10-26 09:30:00', '2026-10-26 09:30:00'),
(3, 7, '红富士苹果 5KG 装 新鲜时令水果', 4, 'MS202610200002', 2, '购物达人', 4,
 '苹果个头均匀，脆甜多汁，包装没有磕碰，性价比很高，下次还会回购。', 'VISIBLE', 0,
 '2026-10-26 09:35:00', '2026-10-26 09:35:00');

-- 回填商品评分：评价均值（无评价的商品保持原值）
UPDATE product p
SET p.rating = COALESCE((SELECT ROUND(AVG(r.rating), 1)
                         FROM review r
                         WHERE r.product_id = p.id AND r.status = 'VISIBLE' AND r.deleted = 0),
                        p.rating);

-- 回填店铺评分：全店所有商品评价均值
UPDATE shop s
SET s.rating = (SELECT ROUND(AVG(r.rating), 2)
                FROM review r
                WHERE r.shop_id = s.id AND r.status = 'VISIBLE' AND r.deleted = 0)
WHERE EXISTS (SELECT 1 FROM review r
              WHERE r.shop_id = s.id AND r.status = 'VISIBLE' AND r.deleted = 0);
