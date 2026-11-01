-- 商品服务：两级分类、品牌、规格模板、商品（SPU）与 SKU
-- 可导入 myshop（共库）或独立的商品库（分库）

-- 分类（两级：parent_id=0 为大类，否则为小类）
CREATE TABLE IF NOT EXISTS product_category (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    parent_id  BIGINT       NOT NULL DEFAULT 0,
    level      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 大类 2 小类',
    name       VARCHAR(50)  NOT NULL,
    icon       VARCHAR(32)  NOT NULL DEFAULT '',
    color      VARCHAR(128) NOT NULL DEFAULT '',
    sort       INT          NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_parent (parent_id),
    KEY idx_sort (sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品分类（两级）';

-- 品牌
CREATE TABLE IF NOT EXISTS product_brand (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(50)  NOT NULL,
    logo       VARCHAR(255) NOT NULL DEFAULT '',
    sort       INT          NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_sort (sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品品牌';

-- 规格模板
CREATE TABLE IF NOT EXISTS spec_template (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    name       VARCHAR(50) NOT NULL,
    sort       INT         NOT NULL DEFAULT 0,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '规格模板';

-- 规格选项
CREATE TABLE IF NOT EXISTS spec_option (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    template_id BIGINT      NOT NULL,
    value       VARCHAR(50) NOT NULL,
    sort        INT         NOT NULL DEFAULT 0,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_template (template_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '规格选项';

-- 商品（SPU）
CREATE TABLE IF NOT EXISTS product (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    name           VARCHAR(200)   NOT NULL,
    brand_id       BIGINT         NULL,
    category_id    BIGINT         NOT NULL COMMENT '主分类（大类）',
    shop_id        BIGINT         NOT NULL DEFAULT 0,
    shop_name      VARCHAR(100)   NOT NULL DEFAULT '',
    price          DECIMAL(10, 2) NOT NULL COMMENT '展示价（最低 SKU 价或默认价）',
    origin_price   DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    discount_price DECIMAL(10, 2) NULL COMMENT '促销价，空则无折扣',
    stock          INT            NOT NULL DEFAULT 0 COMMENT '总库存（SKU 之和）',
    sales          INT            NOT NULL DEFAULT 0,
    rating         DECIMAL(3, 1)  NOT NULL DEFAULT 5.0,
    emoji          VARCHAR(32)    NOT NULL DEFAULT '',
    color          VARCHAR(255)   NOT NULL DEFAULT '',
    tags           VARCHAR(255)   NOT NULL DEFAULT '',
    description    TEXT           NULL,
    specs          TEXT           NULL COMMENT '商品规格定义（JSON 数组）',
    status         TINYINT        NOT NULL DEFAULT 1 COMMENT '1 上架 0 下架',
    audit_status   VARCHAR(16)    NOT NULL DEFAULT 'APPROVED' COMMENT 'PENDING/APPROVED/REJECTED',
    off_reason     VARCHAR(255)   NOT NULL DEFAULT '' COMMENT '下架原因',
    created_at     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted        TINYINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_category (category_id),
    KEY idx_brand (brand_id),
    KEY idx_shop (shop_id),
    KEY idx_status_sales (status, sales),
    KEY idx_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品';

-- 商品多类目关联
CREATE TABLE IF NOT EXISTS product_category_rel (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    product_id  BIGINT   NOT NULL,
    category_id BIGINT   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_category (product_id, category_id),
    KEY idx_category (category_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品类目关联';

-- 商品 SKU（规格组合，独立价格与库存）
CREATE TABLE IF NOT EXISTS product_sku (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    product_id    BIGINT         NOT NULL,
    specs         VARCHAR(500)   NOT NULL DEFAULT '' COMMENT '规格 JSON，如 {"颜色":"黑","尺码":"L"}',
    price         DECIMAL(10, 2) NOT NULL,
    origin_price  DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    stock         INT            NOT NULL DEFAULT 0,
    sales         INT            NOT NULL DEFAULT 0,
    status        TINYINT        NOT NULL DEFAULT 1,
    created_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品 SKU';

-- ---------------------------------------------------------------- 种子数据

INSERT IGNORE INTO product_category (id, parent_id, level, name, icon, color, sort) VALUES
(1, 0, 1, '手机数码', '📱', '#e6f4ff', 1),
(2, 0, 1, '家用电器', '📺', '#fff7e6', 2),
(3, 0, 1, '服饰鞋包', '👔', '#f9f0ff', 3),
(4, 0, 1, '食品生鲜', '🍎', '#f6ffed', 4),
(5, 0, 1, '图书文具', '📚', '#fff0f6', 5),
(6, 0, 1, '美妆个护', '💄', '#fff1f0', 6),
(11, 1, 2, '手机', '', '', 1),
(12, 1, 2, '耳机/音箱', '', '', 2),
(13, 1, 2, '智能手表', '', '', 3),
(21, 2, 2, '电视', '', '', 1),
(22, 2, 2, '洗衣机', '', '', 2),
(31, 3, 2, '男装', '', '', 1),
(32, 3, 2, '鞋靴', '', '', 2),
(41, 4, 2, '新鲜水果', '', '', 1),
(42, 4, 2, '肉禽蛋品', '', '', 2),
(51, 5, 2, '计算机', '', '', 1),
(52, 5, 2, '文具', '', '', 2),
(61, 6, 2, '面部护肤', '', '', 1);

INSERT IGNORE INTO product_brand (id, name, sort) VALUES
(1, '华为', 1), (2, '小米', 2), (3, '耐克', 3), (4, '阿迪达斯', 4), (5, '苹果', 5), (6, '三星', 6);

INSERT IGNORE INTO spec_template (id, name, sort) VALUES
(1, '颜色', 1), (2, '尺码', 2), (3, '容量', 3), (4, '规格', 4);

INSERT IGNORE INTO spec_option (id, template_id, value, sort) VALUES
(1, 1, '黑', 1), (2, 1, '白', 2), (3, 1, '红', 3), (4, 1, '蓝', 4),
(5, 2, 'M', 1), (6, 2, 'L', 2), (7, 2, 'XL', 3), (8, 2, 'XXL', 4),
(9, 3, '128GB', 1), (10, 3, '256GB', 2),
(11, 4, '5KG', 1), (12, 4, '10KG', 2);

INSERT IGNORE INTO product
(id, name, brand_id, category_id, shop_id, shop_name, price, origin_price, discount_price, stock, sales, rating, emoji, color, tags, description, specs, status, audit_status) VALUES
(1, '星辰 X5 Pro 5G 智能手机 12GB+256GB', 1, 1, 1, '数码旗舰店', 3999.00, 4599.00, 3799.00, 500, 2314, 4.8, '📱', 'linear-gradient(135deg,#667eea,#764ba2)', '自营,秒杀', '6.7 英寸 2K 高刷屏，1 亿像素影像系统，5500mAh 大电池 + 100W 快充。', '[{"name":"颜色","options":["曜石黑","星河银"]},{"name":"容量","options":["128GB","256GB"]}]', 1, 'APPROVED'),
(2, '无线降噪蓝牙耳机 主动降噪 40h 续航', 2, 1, 1, '数码旗舰店', 399.00, 599.00, NULL, 1200, 8765, 4.7, '🎧', 'linear-gradient(135deg,#5b86e5,#36d1dc)', '满减', '主动降噪，入耳式设计，单次续航 10 小时。', '[{"name":"颜色","options":["云白","曜黑"]}]', 1, 'APPROVED'),
(3, '4K 超高清智能电视机 65 英寸', 6, 2, 2, '家电优选', 3299.00, 3999.00, NULL, 200, 1102, 4.6, '📺', 'linear-gradient(135deg,#f7971e,#ffd200)', '自营,满减', '65 英寸 4K 全面屏，支持 HDR10+ 与远场语音。', '[{"name":"规格","options":["65英寸"]}]', 1, 'APPROVED'),
(4, '家用滚筒洗衣机 10KG 变频洗烘一体', 6, 2, 2, '家电优选', 2699.00, 3199.00, 2399.00, 150, 876, 4.5, '🧺', 'linear-gradient(135deg,#8e9eab,#eef2f3)', '满减', '10KG 大容量，变频电机，洗烘一体。', '[{"name":"规格","options":["10KG"]}]', 1, 'APPROVED'),
(5, '复古牛仔外套 男士宽松潮流夹克', 3, 3, 3, '潮流服饰馆', 229.00, 399.00, NULL, 800, 3421, 4.4, '🧥', 'linear-gradient(135deg,#4568dc,#b06ab3)', '折扣', '复古水洗牛仔面料，宽松版型。', '[{"name":"颜色","options":["复古蓝"]},{"name":"尺码","options":["M","L","XL","XXL"]}]', 1, 'APPROVED'),
(6, '轻便缓震跑步鞋 男女同款运动鞋', 3, 3, 3, '潮流服饰馆', 299.00, 499.00, NULL, 1000, 5608, 4.7, '👟', 'linear-gradient(135deg,#11998e,#38ef7d)', '秒杀', '轻量缓震中底，透气网面。', '[{"name":"颜色","options":["黑白","灰蓝"]},{"name":"尺码","options":["36","37","38","39","40","41","42","43","44","45"]}]', 1, 'APPROVED'),
(7, '红富士苹果 5KG 装 新鲜时令水果', NULL, 4, 4, '鲜丰水果', 39.90, 59.90, NULL, 2000, 12345, 4.9, '🍎', 'linear-gradient(135deg,#f83600,#f9d423)', '秒杀,产地直发', '产地直发，脆甜多汁，单果约 200g。', '[{"name":"规格","options":["5KG/箱"]}]', 1, 'APPROVED'),
(8, '进口牛排套餐 原切西冷 10 片装', NULL, 4, 4, '鲜丰水果', 169.00, 259.00, NULL, 600, 2345, 4.6, '🥩', 'linear-gradient(135deg,#c31432,#240b36)', '满减', '原切西冷牛排，单片 150g，共 10 片。', '[{"name":"规格","options":["150g×10片"]}]', 1, 'APPROVED'),
(9, '《深入理解计算机系统》中文版 精装', NULL, 5, 3, '潮流服饰馆', 99.00, 139.00, NULL, 300, 987, 4.9, '📚', 'linear-gradient(135deg,#1e3c72,#2a5298)', '满减', '计算机系统经典教材。', '[]', 1, 'APPROVED'),
(10, '高颜值按动中性笔 0.5mm 12 支装', NULL, 5, 3, '潮流服饰馆', 19.90, 35.00, 15.90, 5000, 8907, 4.5, '🖊️', 'linear-gradient(135deg,#ff9966,#ff5e62)', '折扣', '按动式设计，书写顺滑。', '[{"name":"颜色","options":["黑","蓝","红"]}]', 1, 'APPROVED'),
(11, '烟酰胺美白精华液 30ml 提亮肤色', NULL, 6, 3, '潮流服饰馆', 159.00, 239.00, NULL, 900, 4567, 4.6, '🧴', 'linear-gradient(135deg,#fd1d1d,#fcb045)', '满减,热卖', '烟酰胺复配传明酸，提亮肤色。', '[]', 1, 'APPROVED'),
(12, '氨基酸温和洁面乳 100g 敏感肌可用', NULL, 6, 3, '潮流服饰馆', 59.00, 89.00, 49.00, 2000, 6789, 4.7, '🧼', 'linear-gradient(135deg,#43cea2,#185a9d)', '折扣', '氨基酸表活，温和清洁不紧绷。', '[]', 1, 'APPROVED');

-- 商品 SKU 示例（有规格的商品）
INSERT IGNORE INTO product_sku (id, product_id, specs, price, origin_price, stock, sales, status) VALUES
(1, 1, '{"颜色":"曜石黑","容量":"128GB"}', 3999.00, 4599.00, 300, 1500, 1),
(2, 1, '{"颜色":"曜石黑","容量":"256GB"}', 4299.00, 4899.00, 150, 600, 1),
(3, 1, '{"颜色":"星河银","容量":"128GB"}', 3999.00, 4599.00, 50, 214, 1),
(4, 5, '{"颜色":"复古蓝","尺码":"M"}', 229.00, 399.00, 200, 900, 1),
(5, 5, '{"颜色":"复古蓝","尺码":"L"}', 229.00, 399.00, 300, 1200, 1),
(6, 5, '{"颜色":"复古蓝","尺码":"XL"}', 229.00, 399.00, 200, 800, 1);

-- 商品多类目示例（数码旗舰店同时经营手机数码与家用电器）
INSERT IGNORE INTO product_category_rel (product_id, category_id) VALUES
(1, 1), (1, 11),
(2, 1), (2, 12),
(3, 2), (3, 21),
(4, 2), (4, 22);
