-- 订单服务：购物车、订单（按店铺一店一单）、订单明细
-- 下单时按店铺拆分：每个店铺生成一个独立订单，独立支付、发货、收货。

CREATE TABLE IF NOT EXISTS order_cart (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    user_id      BIGINT         NOT NULL,
    product_id   BIGINT         NOT NULL,
    sku_id       BIGINT         NOT NULL DEFAULT 0 COMMENT 'SKU，0 表示无规格',
    sku_specs    VARCHAR(500)   NOT NULL DEFAULT '' COMMENT '规格快照',
    product_name VARCHAR(200)   NOT NULL DEFAULT '',
    price        DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    quantity     INT            NOT NULL DEFAULT 1,
    shop_id      BIGINT         NOT NULL DEFAULT 0,
    shop_name    VARCHAR(100)   NOT NULL DEFAULT '',
    emoji        VARCHAR(32)    NOT NULL DEFAULT '',
    color        VARCHAR(255)   NOT NULL DEFAULT '',
    created_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_product_sku (user_id, product_id, sku_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '购物车';

CREATE TABLE IF NOT EXISTS order_info (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    order_no           VARCHAR(32)    NOT NULL,
    user_id            BIGINT         NOT NULL,
    shop_id            BIGINT         NOT NULL DEFAULT 0 COMMENT '所属店铺：一店一单',
    shop_name          VARCHAR(100)   NOT NULL DEFAULT '',
    status             VARCHAR(16)    NOT NULL DEFAULT 'UNPAID',
    original_amount    DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    promotion_discount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    coupon_discount    DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    freight_amount     DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    pay_amount         DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    promotion_id       BIGINT         NULL,
    promotion_title    VARCHAR(100)   NOT NULL DEFAULT '',
    coupon_id          BIGINT         NULL,
    coupon_title       VARCHAR(100)   NOT NULL DEFAULT '',
    receiver_name      VARCHAR(50)    NOT NULL DEFAULT '',
    receiver_phone     VARCHAR(20)    NOT NULL DEFAULT '',
    receiver_address   VARCHAR(255)   NOT NULL DEFAULT '',
    remark             VARCHAR(255)   NOT NULL DEFAULT '',
    pay_time           DATETIME       NULL,
    ship_time          DATETIME       NULL,
    complete_time      DATETIME       NULL,
    cancel_time        DATETIME       NULL,
    created_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted            TINYINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_status (user_id, status),
    KEY idx_shop_status (shop_id, status),
    KEY idx_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单（每店铺一单）';

CREATE TABLE IF NOT EXISTS order_item (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    order_id      BIGINT         NOT NULL,
    order_no      VARCHAR(32)    NOT NULL,
    product_id    BIGINT         NOT NULL,
    sku_id        BIGINT         NOT NULL DEFAULT 0,
    sku_specs     VARCHAR(500)   NOT NULL DEFAULT '',
    product_name  VARCHAR(200)   NOT NULL DEFAULT '',
    product_emoji VARCHAR(32)    NOT NULL DEFAULT '',
    product_color VARCHAR(255)   NOT NULL DEFAULT '',
    shop_id       BIGINT         NOT NULL DEFAULT 0,
    shop_name     VARCHAR(100)   NOT NULL DEFAULT '',
    price         DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    quantity      INT            NOT NULL DEFAULT 1,
    subtotal      DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    created_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_order_id (order_id),
    KEY idx_order_no (order_no),
    KEY idx_product_id (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单明细';

-- 演示数据：用户 1 的三笔订单（分属三个店铺）+ 一笔待付款订单
INSERT IGNORE INTO order_info
(id, order_no, user_id, shop_id, shop_name, status, original_amount, promotion_discount, coupon_discount, freight_amount, pay_amount,
 promotion_id, promotion_title, coupon_id, coupon_title, receiver_name, receiver_phone, receiver_address, remark,
 pay_time, ship_time, complete_time, created_at) VALUES
(1, 'MS202610100001', 1, 1, '数码旗舰店', 'COMPLETED', 3999.00, 0.00, 0.00, 0.00, 3999.00,
 NULL, '', NULL, '', '张三', '13800000000', '北京市朝阳区望京 SOHO T1', '',
 '2026-10-10 10:00:00', '2026-10-10 15:00:00', '2026-10-12 10:00:00', '2026-10-10 09:50:00'),
(2, 'MS202610200001', 1, 3, '潮流服饰馆', 'COMPLETED', 299.00, 0.00, 0.00, 0.00, 299.00,
 NULL, '', NULL, '', '张三', '13800000000', '北京市朝阳区望京 SOHO T1', '',
 '2026-10-20 18:05:00', '2026-10-21 09:00:00', '2026-10-23 12:00:00', '2026-10-20 18:00:00'),
(3, 'MS202610200002', 1, 4, '鲜丰水果', 'COMPLETED', 79.80, 0.00, 0.00, 0.00, 79.80,
 NULL, '', NULL, '', '张三', '13800000000', '北京市朝阳区望京 SOHO T1', '',
 '2026-10-20 18:05:00', '2026-10-21 09:00:00', '2026-10-23 12:00:00', '2026-10-20 18:00:00'),
(4, 'MS202610260001', 1, 1, '数码旗舰店', 'UNPAID', 399.00, 0.00, 0.00, 0.00, 399.00,
 NULL, '', NULL, '', '张三', '13800000000', '北京市朝阳区望京 SOHO T1', '',
 NULL, NULL, NULL, '2026-10-26 20:00:00');

INSERT IGNORE INTO order_item
(id, order_id, order_no, product_id, sku_id, sku_specs,
 product_name, product_emoji, product_color, shop_id, shop_name, price, quantity, subtotal) VALUES
(1, 1, 'MS202610100001', 1, 0, '',
 '星辰 X5 Pro 5G 智能手机 12GB+256GB', '📱', 'linear-gradient(135deg,#667eea,#764ba2)', 1, '数码旗舰店', 3999.00, 1, 3999.00),
(2, 2, 'MS202610200001', 6, 0, '',
 '轻便缓震跑步鞋 男女同款运动鞋', '👟', 'linear-gradient(135deg,#11998e,#38ef7d)', 3, '潮流服饰馆', 299.00, 1, 299.00),
(3, 3, 'MS202610200002', 7, 0, '',
 '红富士苹果 5KG 装 新鲜时令水果', '🍎', 'linear-gradient(135deg,#f83600,#f9d423)', 4, '鲜丰水果', 39.90, 2, 79.80),
(4, 4, 'MS202610260001', 2, 0, '',
 '无线降噪蓝牙耳机 主动降噪 40h 续航', '🎧', 'linear-gradient(135deg,#5b86e5,#36d1dc)', 1, '数码旗舰店', 399.00, 1, 399.00);
