-- 订单服务：购物车、订单、订单明细
-- 可导入 myshop（共库）或独立的订单库（分库），表名不含库名

CREATE TABLE IF NOT EXISTS order_cart (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    user_id      BIGINT         NOT NULL,
    product_id   BIGINT         NOT NULL,
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
    UNIQUE KEY uk_user_product (user_id, product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '购物车';

CREATE TABLE IF NOT EXISTS order_info (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    order_no           VARCHAR(32)    NOT NULL,
    user_id            BIGINT         NOT NULL,
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
    KEY idx_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单主表';

CREATE TABLE IF NOT EXISTS order_item (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    order_id      BIGINT         NOT NULL,
    order_no      VARCHAR(32)    NOT NULL,
    product_id    BIGINT         NOT NULL,
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

-- 演示数据：用户 1 的两笔已完成订单 + 一笔待付款订单
INSERT IGNORE INTO order_info
(id, order_no, user_id, status, original_amount, promotion_discount, coupon_discount, freight_amount, pay_amount,
 promotion_id, promotion_title, coupon_id, coupon_title, receiver_name, receiver_phone, receiver_address, remark,
 pay_time, ship_time, complete_time, created_at) VALUES
(1, 'MS202610100001', 1, 'COMPLETED', 3999.00, 0.00, 0.00, 0.00, 3999.00,
 NULL, '', NULL, '', '张三', '13800000000', '北京市朝阳区望京 SOHO T1', '',
 '2026-10-10 10:00:00', '2026-10-10 15:00:00', '2026-10-12 10:00:00', '2026-10-10 09:50:00'),
(2, 'MS202610200001', 1, 'COMPLETED', 378.80, 0.00, 0.00, 0.00, 378.80,
 NULL, '', NULL, '', '张三', '13800000000', '北京市朝阳区望京 SOHO T1', '',
 '2026-10-20 18:05:00', '2026-10-21 09:00:00', '2026-10-23 12:00:00', '2026-10-20 18:00:00'),
(3, 'MS202610260001', 1, 'UNPAID', 399.00, 0.00, 0.00, 0.00, 399.00,
 NULL, '', NULL, '', '张三', '13800000000', '北京市朝阳区望京 SOHO T1', '',
 NULL, NULL, NULL, '2026-10-26 20:00:00');

INSERT IGNORE INTO order_item
(id, order_id, order_no, product_id, product_name, product_emoji, product_color, shop_id, shop_name, price, quantity, subtotal) VALUES
(1, 1, 'MS202610100001', 1, '星辰 X5 Pro 5G 智能手机 12GB+256GB', '📱',
 'linear-gradient(135deg,#667eea,#764ba2)', 1, '数码旗舰店', 3999.00, 1, 3999.00),
(2, 2, 'MS202610200001', 6, '轻便缓震跑步鞋 男女同款运动鞋', '👟',
 'linear-gradient(135deg,#11998e,#38ef7d)', 3, '潮流服饰馆', 299.00, 1, 299.00),
(3, 2, 'MS202610200001', 7, '红富士苹果 5KG 装 新鲜时令水果', '🍎',
 'linear-gradient(135deg,#f83600,#f9d423)', 4, '鲜丰水果', 39.90, 2, 79.80),
(4, 3, 'MS202610260001', 2, '无线降噪蓝牙耳机 主动降噪 40h 续航', '🎧',
 'linear-gradient(135deg,#5b86e5,#36d1dc)', 1, '数码旗舰店', 399.00, 1, 399.00);
