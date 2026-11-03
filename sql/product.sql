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
    commission_rate DECIMAL(5, 2) NULL DEFAULT NULL COMMENT '平台佣金费率（%），小类为空时继承大类，大类为空表示不抽佣',
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

-- 商家规格库（规格与规格值由商家自行维护，按店铺隔离）
CREATE TABLE IF NOT EXISTS merchant_spec (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    shop_id    BIGINT      NOT NULL COMMENT '所属店铺',
    name       VARCHAR(50) NOT NULL COMMENT '规格名称，如 颜色 / 尺码',
    options    TEXT        NOT NULL COMMENT '规格值 JSON 数组，如 ["红色","蓝色"]',
    sort       INT         NOT NULL DEFAULT 0,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_shop (shop_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商家规格库';

INSERT IGNORE INTO merchant_spec (id, shop_id, name, options, sort) VALUES
(1, 1, '颜色', '["曜石黑","星河银","云白","深空灰"]', 1),
(2, 1, '容量', '["128GB","256GB","512GB"]', 2),
(3, 2, '规格', '["65英寸","55英寸","10KG"]', 1),
(4, 3, '颜色', '["复古蓝","黑白","灰蓝","酒红","白"]', 1),
(5, 3, '尺码', '["S","M","L","XL","XXL"]', 2),
(6, 4, '规格', '["5KG/箱","150g×10片"]', 1);

-- 商品（SPU）
CREATE TABLE IF NOT EXISTS product (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    name           VARCHAR(200)   NOT NULL,
    brand_id       BIGINT         NULL,
    category_id    BIGINT         NOT NULL COMMENT '主分类（大类）',
    shop_id        BIGINT         NOT NULL DEFAULT 0,
    shop_name      VARCHAR(100)   NOT NULL DEFAULT '',
    price          DECIMAL(10, 2) NOT NULL COMMENT '售价（实际成交基准价）',
    discount_price DECIMAL(10, 2) NULL COMMENT '促销价，空则无折扣',
    stock          INT            NOT NULL DEFAULT 0 COMMENT '总库存（SKU 之和）',
    sales          INT            NOT NULL DEFAULT 0,
    rating         DECIMAL(3, 1)  NOT NULL DEFAULT 5.0,
    emoji          VARCHAR(32)    NOT NULL DEFAULT '',
    color          VARCHAR(255)   NOT NULL DEFAULT '',
    cover_image    VARCHAR(500)   NOT NULL DEFAULT '' COMMENT '主图 URL，空则前端用 emoji 占位',
    images         VARCHAR(2000)  NOT NULL DEFAULT '' COMMENT '图集 URL，逗号分隔',
    tags           VARCHAR(255)   NOT NULL DEFAULT '',
    description    TEXT           NULL,
    detail         MEDIUMTEXT     NULL COMMENT '商品详情（富文本 HTML）',
    specs          TEXT           NULL COMMENT '商品规格定义（JSON 数组）',
    status         TINYINT        NOT NULL DEFAULT 1 COMMENT '1 上架 0 下架',
    audit_status   VARCHAR(16)    NOT NULL DEFAULT 'APPROVED' COMMENT 'PENDING/APPROVED/REJECTED',
    audit_reason   VARCHAR(200)   NOT NULL DEFAULT '' COMMENT '最近一次审核驳回原因',
    pending_data   TEXT           NULL COMMENT '上架中商品的待审核修改（JSON，审核通过后应用，驳回即丢弃）',
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

-- 商品服务保障关联（config_key 对应配置中心 service-guarantee 组的 key）
CREATE TABLE IF NOT EXISTS product_config_rel (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    product_id  BIGINT       NOT NULL,
    config_key  VARCHAR(64)  NOT NULL COMMENT 'config_item 中的服务保障 key（config_group=service-guarantee）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_config (product_id, config_key),
    KEY idx_config_key (config_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品服务保障关联';

-- 商品 SKU（规格组合，独立价格与库存）
CREATE TABLE IF NOT EXISTS product_sku (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    product_id    BIGINT         NOT NULL,
    specs         VARCHAR(500)   NOT NULL DEFAULT '' COMMENT '规格 JSON，如 {"颜色":"黑","尺码":"L"}',
    price         DECIMAL(10, 2) NOT NULL,
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

INSERT IGNORE INTO product_category (id, parent_id, level, name, icon, color, sort, commission_rate) VALUES
(1, 0, 1, '手机数码', '📱', '#e6f4ff', 1, 5.00),
(2, 0, 1, '家用电器', '📺', '#fff7e6', 2, 5.00),
(3, 0, 1, '服饰鞋包', '👔', '#f9f0ff', 3, 5.00),
(4, 0, 1, '食品生鲜', '🍎', '#f6ffed', 4, 5.00),
(5, 0, 1, '图书文具', '📚', '#fff0f6', 5, 5.00),
(6, 0, 1, '美妆个护', '💄', '#fff1f0', 6, 5.00),
(11, 1, 2, '手机', '', '', 1, NULL),
(12, 1, 2, '耳机/音箱', '', '', 2, NULL),
(13, 1, 2, '智能手表', '', '', 3, NULL),
(21, 2, 2, '电视', '', '', 1, NULL),
(22, 2, 2, '洗衣机', '', '', 2, NULL),
(31, 3, 2, '男装', '', '', 1, NULL),
(32, 3, 2, '鞋靴', '', '', 2, NULL),
(41, 4, 2, '新鲜水果', '', '', 1, NULL),
(42, 4, 2, '肉禽蛋品', '', '', 2, NULL),
(51, 5, 2, '计算机', '', '', 1, NULL),
(52, 5, 2, '文具', '', '', 2, NULL),
(61, 6, 2, '面部护肤', '', '', 1, NULL);

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
(id, name, brand_id, category_id, shop_id, shop_name, price, discount_price, stock, sales, rating, emoji, color, tags, description, specs, status, audit_status) VALUES
(1, '星辰 X5 Pro 5G 智能手机 12GB+256GB', 1, 1, 1, '数码旗舰店', 3999.00, 3799.00, 500, 2314, 4.8, '📱', 'linear-gradient(135deg,#667eea,#764ba2)', '自营,秒杀', '6.7 英寸 2K 高刷屏，1 亿像素影像系统，5500mAh 大电池 + 100W 快充。', '[{"name":"颜色","options":["曜石黑","星河银"]},{"name":"容量","options":["128GB","256GB"]}]', 1, 'APPROVED'),
(2, '无线降噪蓝牙耳机 主动降噪 40h 续航', 2, 1, 1, '数码旗舰店', 399.00, NULL, 1200, 8765, 4.7, '🎧', 'linear-gradient(135deg,#5b86e5,#36d1dc)', '满减', '主动降噪，入耳式设计，单次续航 10 小时。', '[{"name":"颜色","options":["云白","曜黑"]}]', 1, 'APPROVED'),
(3, '4K 超高清智能电视机 65 英寸', 6, 2, 2, '家电优选', 3299.00, NULL, 200, 1102, 4.6, '📺', 'linear-gradient(135deg,#f7971e,#ffd200)', '自营,满减', '65 英寸 4K 全面屏，支持 HDR10+ 与远场语音。', '[{"name":"规格","options":["65英寸"]}]', 1, 'APPROVED'),
(4, '家用滚筒洗衣机 10KG 变频洗烘一体', 6, 2, 2, '家电优选', 2699.00, 2399.00, 150, 876, 4.5, '🧺', 'linear-gradient(135deg,#8e9eab,#eef2f3)', '满减', '10KG 大容量，变频电机，洗烘一体。', '[{"name":"规格","options":["10KG"]}]', 1, 'APPROVED'),
(5, '复古牛仔外套 男士宽松潮流夹克', 3, 3, 3, '潮流服饰馆', 229.00, NULL, 800, 3421, 4.4, '🧥', 'linear-gradient(135deg,#4568dc,#b06ab3)', '折扣', '复古水洗牛仔面料，宽松版型。', '[{"name":"颜色","options":["复古蓝"]},{"name":"尺码","options":["M","L","XL","XXL"]}]', 1, 'APPROVED'),
(6, '轻便缓震跑步鞋 男女同款运动鞋', 3, 3, 3, '潮流服饰馆', 299.00, NULL, 1000, 5608, 4.7, '👟', 'linear-gradient(135deg,#11998e,#38ef7d)', '秒杀', '轻量缓震中底，透气网面。', '[{"name":"颜色","options":["黑白","灰蓝"]},{"name":"尺码","options":["36","37","38","39","40","41","42","43","44","45"]}]', 1, 'APPROVED'),
(7, '红富士苹果 5KG 装 新鲜时令水果', NULL, 4, 4, '鲜丰水果', 39.90, NULL, 2000, 12345, 4.9, '🍎', 'linear-gradient(135deg,#f83600,#f9d423)', '秒杀,产地直发', '产地直发，脆甜多汁，单果约 200g。', '[{"name":"规格","options":["5KG/箱"]}]', 1, 'APPROVED'),
(8, '进口牛排套餐 原切西冷 10 片装', NULL, 4, 4, '鲜丰水果', 169.00, NULL, 600, 2345, 4.6, '🥩', 'linear-gradient(135deg,#c31432,#240b36)', '满减', '原切西冷牛排，单片 150g，共 10 片。', '[{"name":"规格","options":["150g×10片"]}]', 1, 'APPROVED'),
(9, '《深入理解计算机系统》中文版 精装', NULL, 5, 3, '潮流服饰馆', 99.00, NULL, 300, 987, 4.9, '📚', 'linear-gradient(135deg,#1e3c72,#2a5298)', '满减', '计算机系统经典教材。', '[]', 1, 'APPROVED'),
(10, '高颜值按动中性笔 0.5mm 12 支装', NULL, 5, 3, '潮流服饰馆', 19.90, 15.90, 5000, 8907, 4.5, '🖊️', 'linear-gradient(135deg,#ff9966,#ff5e62)', '折扣', '按动式设计，书写顺滑。', '[{"name":"颜色","options":["黑","蓝","红"]}]', 1, 'APPROVED'),
(11, '烟酰胺美白精华液 30ml 提亮肤色', NULL, 6, 3, '潮流服饰馆', 159.00, NULL, 900, 4567, 4.6, '🧴', 'linear-gradient(135deg,#fd1d1d,#fcb045)', '满减,热卖', '烟酰胺复配传明酸，提亮肤色。', '[]', 1, 'APPROVED'),
(12, '氨基酸温和洁面乳 100g 敏感肌可用', NULL, 6, 3, '潮流服饰馆', 59.00, 49.00, 2000, 6789, 4.7, '🧼', 'linear-gradient(135deg,#43cea2,#185a9d)', '折扣', '氨基酸表活，温和清洁不紧绷。', '[]', 1, 'APPROVED');

-- 商品 SKU 示例（有规格的商品）
INSERT IGNORE INTO product_sku (id, product_id, specs, price, stock, sales, status) VALUES
(1, 1, '{"颜色":"曜石黑","容量":"128GB"}', 3999.00, 300, 1500, 1),
(2, 1, '{"颜色":"曜石黑","容量":"256GB"}', 4299.00, 150, 600, 1),
(3, 1, '{"颜色":"星河银","容量":"128GB"}', 3999.00, 50, 214, 1),
(4, 5, '{"颜色":"复古蓝","尺码":"M"}', 229.00, 200, 900, 1),
(5, 5, '{"颜色":"复古蓝","尺码":"L"}', 229.00, 300, 1200, 1),
(6, 5, '{"颜色":"复古蓝","尺码":"XL"}', 229.00, 200, 800, 1);

-- 商品多类目示例（数码旗舰店同时经营手机数码与家用电器）
INSERT IGNORE INTO product_category_rel (product_id, category_id) VALUES
(1, 1), (1, 11),
(2, 1), (2, 12),
(3, 2), (3, 21),
(4, 2), (4, 22);

-- 2026-11-03 扩展种子商品：覆盖 6 店 6 类 33 个商品，图片为可直连的 Unsplash 图床 URL
-- 图片字段说明：cover_image 主图，images 图集（逗号分隔，前端 detail 页做缩略图切换）

INSERT IGNORE INTO product
(id, name, brand_id, category_id, shop_id, shop_name, price, discount_price, stock, sales, rating, emoji, color, cover_image, images, tags, description, specs, status, audit_status) VALUES
-- ===== 手机数码（数码旗舰店） =====
(19, '全面屏智能手机 128GB 全网通双卡双待', 5, 1, 1, '数码旗舰店', 5999.00, 5699.00, 400, 1987, 4.8, '📱', 'linear-gradient(135deg,#3a7bd5,#00d2ff)', 'https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=800&q=80&auto=format&fit=crop,https://images.unsplash.com/photo-1592750475338-74b7b21085ab?w=800&q=80&auto=format&fit=crop', '自营,热卖', '6.1 英寸全面屏，仿生芯片流畅省电，双卡双待，支持面容识别与 20W 快充。', '[{"name":"颜色","options":["深空灰","银色"]},{"name":"容量","options":["128GB","256GB"]}]', 1, 'APPROVED'),
(20, '旗舰智能手机 Pro 三摄 256GB 深空灰', 5, 1, 1, '数码旗舰店', 8999.00, 8599.00, 200, 866, 4.9, '📱', 'linear-gradient(135deg,#232526,#414345)', 'https://images.unsplash.com/photo-1592750475338-74b7b21085ab?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1592750475338-74b7b21085ab?w=800&q=80&auto=format&fit=crop,https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=800&q=80&auto=format&fit=crop', '自营,秒杀', '三摄系统支持夜景与深度融合，超视网膜 XDR 显示屏，不锈钢边框质感出众。', '[{"name":"颜色","options":["深空灰","海蓝色"]}]', 1, 'APPROVED'),
(21, '智能手表 44mm GPS 运动健康监测', 5, 1, 1, '数码旗舰店', 2999.00, 2799.00, 300, 1567, 4.8, '⌚', 'linear-gradient(135deg,#2c3e50,#4ca1af)', 'https://images.unsplash.com/photo-1546868871-7041f2a55e12?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1546868871-7041f2a55e12?w=800&q=80&auto=format&fit=crop', '自营', '全天候视网膜显示屏，心率血氧监测，50 米防水，运动模式丰富。', '[{"name":"表壳尺寸","options":["44mm","40mm"]}]', 1, 'APPROVED'),
(22, '平板电脑 11 英寸全面屏 256GB', 5, 1, 1, '数码旗舰店', 6999.00, 6499.00, 150, 645, 4.9, '📲', 'linear-gradient(135deg,#4b6cb7,#182848)', 'https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?w=800&q=80&auto=format&fit=crop', '自营,热卖', '11 英寸 Liquid 视网膜屏，支持触控笔与妙控键盘，学习办公娱乐全能。', '[{"name":"容量","options":["128GB","256GB"]}]', 1, 'APPROVED'),
(23, '轻薄笔记本电脑 13.3 英寸 高色域', 5, 1, 1, '数码旗舰店', 8999.00, NULL, 100, 432, 4.8, '💻', 'linear-gradient(135deg,#8e9eab,#eef2f3)', 'https://images.unsplash.com/photo-1496181133206-80ce9b88a853?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1496181133206-80ce9b88a853?w=800&q=80&auto=format&fit=crop', '自营', '轻至 1.4kg 金属机身，高色域视网膜屏，全天候续航，办公学习首选。', '[]', 1, 'APPROVED'),
(24, '专业微单相机 4K 视频 vlog 套装', NULL, 1, 1, '数码旗舰店', 7599.00, 7399.00, 80, 156, 4.7, '📷', 'linear-gradient(135deg,#141e30,#243b55)', 'https://images.unsplash.com/photo-1516035069371-29a1b244cc32?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1516035069371-29a1b244cc32?w=800&q=80&auto=format&fit=crop', '满减', '约 2420 万有效像素，4K 视频拍摄，五轴防抖，摄影创作入门首选。', '[{"name":"规格","options":["单机身","含镜头套装"]}]', 1, 'APPROVED'),
-- ===== 家用电器（家电优选） =====
(25, '智能语音电视 55 英寸 4K 超高清', 6, 2, 2, '家电优选', 2599.00, 2399.00, 200, 987, 4.6, '📺', 'linear-gradient(135deg,#1f1c2c,#928dab)', 'https://images.unsplash.com/photo-1593359677879-a4bb92f829d1?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1593359677879-a4bb92f829d1?w=800&q=80&auto=format&fit=crop,https://images.unsplash.com/photo-1552975084-6e027cd345c2?w=800&q=80&auto=format&fit=crop', '自营,满减', '55 英寸 4K 超高清面板，AI 远场语音，海量影视资源，支持手机投屏。', '[{"name":"规格","options":["55英寸"]}]', 1, 'APPROVED'),
(26, '高清智能电视 43 英寸 卧室款', 6, 2, 2, '家电优选', 1499.00, NULL, 300, 1245, 4.5, '📺', 'linear-gradient(135deg,#ffecd2,#fcb69f)', 'https://images.unsplash.com/photo-1552975084-6e027cd345c2?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1552975084-6e027cd345c2?w=800&q=80&auto=format&fit=crop', '自营', '43 英寸全高清，护眼防蓝光，机身纤薄，卧室客厅都合适。', '[{"name":"规格","options":["43英寸"]}]', 1, 'APPROVED'),
(27, '复古单门小冰箱 120L 薄荷绿', NULL, 2, 2, '家电优选', 1299.00, 1199.00, 150, 534, 4.7, '🧊', 'linear-gradient(135deg,#96e6a1,#d4fc79)', 'https://images.unsplash.com/photo-1571175443880-49e1d25b2bc5?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1571175443880-49e1d25b2bc5?w=800&q=80&auto=format&fit=crop', '折扣', '复古薄荷绿外观，低噪静音，单身公寓与出租屋颜值担当。', '[{"name":"规格","options":["120L"]}]', 1, 'APPROVED'),
(28, '变频滚筒洗衣机 8KG 除菌洗', 6, 2, 2, '家电优选', 1999.00, 1799.00, 120, 456, 4.6, '🌀', 'linear-gradient(135deg,#bdc3c7,#2c3e50)', 'https://images.unsplash.com/photo-1626806787461-102c1bfaaea1?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1626806787461-102c1bfaaea1?w=800&q=80&auto=format&fit=crop', '满减,热卖', '8KG 大容量，变频电机节能省电，高温除菌洗，全家衣物轻松搞定。', '[{"name":"规格","options":["8KG"]}]', 1, 'APPROVED'),
(29, '家用电烤箱 30L 上下独立控温', NULL, 2, 2, '家电优选', 399.00, 329.00, 260, 876, 4.5, '🔥', 'linear-gradient(135deg,#f7971e,#ffd200)', 'https://images.unsplash.com/photo-1574269909862-7e1d70bb8078?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1574269909862-7e1d70bb8078?w=800&q=80&auto=format&fit=crop', '折扣', '30L 大容量，上下管独立控温，发酵解冻烘焙一机搞定。', '[{"name":"容量","options":["30L"]}]', 1, 'APPROVED'),
-- ===== 服饰鞋包（潮流服饰馆） =====
(30, '飞织跑步鞋 缓震透气 男女同款', 3, 3, 3, '潮流服饰馆', 799.00, 699.00, 500, 2109, 4.8, '👟', 'linear-gradient(135deg,#cb2d3e,#ef473a)', 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=800&q=80&auto=format&fit=crop', '秒杀,热卖', '飞织鞋面轻盈透气，泡棉中底缓震回弹，日常慢跑与通勤皆宜。', '[{"name":"颜色","options":["酒红","黑色"]},{"name":"尺码","options":["39","40","41","42","43","44"]}]', 1, 'APPROVED'),
(31, '复古休闲板鞋 小麦色防滑耐磨', 3, 3, 3, '潮流服饰馆', 599.00, NULL, 400, 987, 4.7, '👟', 'linear-gradient(135deg,#c79081,#dfa579)', 'https://images.unsplash.com/photo-1549298916-b41d501d3772?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1549298916-b41d501d3772?w=800&q=80&auto=format&fit=crop', '自营', '经典板鞋鞋型，小麦配色百搭耐看，橡胶大底防滑耐磨。', '[{"name":"颜色","options":["小麦棕","黑白"]},{"name":"尺码","options":["39","40","41","42","43","44"]}]', 1, 'APPROVED'),
(32, '纯棉圆领短袖T恤 基础百搭款', 3, 3, 3, '潮流服饰馆', 129.00, 99.00, 2000, 5678, 4.6, '👕', 'linear-gradient(135deg,#f5f7fa,#c3cfe2)', 'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=800&q=80&auto=format&fit=crop', '折扣,热卖', '210g 重磅纯棉，版型挺括不变形，基础色系衣橱必备。', '[{"name":"颜色","options":["白","黑"]},{"name":"尺码","options":["M","L","XL","XXL"]}]', 1, 'APPROVED'),
(33, 'PU皮机车夹克 短款修身外套', NULL, 3, 3, '潮流服饰馆', 499.00, 429.00, 300, 654, 4.5, '🧥', 'linear-gradient(135deg,#232526,#414345)', 'https://images.unsplash.com/photo-1551028719-00167b16eac5?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1551028719-00167b16eac5?w=800&q=80&auto=format&fit=crop', '折扣', '简约机车版型，金属拉链细节，秋冬叠穿有型不挑人。', '[{"name":"尺码","options":["M","L","XL"]}]', 1, 'APPROVED'),
(34, '商务双肩包 15.6英寸电脑仓 防泼水', NULL, 3, 3, '潮流服饰馆', 259.00, 219.00, 600, 1876, 4.7, '🎒', 'linear-gradient(135deg,#2c3e50,#4ca1af)', 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=800&q=80&auto=format&fit=crop', '满减', '大容量主仓 + 独立电脑仓，防泼水面料，通勤出差两相宜。', '[{"name":"颜色","options":["藏青","黑色"]}]', 1, 'APPROVED'),
(35, '复古圆框太阳镜 UV400 偏光', NULL, 3, 3, '潮流服饰馆', 329.00, 279.00, 350, 765, 4.6, '🕶️', 'linear-gradient(135deg,#d4af37,#f9ef8f)', 'https://images.unsplash.com/photo-1511499767150-a48a237f0083?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1511499767150-a48a237f0083?w=800&q=80&auto=format&fit=crop', '折扣,热卖', '金属细框复古圆框，UV400 偏光镜片，通勤度假随手一戴就有型。', '[{"name":"镜框","options":["金框墨绿片"]}]', 1, 'APPROVED'),
-- ===== 食品生鲜（鲜丰水果） =====
(36, '阿克苏冰糖心苹果 5KG 礼盒装', NULL, 4, 4, '鲜丰水果', 49.90, 45.90, 1500, 4321, 4.8, '🍎', 'linear-gradient(135deg,#e53935,#e35d5b)', 'https://images.unsplash.com/photo-1560806887-1e4cd0b6cbd6?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1560806887-1e4cd0b6cbd6?w=800&q=80&auto=format&fit=crop', '秒杀,产地直发', '新疆阿克苏直发，果核透明如冰糖心，脆甜多汁，礼盒装送礼体面。', '[{"name":"规格","options":["5KG/箱"]}]', 1, 'APPROVED'),
(37, '赣南脐橙 带叶鲜果 10斤装', NULL, 4, 4, '鲜丰水果', 59.90, 49.90, 1200, 3876, 4.7, '🍊', 'linear-gradient(135deg,#ff9966,#ff5e62)', 'https://images.unsplash.com/photo-1547514701-42782101795e?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1547514701-42782101795e?w=800&q=80&auto=format&fit=crop', '产地直发,满减', '赣南富硒土壤孕育，带叶采摘锁鲜，皮薄化渣，酸甜多汁。', '[{"name":"规格","options":["10斤装"]}]', 1, 'APPROVED'),
(38, '海南金煌芒大果 5斤装 单果400g+', NULL, 4, 4, '鲜丰水果', 39.90, NULL, 1800, 2987, 4.6, '🥭', 'linear-gradient(135deg,#f8b500,#ffe259)', 'https://images.unsplash.com/photo-1553279768-865429fa0078?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1553279768-865429fa0078?w=800&q=80&auto=format&fit=crop', '产地直发', '树上自然熟，单果 400g 起，果肉细腻无纤维，香甜浓醇。', '[{"name":"规格","options":["5斤装"]}]', 1, 'APPROVED'),
(39, '散养土鸡蛋 谷物喂养 30枚装', NULL, 4, 4, '鲜丰水果', 49.90, 42.90, 900, 2109, 4.8, '🥚', 'linear-gradient(135deg,#ffecd2,#fcb69f)', 'https://images.unsplash.com/photo-1582722872445-44dc5f7e3c8f?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1582722872445-44dc5f7e3c8f?w=800&q=80&auto=format&fit=crop', '热卖', '林间散养土鸡，谷物喂养，蛋黄饱满色泽金黄，蒸煮炖炒皆香。', '[{"name":"规格","options":["30枚装"]}]', 1, 'APPROVED'),
(40, '全脂纯牛奶 1L×6盒 整箱装', NULL, 4, 4, '鲜丰水果', 89.00, 79.00, 800, 1765, 4.7, '🥛', 'linear-gradient(135deg,#e0eaef,#f5f7fa)', 'https://images.unsplash.com/photo-1563636619-e9143da7973b?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1563636619-e9143da7973b?w=800&q=80&auto=format&fit=crop', '折扣', '生牛乳制造，每 100ml 含 3.6g 优质乳蛋白，早餐营养必备。', '[{"name":"规格","options":["1L×6盒"]}]', 1, 'APPROVED'),
(41, '手工夹心巧克力礼盒 24粒装', NULL, 4, 4, '鲜丰水果', 129.00, 99.00, 500, 987, 4.8, '🍫', 'linear-gradient(135deg,#5a3f37,#2d132c)', 'https://images.unsplash.com/photo-1599599810769-bcde5a160d32?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1599599810769-bcde5a160d32?w=800&q=80&auto=format&fit=crop', '秒杀', '手工浇模夹心巧克力，多种口味混搭，礼盒装送礼有心意。', '[{"name":"规格","options":["24粒礼盒"]}]', 1, 'APPROVED'),
(42, '谷饲眼肉牛排 原切厚切 8片装', NULL, 4, 4, '鲜丰水果', 159.00, 139.00, 400, 876, 4.6, '🥩', 'linear-gradient(135deg,#c31432,#240b36)', 'https://images.unsplash.com/photo-1546964124-0cce460f38ef?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1546964124-0cce460f38ef?w=800&q=80&auto=format&fit=crop', '满减', '谷饲眼肉芯，原切无拼接，厚切多汁，煎烤 5 分钟即享。', '[{"name":"规格","options":["150g×8片"]}]', 1, 'APPROVED'),
-- ===== 图书文具（书虫书店） =====
(43, '商业思维经典套装（全 9 册）', NULL, 5, 5, '书虫书店', 399.00, 299.00, 200, 654, 4.9, '📘', 'linear-gradient(135deg,#f4c4f3,#fc67fa)', 'https://images.unsplash.com/photo-1512820790803-83ca734da794?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1512820790803-83ca734da794?w=800&q=80&auto=format&fit=crop', '满减,热卖', '从零到一、终身成长等商业经典合集，创业者与职场人必读。', '[]', 1, 'APPROVED'),
(44, '世界名著典藏全集（全 12 册）', NULL, 5, 5, '书虫书店', 299.00, 199.00, 300, 876, 4.8, '📚', 'linear-gradient(135deg,#1e3c72,#2a5298)', 'https://images.unsplash.com/photo-1481627834876-b7833e8f5570?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1481627834876-b7833e8f5570?w=800&q=80&auto=format&fit=crop', '折扣', '精选世界文学名著，精装典藏译本，书架必备收藏级套装。', '[]', 1, 'APPROVED'),
(45, '生活美学图文随笔礼盒（全 3 册）', NULL, 5, 5, '书虫书店', 168.00, NULL, 260, 432, 4.7, '📗', 'linear-gradient(135deg,#fbc2eb,#a6c1ee)', 'https://images.unsplash.com/photo-1543002588-bfa74002ed7e?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1543002588-bfa74002ed7e?w=800&q=80&auto=format&fit=crop', '自营', '全彩图文精装，旅居与生活美学随笔，适合送给热爱生活的人。', '[]', 1, 'APPROVED'),
(46, '经典金属圆珠笔 商务签字笔', NULL, 5, 5, '书虫书店', 59.00, NULL, 1000, 2345, 4.6, '🖊️', 'linear-gradient(135deg,#89f7fe,#66a6ff)', 'https://images.unsplash.com/photo-1585336261022-680e295ce3fe?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1585336261022-680e295ce3fe?w=800&q=80&auto=format&fit=crop', '自营', '黄铜笔身烤漆工艺，旋转出芯书写顺滑，商务办公质感之选。', '[{"name":"颜色","options":["墨蓝","银色"]}]', 1, 'APPROVED'),
(47, 'B5 线圈笔记本 横线本 3 本装', NULL, 5, 5, '书虫书店', 29.90, 24.90, 3000, 5678, 4.7, '📓', 'linear-gradient(135deg,#fdfbfb,#ebedee)', 'https://images.unsplash.com/photo-1531346878377-a5be20888e57?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1531346878377-a5be20888e57?w=800&q=80&auto=format&fit=crop', '折扣,热卖', 'B5 大开本 80g 米白纸，不洇墨不断墨，学生党刷题笔记首选。', '[{"name":"规格","options":["B5×3本"]}]', 1, 'APPROVED'),
-- ===== 美妆个护（美妍美妆） =====
(48, '温和洁面+保湿面霜 护肤套装', NULL, 6, 6, '美妍美妆', 299.00, 259.00, 400, 1234, 4.7, '🧴', 'linear-gradient(135deg,#a18cd1,#fbc2eb)', 'https://images.unsplash.com/photo-1571781926291-c477ebfd024b?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1571781926291-c477ebfd024b?w=800&q=80&auto=format&fit=crop', '满减,热卖', '氨基酸洁面 + 修护面霜基础护肤组合，敏感肌友好，早晚两步到位。', '[]', 1, 'APPROVED'),
(49, '修护发膜 250ml 深层滋养顺滑', NULL, 6, 6, '美妍美妆', 129.00, NULL, 600, 876, 4.6, '💇', 'linear-gradient(135deg,#e8f0fe,#c9d6ff)', 'https://images.unsplash.com/photo-1608248543803-ba4f8c70ae0b?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1608248543803-ba4f8c70ae0b?w=800&q=80&auto=format&fit=crop', '自营', '植物修护精华，5 分钟深层滋养，干枯烫染发质重焕顺滑。', '[{"name":"规格","options":["250ml"]}]', 1, 'APPROVED'),
(50, '经典五号淡香精 100ml', NULL, 6, 6, '美妍美妆', 1379.00, 1289.00, 100, 213, 4.9, '🌸', 'linear-gradient(135deg,#fdfbfb,#ebedee)', 'https://images.unsplash.com/photo-1541643600914-78b084683601?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1541643600914-78b084683601?w=800&q=80&auto=format&fit=crop', '满减', '经典乙醛花香调，清雅持久，仪式感满满的传世之作。', '[]', 1, 'APPROVED'),
(51, '香氛身体护理套装 玫瑰金系列', NULL, 6, 6, '美妍美妆', 199.00, 169.00, 350, 534, 4.6, '🧼', 'linear-gradient(135deg,#f6d365,#fda085)', 'https://images.unsplash.com/photo-1631730486572-226d1f595b68?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1631730486572-226d1f595b68?w=800&q=80&auto=format&fit=crop', '折扣', '香氛沐浴露 + 身体乳组合，玫瑰金简约瓶身，留香持久。', '[]', 1, 'APPROVED');

-- 扩展商品 SKU 示例（手机 / 跑鞋 / T恤）
INSERT IGNORE INTO product_sku (id, product_id, specs, price, stock, sales, status) VALUES
(19, 19, '{"颜色":"深空灰","容量":"128GB"}', 5999.00, 300, 900, 1),
(20, 19, '{"颜色":"深空灰","容量":"256GB"}', 6499.00, 100, 300, 1),
(21, 30, '{"颜色":"酒红","尺码":"41"}', 699.00, 150, 600, 1),
(22, 30, '{"颜色":"酒红","尺码":"42"}', 699.00, 200, 700, 1),
(23, 32, '{"颜色":"白","尺码":"M"}', 99.00, 800, 2000, 1),
(24, 32, '{"颜色":"白","尺码":"L"}', 99.00, 800, 2200, 1);

-- 扩展商品类目关联（主类目 + 子类目）
INSERT IGNORE INTO product_category_rel (product_id, category_id) VALUES
(19, 1), (19, 11),
(20, 1), (20, 11),
(21, 1), (21, 13),
(22, 1),
(25, 2), (25, 21),
(26, 2), (26, 21),
(28, 2), (28, 22),
(30, 3), (30, 32),
(31, 3), (31, 32),
(36, 4), (36, 41),
(37, 4), (37, 41),
(38, 4), (38, 41),
(39, 4), (39, 42),
(43, 5),
(44, 5),
(45, 5),
(46, 5), (46, 52),
(47, 5), (47, 52),
(48, 6), (48, 61);

-- ===== 2026-11-03 第二批扩展：老商品 1-12 补图 + 新增商品 52-92（见 upgrade-20261103-photos-and-41-products.sql） =====
-- 2026-11-03 第二批扩展：老商品 1-12 按类目就近补图 + 新增 41 个商品（52-92，覆盖 6 店全类目）
-- 图片均为可直连的 Unsplash 图床 URL；cover_image 主图，images 图集（逗号分隔）
-- 老商品仅补图不改名不改价；新商品全部 status=1 / audit_status='APPROVED'

-- ===== 一、老商品 1-12 就近配图 =====
UPDATE product SET
  cover_image = 'https://images.unsplash.com/photo-1580910051074-3eb694886505?w=800&q=80&auto=format&fit=crop',
  images      = 'https://images.unsplash.com/photo-1580910051074-3eb694886505?w=800&q=80&auto=format&fit=crop'
WHERE id = 1;
UPDATE product SET
  cover_image = 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=800&q=80&auto=format&fit=crop',
  images      = 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=800&q=80&auto=format&fit=crop'
WHERE id = 2;
UPDATE product SET
  cover_image = 'https://images.unsplash.com/photo-1593359677879-a4bb92f829d1?w=800&q=80&auto=format&fit=crop',
  images      = 'https://images.unsplash.com/photo-1593359677879-a4bb92f829d1?w=800&q=80&auto=format&fit=crop,https://images.unsplash.com/photo-1552975084-6e027cd345c2?w=800&q=80&auto=format&fit=crop'
WHERE id = 3;
UPDATE product SET
  cover_image = 'https://images.unsplash.com/photo-1610557892470-55d9e80c0bce?w=800&q=80&auto=format&fit=crop',
  images      = 'https://images.unsplash.com/photo-1610557892470-55d9e80c0bce?w=800&q=80&auto=format&fit=crop'
WHERE id = 4;
UPDATE product SET
  cover_image = 'https://images.unsplash.com/photo-1591047139829-d91aecb6caea?w=800&q=80&auto=format&fit=crop',
  images      = 'https://images.unsplash.com/photo-1591047139829-d91aecb6caea?w=800&q=80&auto=format&fit=crop'
WHERE id = 5;
UPDATE product SET
  cover_image = 'https://images.unsplash.com/photo-1600185365483-26d7a4cc7519?w=800&q=80&auto=format&fit=crop',
  images      = 'https://images.unsplash.com/photo-1600185365483-26d7a4cc7519?w=800&q=80&auto=format&fit=crop'
WHERE id = 6;
UPDATE product SET
  cover_image = 'https://images.unsplash.com/photo-1568702846914-96b305d2aaeb?w=800&q=80&auto=format&fit=crop',
  images      = 'https://images.unsplash.com/photo-1568702846914-96b305d2aaeb?w=800&q=80&auto=format&fit=crop'
WHERE id = 7;
UPDATE product SET
  cover_image = 'https://images.unsplash.com/photo-1607623814075-e51df1bdc82f?w=800&q=80&auto=format&fit=crop',
  images      = 'https://images.unsplash.com/photo-1607623814075-e51df1bdc82f?w=800&q=80&auto=format&fit=crop'
WHERE id = 8;
UPDATE product SET
  cover_image = 'https://images.unsplash.com/photo-1495446815901-a7297e633e8d?w=800&q=80&auto=format&fit=crop',
  images      = 'https://images.unsplash.com/photo-1495446815901-a7297e633e8d?w=800&q=80&auto=format&fit=crop,https://images.unsplash.com/photo-1532012197267-da84d127e765?w=800&q=80&auto=format&fit=crop,https://images.unsplash.com/photo-1519682337058-a94d519337bc?w=800&q=80&auto=format&fit=crop,https://images.unsplash.com/photo-1507842217343-583bb7270b66?w=800&q=80&auto=format&fit=crop'
WHERE id = 9;
UPDATE product SET
  cover_image = 'https://images.unsplash.com/photo-1583485088034-697b5bc54ccd?w=800&q=80&auto=format&fit=crop',
  images      = 'https://images.unsplash.com/photo-1583485088034-697b5bc54ccd?w=800&q=80&auto=format&fit=crop'
WHERE id = 10;
UPDATE product SET
  cover_image = 'https://images.unsplash.com/photo-1596462502278-27bfdc403348?w=800&q=80&auto=format&fit=crop',
  images      = 'https://images.unsplash.com/photo-1596462502278-27bfdc403348?w=800&q=80&auto=format&fit=crop'
WHERE id = 11;
UPDATE product SET
  cover_image = 'https://images.unsplash.com/photo-1556228720-195a672e8a03?w=800&q=80&auto=format&fit=crop',
  images      = 'https://images.unsplash.com/photo-1556228720-195a672e8a03?w=800&q=80&auto=format&fit=crop'
WHERE id = 12;

-- ===== 二、新增商品 52-92 =====
INSERT IGNORE INTO product
(id, name, brand_id, category_id, shop_id, shop_name, price, discount_price, stock, sales, rating, emoji, color, cover_image, images, tags, description, specs, status, audit_status) VALUES
-- ===== 数码数码配件（数码旗舰店） =====
(52, '头戴式无线降噪耳机 40h 续航', 5, 1, 1, '数码旗舰店', 699.00, 599.00, 400, 1345, 4.8, '🎧', 'linear-gradient(135deg,#4b6cb7,#182848)', 'https://images.unsplash.com/photo-1546435770-a3e426bf472b?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1546435770-a3e426bf472b?w=800&q=80&auto=format&fit=crop,https://images.unsplash.com/photo-1583394838336-acd977736f90?w=800&q=80&auto=format&fit=crop', '自营,热卖', '主动降噪一键静音，40mm 大驱动单元，佩戴轻若无物，通勤出差神器。', '[{"name":"颜色","options":["曜石黑","珍珠白"]}]', 1, 'APPROVED'),
(53, '复古头戴式有线耳机 包耳式', NULL, 1, 1, '数码旗舰店', 199.00, NULL, 500, 876, 4.6, '🎧', 'linear-gradient(135deg,#f5f7fa,#c3cfe2)', 'https://images.unsplash.com/photo-1484704849700-f032a568e944?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1484704849700-f032a568e944?w=800&q=80&auto=format&fit=crop', '自营', '经典复古造型，包耳式设计隔音自然，3.5mm 接口即插即用。', '[]', 1, 'APPROVED'),
(54, '桌面无线蓝牙音箱 原木质感', NULL, 1, 1, '数码旗舰店', 399.00, 349.00, 300, 543, 4.7, '🔊', 'linear-gradient(135deg,#c79081,#dfa579)', 'https://images.unsplash.com/photo-1545454675-3531b543be5d?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1545454675-3531b543be5d?w=800&q=80&auto=format&fit=crop', '自营,满减', '360° 环绕声场，低音浑厚，木质箱体颜值在线，桌面点睛之笔。', '[{"name":"颜色","options":["原木色","黑色"]}]', 1, 'APPROVED'),
(55, '超薄无线键盘 双模连接 静音', NULL, 1, 1, '数码旗舰店', 249.00, 199.00, 400, 678, 4.6, '⌨️', 'linear-gradient(135deg,#fdfbfb,#ebedee)', 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=800&q=80&auto=format&fit=crop,https://images.unsplash.com/photo-1547394765-185e1e68f34e?w=800&q=80&auto=format&fit=crop', '自营,折扣', '超薄剪刀脚结构，静音敲击，蓝牙/2.4G 双模一键切换，办公颜值担当。', '[{"name":"颜色","options":["白色","深空灰"]}]', 1, 'APPROVED'),
(56, '机械键盘 RGB 背光 热插拔轴体', NULL, 1, 1, '数码旗舰店', 449.00, 399.00, 350, 912, 4.8, '⌨️', 'linear-gradient(135deg,#8e2de2,#4a00e0)', 'https://images.unsplash.com/photo-1595225476474-87563907a212?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1595225476474-87563907a212?w=800&q=80&auto=format&fit=crop', '热卖', '热插拔轴体随心换轴，全键无冲，1680 万色 RGB 背光，游戏办公双修。', '[{"name":"轴体","options":["红轴","茶轴"]}]', 1, 'APPROVED'),
(57, '静音无线鼠标 办公人体工学', NULL, 1, 1, '数码旗舰店', 129.00, 99.00, 800, 1876, 4.7, '🖱️', 'linear-gradient(135deg,#bdc3c7,#2c3e50)', 'https://images.unsplash.com/photo-1527864550417-7fd91fc51a46?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1527864550417-7fd91fc51a46?w=800&q=80&auto=format&fit=crop', '折扣,热卖', '人体工学侧翼支撑，静音微动不扰人，一节电池用半年，办公族福音。', '[{"name":"颜色","options":["石墨黑","象牙白"]}]', 1, 'APPROVED'),
(58, '一体机电脑 23.8 英寸 4K 全面屏', NULL, 1, 1, '数码旗舰店', 4999.00, 4699.00, 100, 324, 4.8, '🖥️', 'linear-gradient(135deg,#232526,#414345)', 'https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?w=800&q=80&auto=format&fit=crop', '自营,满减', '4K 全面屏一体成型，隐藏式主机仓，桌面只留一台屏幕的简洁。', '[{"name":"配置","options":["16G+512G","16G+1T"]}]', 1, 'APPROVED'),
(59, '入门级单反相机 18-55mm 套机', NULL, 1, 1, '数码旗舰店', 3899.00, 3599.00, 120, 265, 4.7, '📷', 'linear-gradient(135deg,#141e30,#243b55)', 'https://images.unsplash.com/photo-1502920917128-1aa500764cbd?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1502920917128-1aa500764cbd?w=800&q=80&auto=format&fit=crop', '满减', '约 2410 万有效像素，套机镜头日用够用，光学取景器所见即所得。', '[{"name":"规格","options":["18-55mm 套机"]}]', 1, 'APPROVED'),
(60, '移动电源 20000mAh 22.5W 快充', 2, 1, 1, '数码旗舰店', 149.00, 129.00, 1000, 3210, 4.8, '🔋', 'linear-gradient(135deg,#f7971e,#ffd200)', 'https://images.unsplash.com/photo-1609091839311-d5365f9ff1c5?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1609091839311-d5365f9ff1c5?w=800&q=80&auto=format&fit=crop', '自营,热卖', '20000mAh 大容量，22.5W 超级快充，可带上飞机，出行续航安心。', '[{"name":"容量","options":["20000mAh"]}]', 1, 'APPROVED'),
(61, '创作设计本 13.5 英寸 高刷全面屏', 5, 1, 1, '数码旗舰店', 9999.00, 9499.00, 80, 198, 4.9, '💻', 'linear-gradient(135deg,#3a1c71,#d76d77)', 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=800&q=80&auto=format&fit=crop', '自营', '高刷全面屏 + P3 广色域，性能释放冷静，设计师与剪辑师的生产力工具。', '[{"name":"配置","options":["16G+512G","32G+1T"]}]', 1, 'APPROVED'),
(62, '轻薄笔记本 Air 13 英寸 静音续航', 5, 1, 1, '数码旗舰店', 6999.00, 6799.00, 150, 456, 4.8, '💻', 'linear-gradient(135deg,#8e9eab,#eef2f3)', 'https://images.unsplash.com/photo-1541807084-5c52b6b3adef?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1541807084-5c52b6b3adef?w=800&q=80&auto=format&fit=crop', '自营,热卖', '轻至 1.24kg，无风扇静音设计，全天候续航，轻薄本标杆之作。', '[{"name":"配置","options":["8G+256G","16G+512G"]}]', 1, 'APPROVED'),
(63, '智能手表 方形表盘 简约白', NULL, 1, 1, '数码旗舰店', 899.00, 799.00, 300, 876, 4.6, '⌚', 'linear-gradient(135deg,#e0eaef,#f5f7fa)', 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=800&q=80&auto=format&fit=crop', '折扣', '方形表盘极简设计，心率睡眠监测，消息提醒抬腕可见。', '[{"name":"表壳尺寸","options":["40mm","44mm"]}]', 1, 'APPROVED'),
(64, '商务机械腕表 全自动机芯 皮带款', NULL, 1, 1, '数码旗舰店', 2999.00, 2699.00, 60, 132, 4.9, '⌚', 'linear-gradient(135deg,#232526,#414345)', 'https://images.unsplash.com/photo-1524805444758-089113d48a6d?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1524805444758-089113d48a6d?w=800&q=80&auto=format&fit=crop', '满减', '全自动机械机芯，蓝宝石镜面，牛皮表带随岁月愈显质感。', '[{"name":"表盘","options":["白盘","黑盘"]}]', 1, 'APPROVED'),
-- ===== 服饰鞋包（潮流服饰馆） =====
(65, '加绒连帽卫衣 oversize 宽松款', NULL, 3, 3, '潮流服饰馆', 259.00, 199.00, 600, 1543, 4.7, '🧥', 'linear-gradient(135deg,#bdc3c7,#2c3e50)', 'https://images.unsplash.com/photo-1556821840-3a63f95609a7?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1556821840-3a63f95609a7?w=800&q=80&auto=format&fit=crop', '热卖,折扣', '320g 加绒抓暖，oversize 版型不挑身材，秋冬叠穿百搭。', '[{"name":"颜色","options":["麻灰","黑色"]},{"name":"尺码","options":["M","L","XL"]}]', 1, 'APPROVED'),
(66, '直筒牛仔裤 棉弹丹宁 经典蓝', NULL, 3, 3, '潮流服饰馆', 299.00, 239.00, 500, 987, 4.6, '👖', 'linear-gradient(135deg,#2193b0,#6dd5ed)', 'https://images.unsplash.com/photo-1604176354204-9268737828e4?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1604176354204-9268737828e4?w=800&q=80&auto=format&fit=crop', '折扣', '精选棉弹丹宁，直筒版型藏肉显瘦，经典蓝耐洗不褪色。', '[{"name":"尺码","options":["28","29","30","31","32","33"]}]', 1, 'APPROVED'),
(67, '宽松工装外套 春秋薄款夹克', NULL, 3, 3, '潮流服饰馆', 329.00, 269.00, 400, 876, 4.6, '🧥', 'linear-gradient(135deg,#96e6a1,#d4fc79)', 'https://images.unsplash.com/photo-1591195853828-11db59a44f6b?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1591195853828-11db59a44f6b?w=800&q=80&auto=format&fit=crop', '折扣', '宽松落肩版型，春秋薄款好叠穿，搭牛仔裤与工装裤都利落。', '[{"name":"颜色","options":["燕麦色","军绿"]},{"name":"尺码","options":["S","M","L","XL"]}]', 1, 'APPROVED'),
(68, '简约白色棒球帽 网眼透气', NULL, 3, 3, '潮流服饰馆', 89.00, 69.00, 800, 2134, 4.6, '🧢', 'linear-gradient(135deg,#fdfbfb,#ebedee)', 'https://images.unsplash.com/photo-1588850561407-ed78c282e89b?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1588850561407-ed78c282e89b?w=800&q=80&auto=format&fit=crop', '热卖', '网眼透气后仓，可调节帽围，遮阳防晒一帽搞定。', '[]', 1, 'APPROVED'),
(69, '粗针织围巾披肩 两用加长', NULL, 3, 3, '潮流服饰馆', 129.00, 99.00, 400, 765, 4.7, '🧣', 'linear-gradient(135deg,#96e6a1,#d4fc79)', 'https://images.unsplash.com/photo-1457545195570-67f207084966?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1457545195570-67f207084966?w=800&q=80&auto=format&fit=crop', '折扣', '粗针织纹路柔软亲肤，两用长度披肩围脖随意切换，冬日暖手神器。', '[{"name":"颜色","options":["草绿","烟灰"]}]', 1, 'APPROVED'),
(70, '波西米亚长款连衣裙 荷叶袖', NULL, 3, 3, '潮流服饰馆', 359.00, 289.00, 300, 678, 4.8, '👗', 'linear-gradient(135deg,#f6d365,#fda085)', 'https://images.unsplash.com/photo-1596783074918-c84cb06531ca?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1596783074918-c84cb06531ca?w=800&q=80&auto=format&fit=crop', '热卖', '垂坠雪纺飘逸显瘦，荷叶袖遮拜拜肉，度假通勤两相宜。', '[{"name":"颜色","options":["珊瑚粉"]},{"name":"尺码","options":["S","M","L"]}]', 1, 'APPROVED'),
(71, '综合体能训练鞋 平底稳支撑', 3, 3, 3, '潮流服饰馆', 699.00, 599.00, 400, 1098, 4.8, '👟', 'linear-gradient(135deg,#96e6a1,#d4fc79)', 'https://images.unsplash.com/photo-1606107557195-0e29a4b5b4aa?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1606107557195-0e29a4b5b4aa?w=800&q=80&auto=format&fit=crop', '秒杀,热卖', '平底宽前掌稳如磐石，横向移动支撑到位，撸铁跳操全胜任。', '[{"name":"颜色","options":["荧光绿"]},{"name":"尺码","options":["39","40","41","42","43","44"]}]', 1, 'APPROVED'),
(72, '复古老爹鞋 厚底拼色', NULL, 3, 3, '潮流服饰馆', 459.00, 399.00, 350, 876, 4.6, '👟', 'linear-gradient(135deg,#d4af37,#f9ef8f)', 'https://images.unsplash.com/photo-1560769629-975ec94e6a86?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1560769629-975ec94e6a86?w=800&q=80&auto=format&fit=crop', '折扣', '厚底增高 4cm，复古拼色自带焦点，走路像踩棉花一样软。', '[{"name":"尺码","options":["38","39","40","41","42","43"]}]', 1, 'APPROVED'),
(73, '气垫缓震跑步鞋 白橙配色', 3, 3, 3, '潮流服饰馆', 799.00, 699.00, 300, 654, 4.7, '👟', 'linear-gradient(135deg,#f7971e,#ffd200)', 'https://images.unsplash.com/photo-1600185365483-26d7a4cc7519?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1600185365483-26d7a4cc7519?w=800&q=80&auto=format&fit=crop', '满减', '后掌可见式气垫，落地回弹干脆，白橙配色清爽出街。', '[{"name":"尺码","options":["39","40","41","42","43","44"]}]', 1, 'APPROVED'),
(74, '日系基础款纯棉短袖T恤 宽松版', NULL, 3, 3, '潮流服饰馆', 139.00, 109.00, 900, 2345, 4.7, '👕', 'linear-gradient(135deg,#f5f7fa,#c3cfe2)', 'https://images.unsplash.com/photo-1576566588028-4147f3842f27?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1576566588028-4147f3842f27?w=800&q=80&auto=format&fit=crop', '折扣,热卖', '230g 重磅纯棉，宽松落肩版型男女同款，基础色怎么搭都不会错。', '[{"name":"颜色","options":["米白","浅灰"]},{"name":"尺码","options":["M","L","XL","XXL"]}]', 1, 'APPROVED'),
-- ===== 食品生鲜（鲜丰水果） =====
(75, '当季奶油草莓 250g×2 盒', NULL, 4, 4, '鲜丰水果', 39.90, 32.90, 600, 1876, 4.8, '🍓', 'linear-gradient(135deg,#e53935,#e35d5b)', 'https://images.unsplash.com/photo-1464965911861-746a04b4bca6?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1464965911861-746a04b4bca6?w=800&q=80&auto=format&fit=crop', '秒杀,产地直发', '空心少、香气足，奶香味浓郁，冷链直达开箱即食。', '[{"name":"规格","options":["250g×2盒"]}]', 1, 'APPROVED'),
(76, '高原蜜西瓜 单果 8 斤+', NULL, 4, 4, '鲜丰水果', 59.90, 49.90, 500, 1345, 4.7, '🍉', 'linear-gradient(135deg,#96e6a1,#d4fc79)', 'https://images.unsplash.com/photo-1587049352846-4a222e784d38?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1587049352846-4a222e784d38?w=800&q=80&auto=format&fit=crop', '产地直发', '沙瓤脆甜皮薄如纸，高原昼夜温差养出的自然甜，冰镇更佳。', '[{"name":"规格","options":["单果 8斤+"]}]', 1, 'APPROVED'),
(77, '高山甜香蕉 自然熟 5 斤装', NULL, 4, 4, '鲜丰水果', 29.90, 24.90, 800, 2134, 4.6, '🍌', 'linear-gradient(135deg,#f8b500,#ffe259)', 'https://images.unsplash.com/photo-1571771894821-ce9b6c11b08e?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1571771894821-ce9b6c11b08e?w=800&q=80&auto=format&fit=crop', '产地直发,折扣', '高山梯田自然熟，无催熟剂，软糯香甜，宝宝辅食也放心。', '[{"name":"规格","options":["5斤装"]}]', 1, 'APPROVED'),
(78, '秘鲁牛油果 单果 130g 6 个装', NULL, 4, 4, '鲜丰水果', 49.90, 42.90, 400, 987, 4.7, '🥑', 'linear-gradient(135deg,#96e6a1,#d4fc79)', 'https://images.unsplash.com/photo-1523049673857-eb18f1d7b578?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1523049673857-eb18f1d7b578?w=800&q=80&auto=format&fit=crop', '折扣', '单果 130g 起，捏起来微软即可食用，早餐吐司轻食伴侣。', '[{"name":"规格","options":["6个装"]}]', 1, 'APPROVED'),
(79, '东北长粒香大米 10KG 当季新米', NULL, 4, 4, '鲜丰水果', 89.90, 79.90, 300, 765, 4.8, '🍚', 'linear-gradient(135deg,#ffecd2,#fcb69f)', 'https://images.unsplash.com/photo-1586201375761-83865001e31c?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1586201375761-83865001e31c?w=800&q=80&auto=format&fit=crop', '满减,热卖', '黑土地一年一熟，米粒修长饱满，饭香四溢冷而不回生。', '[{"name":"规格","options":["10KG/袋"]}]', 1, 'APPROVED'),
(80, '特级初榨橄榄油 750ml 冷压榨', NULL, 4, 4, '鲜丰水果', 129.00, 109.00, 350, 654, 4.7, '🫒', 'linear-gradient(135deg,#96e6a1,#d4fc79)', 'https://images.unsplash.com/photo-1474979266404-7eaacbcd87c5?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1474979266404-7eaacbcd87c5?w=800&q=80&auto=format&fit=crop', '折扣', '物理冷压榨，酸度 ≤0.5%，凉拌烹饪皆宜，轻食健身必备。', '[{"name":"规格","options":["750ml/瓶"]}]', 1, 'APPROVED'),
(81, '中度烘焙咖啡豆 1KG 坚果可可调', NULL, 4, 4, '鲜丰水果', 99.00, 89.00, 250, 543, 4.6, '☕', 'linear-gradient(135deg,#5a3f37,#2d132c)', 'https://images.unsplash.com/photo-1447933601403-0c6688de566e?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1447933601403-0c6688de566e?w=800&q=80&auto=format&fit=crop', '折扣', '中度烘焙坚果可可调，油脂丰厚，手冲意式都出色。', '[{"name":"规格","options":["1KG/袋"]}]', 1, 'APPROVED'),
(82, '冷冻大号基围虾 1KG 海捕速冻', NULL, 4, 4, '鲜丰水果', 79.90, 69.90, 400, 876, 4.7, '🦐', 'linear-gradient(135deg,#ff9966,#ff5e62)', 'https://images.unsplash.com/photo-1565680018434-b513d5e5fd47?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1565680018434-b513d5e5fd47?w=800&q=80&auto=format&fit=crop', '满减', '海捕速冻锁鲜，单只 12cm 起，白灼清蒸都鲜甜弹牙。', '[{"name":"规格","options":["1KG/盒"]}]', 1, 'APPROVED'),
(83, '奥尔良风味烤鸡腿半成品 1KG', NULL, 4, 4, '鲜丰水果', 45.90, 39.90, 500, 1098, 4.6, '🍗', 'linear-gradient(135deg,#c31432,#240b36)', 'https://images.unsplash.com/photo-1626074353765-517a681e40be?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1626074353765-517a681e40be?w=800&q=80&auto=format&fit=crop', '折扣,热卖', '秘制奥尔良腌料入味，解冻后烤箱 20 分钟即享，追剧露营必备。', '[{"name":"规格","options":["1KG/袋"]}]', 1, 'APPROVED'),
-- ===== 图书文具（书虫书店） =====
(84, '人文经典精选套装（全 6 册）', NULL, 5, 5, '书虫书店', 299.00, 239.00, 200, 432, 4.8, '📚', 'linear-gradient(135deg,#1e3c72,#2a5298)', 'https://images.unsplash.com/photo-1524995997946-a1c2e315a42f?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1524995997946-a1c2e315a42f?w=800&q=80&auto=format&fit=crop', '满减', '哲学、历史、艺术六部入门经典，布面精装，书架颜值担当。', '[]', 1, 'APPROVED'),
(85, '世界散文随笔经典（全 4 册）', NULL, 5, 5, '书虫书店', 199.00, 159.00, 260, 534, 4.7, '📖', 'linear-gradient(135deg,#fbc2eb,#a6c1ee)', 'https://images.unsplash.com/photo-1457369804613-52c61a468e7d?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1457369804613-52c61a468e7d?w=800&q=80&auto=format&fit=crop', '折扣', '名家译本收录四大随笔圣手，睡前读两页，治愈一整天。', '[]', 1, 'APPROVED'),
(86, '牛皮纸手账本 A5 方格计划本', NULL, 5, 5, '书虫书店', 49.90, 39.90, 600, 1345, 4.6, '📒', 'linear-gradient(135deg,#c79081,#dfa579)', 'https://images.unsplash.com/photo-1517971129774-8a2b38fa128e?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1517971129774-8a2b38fa128e?w=800&q=80&auto=format&fit=crop', '折扣,热卖', 'A5 方格内页书写不洇墨，附月度计划页与夹层口袋，学习工作两相宜。', '[{"name":"颜色","options":["复古棕","墨绿"]}]', 1, 'APPROVED'),
(87, '多功能文具收纳套装 12 件套', NULL, 5, 5, '书虫书店', 79.00, 59.00, 500, 987, 4.7, '✏️', 'linear-gradient(135deg,#89f7fe,#66a6ff)', 'https://images.unsplash.com/photo-1456735190827-d1262f71b8a3?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1456735190827-d1262f71b8a3?w=800&q=80&auto=format&fit=crop', '折扣', '笔袋文件分格收纳，开箱即用，学生党办公族桌面整理首选。', '[{"name":"规格","options":["12 件套"]}]', 1, 'APPROVED'),
(88, '儿童开学文具礼包 文具套装', NULL, 5, 5, '书虫书店', 69.00, 49.90, 400, 765, 4.8, '🎒', 'linear-gradient(135deg,#fbc2eb,#a6c1ee)', 'https://images.unsplash.com/photo-1503676260728-1c00da094a0b?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1503676260728-1c00da094a0b?w=800&q=80&auto=format&fit=crop', '热卖', '笔、本、橡皮一次配齐，圆角护眼设计，开学季礼物首选。', '[{"name":"规格","options":["开学礼包"]}]', 1, 'APPROVED'),
-- ===== 美妆个护（美妍美妆） =====
(89, '哑光眼影盘 35 色 大地色系', NULL, 6, 6, '美妍美妆', 169.00, 139.00, 400, 987, 4.7, '🎨', 'linear-gradient(135deg,#a18cd1,#fbc2eb)', 'https://images.unsplash.com/photo-1573461160327-b450ce3d8e7f?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1573461160327-b450ce3d8e7f?w=800&q=80&auto=format&fit=crop,https://images.unsplash.com/photo-1512496015851-a90fb38ba796?w=800&q=80&auto=format&fit=crop', '折扣,热卖', '大地色系打底 + 摩卡红棕点缀，粉质细腻不飞粉，新手也化不出错。', '[{"name":"色系","options":["35 色大地盘"]}]', 1, 'APPROVED'),
(90, '玫瑰金化妆刷套装 8 件套', NULL, 6, 6, '美妍美妆', 129.00, 99.00, 350, 765, 4.8, '💄', 'linear-gradient(135deg,#f6d365,#fda085)', 'https://images.unsplash.com/photo-1522335789203-aabd1fc54bc9?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1522335789203-aabd1fc54bc9?w=800&q=80&auto=format&fit=crop', '折扣', '纤维刷毛抓粉均匀，玫瑰金漆柄手感温润，散粉眼影刷一应俱全。', '[{"name":"规格","options":["8 件套"]}]', 1, 'APPROVED'),
(91, '防晒棒 SPF50+ 清爽不成膜', NULL, 6, 6, '美妍美妆', 89.00, 69.00, 600, 1543, 4.6, '🧴', 'linear-gradient(135deg,#e0eaef,#f5f7fa)', 'https://images.unsplash.com/photo-1556228578-8c89e6adf883?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1556228578-8c89e6adf883?w=800&q=80&auto=format&fit=crop', '折扣,热卖', '旋转出膏随时补涂，清爽不成膜不假白，通勤户外都能扛。', '[{"name":"规格","options":["21g/支"]}]', 1, 'APPROVED'),
(92, '蔚蓝男士浓香水 100ml 木质芳香调', NULL, 6, 6, '美妍美妆', 1299.00, 1199.00, 100, 213, 4.9, '🌸', 'linear-gradient(135deg,#232526,#414345)', 'https://images.unsplash.com/photo-1523293182086-7651a899d37f?w=800&q=80&auto=format&fit=crop', 'https://images.unsplash.com/photo-1523293182086-7651a899d37f?w=800&q=80&auto=format&fit=crop', '满减', '木质芳香调，开场柑橘凌厉，尾调檀香沉稳，商务场合气场全开。', '[]', 1, 'APPROVED');

-- ===== 三、新增 SKU（延续既有编号） =====
INSERT IGNORE INTO product_sku (id, product_id, specs, price, stock, sales, status) VALUES
(25, 52, '{"颜色":"曜石黑"}', 599.00, 300, 600, 1),
(26, 52, '{"颜色":"珍珠白"}', 599.00, 100, 300, 1),
(27, 65, '{"颜色":"麻灰","尺码":"M"}', 199.00, 250, 600, 1),
(28, 65, '{"颜色":"麻灰","尺码":"L"}', 199.00, 250, 700, 1),
(29, 71, '{"颜色":"荧光绿","尺码":"41"}', 599.00, 120, 400, 1),
(30, 71, '{"颜色":"荧光绿","尺码":"42"}', 599.00, 150, 450, 1);

-- ===== 四、新增商品类目关联 =====
INSERT IGNORE INTO product_category_rel (product_id, category_id) VALUES
(52, 1), (52, 12),
(53, 1), (53, 12),
(54, 1), (54, 12),
(55, 1),
(56, 1),
(57, 1),
(58, 1), (58, 51),
(59, 1),
(60, 1),
(61, 1), (61, 51),
(62, 1), (62, 51),
(63, 1), (63, 13),
(64, 1), (64, 13),
(65, 3), (65, 31),
(66, 3), (66, 31),
(67, 3), (67, 31),
(68, 3),
(69, 3),
(70, 3),
(71, 3), (71, 32),
(72, 3), (72, 32),
(73, 3), (73, 32),
(74, 3), (74, 31),
(75, 4), (75, 41),
(76, 4), (76, 41),
(77, 4), (77, 41),
(78, 4), (78, 41),
(79, 4),
(80, 4),
(81, 4),
(82, 4), (82, 42),
(83, 4), (83, 42),
(84, 5),
(85, 5),
(86, 5), (86, 52),
(87, 5), (87, 52),
(88, 5), (88, 52),
(89, 6),
(90, 6),
(91, 6), (91, 61),
(92, 6);
-- ===== 2026-11-04 第三批扩展：新增商品 93-141（49 款）+ SKU 31-37 =====
-- 图片来源：cdn.dummyjson.com 官方商品图（thumbnail + 图集，URL 与商品一一对应）

INSERT IGNORE INTO product (id, name, brand_id, category_id, shop_id, shop_name, price, discount_price,
  stock, sales, rating, emoji, color, cover_image, images, tags, description, specs, status, audit_status) VALUES
(93, '家用微波炉 23L 一键速热', NULL, 2, 2, '家电优选', 499.00, 429.00, 120, 980, 4.8, '🍱', 'linear-gradient(135deg,#667eea,#764ba2)', 'https://cdn.dummyjson.com/product-images/kitchen-accessories/microwave-oven/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/kitchen-accessories/microwave-oven/1.webp,https://cdn.dummyjson.com/product-images/kitchen-accessories/microwave-oven/2.webp,https://cdn.dummyjson.com/product-images/kitchen-accessories/microwave-oven/3.webp,https://cdn.dummyjson.com/product-images/kitchen-accessories/microwave-oven/4.webp', '热卖,折扣', '23L 家用容量，加热解冻一键操作，受热均匀更省心。', '[{"name":"容量","options":["23L"]},{"name":"颜色","options":["黑色","白色"]}]', 1, 'APPROVED'),
(94, '台式破壁料理机 1.5L 果汁五谷', NULL, 2, 2, '家电优选', 399.00, 349.00, 80, 560, 4.7, '🥤', 'linear-gradient(135deg,#f093fb,#f5576c)', 'https://cdn.dummyjson.com/product-images/kitchen-accessories/boxed-blender/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/kitchen-accessories/boxed-blender/1.webp,https://cdn.dummyjson.com/product-images/kitchen-accessories/boxed-blender/2.webp,https://cdn.dummyjson.com/product-images/kitchen-accessories/boxed-blender/3.webp,https://cdn.dummyjson.com/product-images/kitchen-accessories/boxed-blender/4.webp', '折扣', '大容量杯体，果蔬五谷一键搅打，口感细腻顺滑易清洗。', '[{"name":"容量","options":["1.5L"]}]', 1, 'APPROVED'),
(95, '手持料理棒 婴儿辅食机 300W', NULL, 2, 2, '家电优选', 189.00, 159.00, 150, 430, 4.6, '🥣', 'linear-gradient(135deg,#4facfe,#00f2fe)', 'https://cdn.dummyjson.com/product-images/kitchen-accessories/hand-blender/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/kitchen-accessories/hand-blender/1.webp', '折扣', '手持式料理棒，辅食、果汁、奶昔都能打，即打即用易清洗。', '[{"name":"功率","options":["300W"]}]', 1, 'APPROVED'),
(96, '台式电陶炉 无明火加热 旋钮控温', NULL, 2, 2, '家电优选', 259.00, 219.00, 90, 320, 4.6, '🍳', 'linear-gradient(135deg,#43e97b,#38f9d7)', 'https://cdn.dummyjson.com/product-images/kitchen-accessories/electric-stove/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/kitchen-accessories/electric-stove/1.webp,https://cdn.dummyjson.com/product-images/kitchen-accessories/electric-stove/2.webp,https://cdn.dummyjson.com/product-images/kitchen-accessories/electric-stove/3.webp,https://cdn.dummyjson.com/product-images/kitchen-accessories/electric-stove/4.webp', '折扣', '台式电陶炉，无明火加热更安心，火力旋钮调节，煮茶炖汤都合适。', '[{"name":"功率","options":["2000W"]}]', 1, 'APPROVED'),
(97, '简约百搭台灯 卧室床头书房', NULL, 2, 2, '家电优选', 139.00, 109.00, 200, 410, 4.7, '💡', 'linear-gradient(135deg,#fa709a,#fee140)', 'https://cdn.dummyjson.com/product-images/home-decoration/table-lamp/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/home-decoration/table-lamp/1.webp', '折扣', '简约造型台灯，卧室床头、书房桌面的照明好物，百搭不挑装修。', '[{"name":"颜色","options":["原木色"]}]', 1, 'APPROVED'),
(98, '陕西绿心猕猴桃 6 个装', NULL, 4, 4, '鲜丰水果', 19.90, 15.90, 300, 1520, 4.8, '🥝', 'linear-gradient(135deg,#30cfd0,#330867)', 'https://cdn.dummyjson.com/product-images/groceries/kiwi/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/groceries/kiwi/1.webp', '秒杀,产地直发', '酸甜多汁，维C丰富，皮薄肉厚，冷藏后风味更佳。', '[{"name":"规格","options":["6个装"]}]', 1, 'APPROVED'),
(99, '安岳黄柠檬 4 个装', NULL, 4, 4, '鲜丰水果', 12.90, 9.90, 400, 2210, 4.8, '🍋', 'linear-gradient(135deg,#a8edea,#fed6e3)', 'https://cdn.dummyjson.com/product-images/groceries/lemon/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/groceries/lemon/1.webp', '秒杀,产地直发', '果香清新，皮薄多汁，泡水榨汁皆宜。', '[{"name":"规格","options":["4个装"]}]', 1, 'APPROVED'),
(100, '新鲜桑葚 250g 冷链直达', NULL, 4, 4, '鲜丰水果', 25.90, 19.90, 200, 680, 4.7, '🫐', 'linear-gradient(135deg,#ff9a9e,#fecfef)', 'https://cdn.dummyjson.com/product-images/groceries/mulberry/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/groceries/mulberry/1.webp', '秒杀,产地直发', '果肉饱满多汁，酸甜适口，冷链保鲜开箱即食。', '[{"name":"规格","options":["250g"]}]', 1, 'APPROVED'),
(101, '冷鲜鸡胸肉 500g 低脂高蛋白', NULL, 4, 4, '鲜丰水果', 15.90, 12.90, 250, 1830, 4.7, '🍗', 'linear-gradient(135deg,#fbc2eb,#a6c1ee)', 'https://cdn.dummyjson.com/product-images/groceries/chicken-meat/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/groceries/chicken-meat/1.webp,https://cdn.dummyjson.com/product-images/groceries/chicken-meat/2.webp', '秒杀,折扣', '冷鲜鸡胸肉，低脂高蛋白，煎炒炖煮都合适。', '[{"name":"规格","options":["500g"]}]', 1, 'APPROVED'),
(102, '冷冻鱼排 400g 香煎油炸', NULL, 4, 4, '鲜丰水果', 59.90, 49.90, 150, 760, 4.6, '🐟', 'linear-gradient(135deg,#fdcb6e,#e17055)', 'https://cdn.dummyjson.com/product-images/groceries/fish-steak/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/groceries/fish-steak/1.webp', '折扣', '肉紧味鲜，香煎油炸都合适，冷冻锁鲜直达。', '[{"name":"规格","options":["400g"]}]', 1, 'APPROVED'),
(103, '水果黄瓜 500g 即食脆嫩', NULL, 4, 4, '鲜丰水果', 6.90, NULL, 400, 3120, 4.8, '🥒', 'linear-gradient(135deg,#84fab0,#8fd3f4)', 'https://cdn.dummyjson.com/product-images/groceries/cucumber/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/groceries/cucumber/1.webp', '热卖,产地直发', '脆嫩清爽，水分足，即食凉拌都合适。', '[{"name":"规格","options":["500g"]}]', 1, 'APPROVED'),
(104, '灯笼青椒 3 个装', NULL, 4, 4, '鲜丰水果', 5.90, NULL, 350, 940, 4.6, '🫑', 'linear-gradient(135deg,#d299c2,#fef9d7)', 'https://cdn.dummyjson.com/product-images/groceries/green-bell-pepper/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/groceries/green-bell-pepper/1.webp', '产地直发', '皮薄肉厚微甜，家常炒菜百搭。', '[{"name":"规格","options":["3个装"]}]', 1, 'APPROVED'),
(105, '新鲜青辣椒 100g 提味增香', NULL, 4, 4, '鲜丰水果', 4.90, NULL, 300, 870, 4.6, '🌶️', 'linear-gradient(135deg,#667eea,#764ba2)', 'https://cdn.dummyjson.com/product-images/groceries/green-chili-pepper/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/groceries/green-chili-pepper/1.webp', '产地直发', '辣度十足，提味增香，炒菜拌面少许即够。', '[{"name":"规格","options":["100g"]}]', 1, 'APPROVED'),
(106, '黄心土豆 2KG 炖煮粉糯', NULL, 4, 4, '鲜丰水果', 9.90, NULL, 500, 2560, 4.8, '🥔', 'linear-gradient(135deg,#f093fb,#f5576c)', 'https://cdn.dummyjson.com/product-images/groceries/potatoes/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/groceries/potatoes/1.webp', '热卖,产地直发', '粉糯香甜，炖煮炒菜样样合适。', '[{"name":"规格","options":["2KG"]}]', 1, 'APPROVED'),
(107, '紫皮洋葱 1KG 辛香浓郁', NULL, 4, 4, '鲜丰水果', 7.90, NULL, 400, 1290, 4.7, '🧅', 'linear-gradient(135deg,#4facfe,#00f2fe)', 'https://cdn.dummyjson.com/product-images/groceries/red-onions/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/groceries/red-onions/1.webp', '产地直发', '紫皮洋葱，辛香浓郁，爆锅提味必备。', '[{"name":"规格","options":["1KG"]}]', 1, 'APPROVED'),
(108, '秦岭土蜂蜜 500g 玻璃罐装', NULL, 4, 4, '鲜丰水果', 49.90, 39.90, 200, 1450, 4.8, '🍯', 'linear-gradient(135deg,#43e97b,#38f9d7)', 'https://cdn.dummyjson.com/product-images/groceries/honey-jar/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/groceries/honey-jar/1.webp', '热卖,折扣', '玻璃罐装，冲水调饮清润甘甜，早晚一杯刚刚好。', '[{"name":"规格","options":["500g"]}]', 1, 'APPROVED'),
(109, '家庭装冰淇淋 1L 绵密细腻', NULL, 4, 4, '鲜丰水果', 29.90, 24.90, 180, 990, 4.7, '🍨', 'linear-gradient(135deg,#fa709a,#fee140)', 'https://cdn.dummyjson.com/product-images/groceries/ice-cream/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/groceries/ice-cream/1.webp,https://cdn.dummyjson.com/product-images/groceries/ice-cream/2.webp,https://cdn.dummyjson.com/product-images/groceries/ice-cream/3.webp,https://cdn.dummyjson.com/product-images/groceries/ice-cream/4.webp', '折扣', '绵密细腻，一勺一个满足，夏日必备解暑。', '[{"name":"规格","options":["1L"]}]', 1, 'APPROVED'),
(110, '鲜榨风味橙汁 1L 果香浓郁', NULL, 4, 4, '鲜丰水果', 19.90, 15.90, 220, 1120, 4.6, '🧃', 'linear-gradient(135deg,#30cfd0,#330867)', 'https://cdn.dummyjson.com/product-images/groceries/juice/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/groceries/juice/1.webp', '折扣', '鲜榨风味橙汁，果香浓郁，冰镇更好喝。', '[{"name":"规格","options":["1L"]}]', 1, 'APPROVED'),
(111, '乳清蛋白粉 1KG 巧克力味', NULL, 4, 4, '鲜丰水果', 169.00, 139.00, 100, 540, 4.7, '🥛', 'linear-gradient(135deg,#a8edea,#fed6e3)', 'https://cdn.dummyjson.com/product-images/groceries/protein-powder/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/groceries/protein-powder/1.webp', '折扣,热卖', '运动健身补给，冲调便捷，训练后及时补充。', '[{"name":"规格","options":["1KG"]}]', 1, 'APPROVED'),
(112, '原味柠檬汽水 330ml×6 罐', NULL, 4, 4, '鲜丰水果', 12.90, NULL, 300, 1680, 4.6, '🥤', 'linear-gradient(135deg,#ff9a9e,#fecfef)', 'https://cdn.dummyjson.com/product-images/groceries/soft-drinks/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/groceries/soft-drinks/1.webp', '热卖', '清爽气泡，冰镇后畅爽解腻，家庭聚餐常备。', '[{"name":"规格","options":["330ml×6罐"]}]', 1, 'APPROVED'),
(113, '天然饮用矿泉水 550ml×24 瓶', NULL, 4, 4, '鲜丰水果', 39.90, 32.90, 500, 4200, 4.9, '💧', 'linear-gradient(135deg,#fbc2eb,#a6c1ee)', 'https://cdn.dummyjson.com/product-images/groceries/water/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/groceries/water/1.webp', '热卖', '天然水源，清冽甘甜，整箱囤货更划算。', '[{"name":"规格","options":["550ml×24瓶"]}]', 1, 'APPROVED'),
(114, '家用抽纸 6 包装 柔韧亲肤', NULL, 4, 4, '鲜丰水果', 19.90, 15.90, 400, 2740, 4.8, '🧻', 'linear-gradient(135deg,#fdcb6e,#e17055)', 'https://cdn.dummyjson.com/product-images/groceries/tissue-paper-box/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/groceries/tissue-paper-box/1.webp,https://cdn.dummyjson.com/product-images/groceries/tissue-paper-box/2.webp', '折扣,热卖', '居家常备，纸张柔韧，餐巾擦手都合适。', '[{"name":"规格","options":["6包装"]}]', 1, 'APPROVED'),
(115, '蓝黑格纹长袖衬衫 通勤休闲', NULL, 3, 3, '潮流服饰馆', 159.00, 129.00, 160, 720, 4.7, '👔', 'linear-gradient(135deg,#84fab0,#8fd3f4)', 'https://cdn.dummyjson.com/product-images/mens-shirts/blue-&-black-check-shirt/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/mens-shirts/blue-&-black-check-shirt/1.webp,https://cdn.dummyjson.com/product-images/mens-shirts/blue-&-black-check-shirt/2.webp,https://cdn.dummyjson.com/product-images/mens-shirts/blue-&-black-check-shirt/3.webp,https://cdn.dummyjson.com/product-images/mens-shirts/blue-&-black-check-shirt/4.webp', '折扣', '蓝黑格纹长袖衬衫，版型利落，通勤休闲都能穿。', '[{"name":"尺码","options":["M","L"]}]', 1, 'APPROVED'),
(116, '纯棉短袖衬衫 夏季薄款', NULL, 3, 3, '潮流服饰馆', 129.00, 99.00, 180, 650, 4.6, '👕', 'linear-gradient(135deg,#d299c2,#fef9d7)', 'https://cdn.dummyjson.com/product-images/mens-shirts/man-short-sleeve-shirt/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/mens-shirts/man-short-sleeve-shirt/1.webp,https://cdn.dummyjson.com/product-images/mens-shirts/man-short-sleeve-shirt/2.webp,https://cdn.dummyjson.com/product-images/mens-shirts/man-short-sleeve-shirt/3.webp,https://cdn.dummyjson.com/product-images/mens-shirts/man-short-sleeve-shirt/4.webp', '折扣', '基础款短袖衬衫，夏日清爽百搭，单穿打底都合适。', '[{"name":"尺码","options":["M","L"]}]', 1, 'APPROVED'),
(117, '经典红黑配色篮球鞋 复古街头', 3, 3, 3, '潮流服饰馆', 899.00, 799.00, 90, 480, 4.8, '👟', 'linear-gradient(135deg,#667eea,#764ba2)', 'https://cdn.dummyjson.com/product-images/mens-shoes/nike-air-jordan-1-red-and-black/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/mens-shoes/nike-air-jordan-1-red-and-black/1.webp,https://cdn.dummyjson.com/product-images/mens-shoes/nike-air-jordan-1-red-and-black/2.webp,https://cdn.dummyjson.com/product-images/mens-shoes/nike-air-jordan-1-red-and-black/3.webp,https://cdn.dummyjson.com/product-images/mens-shoes/nike-air-jordan-1-red-and-black/4.webp', '热卖,折扣', '经典红黑配色篮球鞋，复古街头风，场内场外都能穿。', '[{"name":"尺码","options":["40","42","44"]}]', 1, 'APPROVED'),
(118, '复古慢跑鞋 轻便百搭', NULL, 3, 3, '潮流服饰馆', 499.00, 429.00, 120, 560, 4.7, '👟', 'linear-gradient(135deg,#f093fb,#f5576c)', 'https://cdn.dummyjson.com/product-images/mens-shoes/puma-future-rider-trainers/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/mens-shoes/puma-future-rider-trainers/1.webp,https://cdn.dummyjson.com/product-images/mens-shoes/puma-future-rider-trainers/2.webp,https://cdn.dummyjson.com/product-images/mens-shoes/puma-future-rider-trainers/3.webp,https://cdn.dummyjson.com/product-images/mens-shoes/puma-future-rider-trainers/4.webp', '折扣', '复古跑鞋造型，轻便百搭，日常通勤散步都舒适。', '[{"name":"尺码","options":["40","42","44"]}]', 1, 'APPROVED'),
(119, '白红拼色休闲板鞋 街头风', NULL, 3, 3, '潮流服饰馆', 599.00, 499.00, 100, 390, 4.6, '👟', 'linear-gradient(135deg,#4facfe,#00f2fe)', 'https://cdn.dummyjson.com/product-images/mens-shoes/sports-sneakers-off-white-&-red/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/mens-shoes/sports-sneakers-off-white-&-red/1.webp,https://cdn.dummyjson.com/product-images/mens-shoes/sports-sneakers-off-white-&-red/2.webp,https://cdn.dummyjson.com/product-images/mens-shoes/sports-sneakers-off-white-&-red/3.webp,https://cdn.dummyjson.com/product-images/mens-shoes/sports-sneakers-off-white-&-red/4.webp', '折扣', '白红拼色休闲板鞋，街头感十足，牛仔卫衣都好搭。', '[{"name":"尺码","options":["40","42","44"]}]', 1, 'APPROVED'),
(120, '蓝色法式连衣裙 收腰显瘦', NULL, 3, 3, '潮流服饰馆', 259.00, 199.00, 140, 610, 4.7, '👗', 'linear-gradient(135deg,#43e97b,#38f9d7)', 'https://cdn.dummyjson.com/product-images/tops/blue-frock/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/tops/blue-frock/1.webp,https://cdn.dummyjson.com/product-images/tops/blue-frock/2.webp,https://cdn.dummyjson.com/product-images/tops/blue-frock/3.webp,https://cdn.dummyjson.com/product-images/tops/blue-frock/4.webp', '折扣,热卖', '蓝色连衣裙，收腰显瘦，度假约会都出彩。', '[{"name":"尺码","options":["S","M"]}]', 1, 'APPROVED'),
(121, '女童夏季连衣裙 清凉透气', NULL, 3, 3, '潮流服饰馆', 99.00, 79.00, 160, 430, 4.7, '👗', 'linear-gradient(135deg,#fa709a,#fee140)', 'https://cdn.dummyjson.com/product-images/tops/girl-summer-dress/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/tops/girl-summer-dress/1.webp,https://cdn.dummyjson.com/product-images/tops/girl-summer-dress/2.webp,https://cdn.dummyjson.com/product-images/tops/girl-summer-dress/3.webp,https://cdn.dummyjson.com/product-images/tops/girl-summer-dress/4.webp', '折扣', '女童夏季连衣裙，清凉透气，活泼可爱好穿搭。', '[{"name":"尺码","options":["110cm","130cm"]}]', 1, 'APPROVED'),
(122, '灰色针织连衣裙 过膝长款', NULL, 3, 3, '潮流服饰馆', 299.00, 239.00, 90, 260, 4.6, '👗', 'linear-gradient(135deg,#30cfd0,#330867)', 'https://cdn.dummyjson.com/product-images/tops/gray-dress/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/tops/gray-dress/1.webp,https://cdn.dummyjson.com/product-images/tops/gray-dress/2.webp,https://cdn.dummyjson.com/product-images/tops/gray-dress/3.webp,https://cdn.dummyjson.com/product-images/tops/gray-dress/4.webp', '折扣', '灰色针织连衣裙，过膝长款，温柔知性显气质。', '[{"name":"尺码","options":["S","M"]}]', 1, 'APPROVED'),
(123, '黑色优雅长款晚礼服', NULL, 3, 3, '潮流服饰馆', 899.00, 799.00, 50, 150, 4.8, '👗', 'linear-gradient(135deg,#a8edea,#fed6e3)', 'https://cdn.dummyjson.com/product-images/womens-dresses/black-women''s-gown/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/womens-dresses/black-women''s-gown/1.webp,https://cdn.dummyjson.com/product-images/womens-dresses/black-women''s-gown/2.webp,https://cdn.dummyjson.com/product-images/womens-dresses/black-women''s-gown/3.webp,https://cdn.dummyjson.com/product-images/womens-dresses/black-women''s-gown/4.webp', '折扣', '黑色优雅长款晚礼服，宴会年会的气质单品。', '[{"name":"尺码","options":["S","M"]}]', 1, 'APPROVED'),
(124, '蓝色女士手提包 大容量', NULL, 3, 3, '潮流服饰馆', 299.00, 239.00, 110, 340, 4.7, '👜', 'linear-gradient(135deg,#ff9a9e,#fecfef)', 'https://cdn.dummyjson.com/product-images/womens-bags/blue-women''s-handbag/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/womens-bags/blue-women''s-handbag/1.webp,https://cdn.dummyjson.com/product-images/womens-bags/blue-women''s-handbag/2.webp,https://cdn.dummyjson.com/product-images/womens-bags/blue-women''s-handbag/3.webp', '折扣', '蓝色女士手提包，容量能装，通勤出街都合适。', '[{"name":"颜色","options":["蓝色"]}]', 1, 'APPROVED'),
(125, '皮质女士单肩包 通勤气质', NULL, 3, 3, '潮流服饰馆', 699.00, 599.00, 70, 210, 4.7, '👜', 'linear-gradient(135deg,#fbc2eb,#a6c1ee)', 'https://cdn.dummyjson.com/product-images/womens-bags/heshe-women''s-leather-bag/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/womens-bags/heshe-women''s-leather-bag/1.webp,https://cdn.dummyjson.com/product-images/womens-bags/heshe-women''s-leather-bag/2.webp,https://cdn.dummyjson.com/product-images/womens-bags/heshe-women''s-leather-bag/3.webp', '折扣', '皮质女士单肩包，纹理质感，通勤约会气质满分。', '[{"name":"颜色","options":["棕色"]}]', 1, 'APPROVED'),
(126, '黑色太阳镜 简约遮阳', NULL, 3, 3, '潮流服饰馆', 199.00, 159.00, 200, 780, 4.6, '🕶️', 'linear-gradient(135deg,#fdcb6e,#e17055)', 'https://cdn.dummyjson.com/product-images/sunglasses/black-sun-glasses/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/sunglasses/black-sun-glasses/1.webp,https://cdn.dummyjson.com/product-images/sunglasses/black-sun-glasses/2.webp,https://cdn.dummyjson.com/product-images/sunglasses/black-sun-glasses/3.webp', '折扣', '黑色太阳镜，简约百搭，出行遮阳凹造型必备。', '[{"name":"颜色","options":["黑色"]}]', 1, 'APPROVED'),
(127, '白色双肩包 轻盈能装', NULL, 3, 3, '潮流服饰馆', 259.00, 209.00, 120, 460, 4.6, '🎒', 'linear-gradient(135deg,#84fab0,#8fd3f4)', 'https://cdn.dummyjson.com/product-images/womens-bags/white-faux-leather-backpack/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/womens-bags/white-faux-leather-backpack/1.webp,https://cdn.dummyjson.com/product-images/womens-bags/white-faux-leather-backpack/2.webp,https://cdn.dummyjson.com/product-images/womens-bags/white-faux-leather-backpack/3.webp', '折扣', '白色双肩包，轻盈能装，通勤旅行都合适。', '[{"name":"颜色","options":["白色"]}]', 1, 'APPROVED'),
(128, '苹果 AirPods 无线蓝牙耳机', 5, 1, 1, '数码旗舰店', 999.00, 899.00, 150, 1890, 4.8, '🎧', 'linear-gradient(135deg,#667eea,#764ba2)', 'https://cdn.dummyjson.com/product-images/mobile-accessories/apple-airpods/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/mobile-accessories/apple-airpods/1.webp,https://cdn.dummyjson.com/product-images/mobile-accessories/apple-airpods/2.webp,https://cdn.dummyjson.com/product-images/mobile-accessories/apple-airpods/3.webp', '热卖,折扣', '苹果无线蓝牙耳机，开盖即连，通话音乐清晰稳定。', '[{"name":"颜色","options":["白色"]}]', 1, 'APPROVED'),
(129, '苹果 AirPods Max 头戴式降噪耳机', 5, 1, 1, '数码旗舰店', 3999.00, 3699.00, 60, 420, 4.8, '🎧', 'linear-gradient(135deg,#f093fb,#f5576c)', 'https://cdn.dummyjson.com/product-images/mobile-accessories/apple-airpods-max-silver/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/mobile-accessories/apple-airpods-max-silver/1.webp', '折扣', '苹果头戴式降噪耳机，包裹感佩戴，通勤差旅安静专注。', '[{"name":"颜色","options":["银色"]}]', 1, 'APPROVED'),
(130, '苹果 HomePod mini 智能音箱', 5, 1, 1, '数码旗舰店', 749.00, 699.00, 90, 530, 4.7, '🔊', 'linear-gradient(135deg,#4facfe,#00f2fe)', 'https://cdn.dummyjson.com/product-images/mobile-accessories/apple-homepod-mini-cosmic-grey/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/mobile-accessories/apple-homepod-mini-cosmic-grey/1.webp', '折扣', '苹果智能音箱，小巧机身，语音助手、音乐播报都能搞定。', '[{"name":"颜色","options":["深空灰"]}]', 1, 'APPROVED'),
(131, '颈挂式无线蓝牙耳机 运动续航', NULL, 1, 1, '数码旗舰店', 399.00, 349.00, 130, 690, 4.6, '🎧', 'linear-gradient(135deg,#43e97b,#38f9d7)', 'https://cdn.dummyjson.com/product-images/mobile-accessories/beats-flex-wireless-earphones/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/mobile-accessories/beats-flex-wireless-earphones/1.webp', '折扣', '颈挂式无线蓝牙耳机，运动稳固佩戴，续航持久。', '[{"name":"颜色","options":["黑色"]}]', 1, 'APPROVED'),
(132, '苹果 Watch 智能手表 金色铝金属', 5, 1, 1, '数码旗舰店', 2399.00, 2199.00, 70, 380, 4.8, '⌚', 'linear-gradient(135deg,#fa709a,#fee140)', 'https://cdn.dummyjson.com/product-images/mobile-accessories/apple-watch-series-4-gold/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/mobile-accessories/apple-watch-series-4-gold/1.webp,https://cdn.dummyjson.com/product-images/mobile-accessories/apple-watch-series-4-gold/2.webp,https://cdn.dummyjson.com/product-images/mobile-accessories/apple-watch-series-4-gold/3.webp', '热卖,折扣', '苹果智能手表，金色表身，消息提醒、运动记录一表搞定。', '[{"name":"尺寸","options":["44mm"]}]', 1, 'APPROVED'),
(133, '苹果 MagSafe 磁吸无线充电宝', 5, 1, 1, '数码旗舰店', 749.00, 699.00, 100, 350, 4.7, '🔋', 'linear-gradient(135deg,#30cfd0,#330867)', 'https://cdn.dummyjson.com/product-images/mobile-accessories/apple-magsafe-battery-pack/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/mobile-accessories/apple-magsafe-battery-pack/1.webp,https://cdn.dummyjson.com/product-images/mobile-accessories/apple-magsafe-battery-pack/2.webp', '折扣', '磁吸无线充电宝，吸附即充，出门应急电量补给。', '[{"name":"容量","options":["1460mAh"]}]', 1, 'APPROVED'),
(134, '纤长卷翘睫毛膏 防水不晕染', NULL, 6, 6, '美妍美妆', 79.00, 59.00, 250, 1350, 4.7, '👁️', 'linear-gradient(135deg,#a8edea,#fed6e3)', 'https://cdn.dummyjson.com/product-images/beauty/essence-mascara-lash-princess/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/beauty/essence-mascara-lash-princess/1.webp', '折扣,热卖', '纤长卷翘配方，刷头细致，根根分明不结块。', '[{"name":"色号","options":["黑色"]}]', 1, 'APPROVED'),
(135, '珠光眼影盘 带镜子 日常通勤', NULL, 6, 6, '美妍美妆', 99.00, 79.00, 180, 870, 4.7, '🎨', 'linear-gradient(135deg,#ff9a9e,#fecfef)', 'https://cdn.dummyjson.com/product-images/beauty/eyeshadow-palette-with-mirror/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/beauty/eyeshadow-palette-with-mirror/1.webp', '折扣', '珠光眼影盘，自带镜子，日常通勤妆一盒搞定。', '[{"name":"色系","options":["珠光盘"]}]', 1, 'APPROVED'),
(136, '轻盈定妆散粉 控油持妆', NULL, 6, 6, '美妍美妆', 89.00, 69.00, 200, 640, 4.6, '✨', 'linear-gradient(135deg,#fbc2eb,#a6c1ee)', 'https://cdn.dummyjson.com/product-images/beauty/powder-canister/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/beauty/powder-canister/1.webp', '折扣', '轻盈定妆散粉，控油持妆，妆面清爽不卡粉。', '[{"name":"色号","options":["自然色"]}]', 1, 'APPROVED'),
(137, '丝绒哑光口红 正红色', NULL, 6, 6, '美妍美妆', 89.00, 69.00, 220, 1120, 4.8, '💄', 'linear-gradient(135deg,#fdcb6e,#e17055)', 'https://cdn.dummyjson.com/product-images/beauty/red-lipstick/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/beauty/red-lipstick/1.webp', '折扣,热卖', '丝绒哑光质地，正红色显白提气色，一支气场全开。', '[{"name":"色号","options":["正红色"]}]', 1, 'APPROVED'),
(138, '亮泽指甲油 免烤快干', NULL, 6, 6, '美妍美妆', 29.90, 19.90, 300, 1560, 4.6, '💅', 'linear-gradient(135deg,#84fab0,#8fd3f4)', 'https://cdn.dummyjson.com/product-images/beauty/red-nail-polish/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/beauty/red-nail-polish/1.webp', '折扣', '正红色指甲油，免烤快干，色泽饱满有光泽。', '[{"name":"色号","options":["正红色"]}]', 1, 'APPROVED'),
(139, '植物精粹温和洗手液 500ml', NULL, 6, 6, '美妍美妆', 29.90, 24.90, 300, 980, 4.7, '🧼', 'linear-gradient(135deg,#d299c2,#fef9d7)', 'https://cdn.dummyjson.com/product-images/skin-care/attitude-super-leaves-hand-soap/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/skin-care/attitude-super-leaves-hand-soap/1.webp,https://cdn.dummyjson.com/product-images/skin-care/attitude-super-leaves-hand-soap/2.webp,https://cdn.dummyjson.com/product-images/skin-care/attitude-super-leaves-hand-soap/3.webp', '折扣', '植物精粹配方，温和清洁不紧绷，全家可用。', '[{"name":"规格","options":["500ml"]}]', 1, 'APPROVED'),
(140, '香氛保湿沐浴露 750ml', NULL, 6, 6, '美妍美妆', 49.90, 39.90, 220, 1230, 4.7, '🧴', 'linear-gradient(135deg,#667eea,#764ba2)', 'https://cdn.dummyjson.com/product-images/skin-care/olay-ultra-moisture-shea-butter-body-wash/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/skin-care/olay-ultra-moisture-shea-butter-body-wash/1.webp,https://cdn.dummyjson.com/product-images/skin-care/olay-ultra-moisture-shea-butter-body-wash/2.webp,https://cdn.dummyjson.com/product-images/skin-care/olay-ultra-moisture-shea-butter-body-wash/3.webp', '折扣,热卖', '香氛沐浴露，绵密泡沫，洗后滋润不干燥。', '[{"name":"规格","options":["750ml"]}]', 1, 'APPROVED'),
(141, '男士清爽身体乳 250ml', NULL, 6, 6, '美妍美妆', 39.90, 29.90, 180, 560, 4.6, '🧴', 'linear-gradient(135deg,#f093fb,#f5576c)', 'https://cdn.dummyjson.com/product-images/skin-care/vaseline-men-body-and-face-lotion/thumbnail.webp', 'https://cdn.dummyjson.com/product-images/skin-care/vaseline-men-body-and-face-lotion/1.webp,https://cdn.dummyjson.com/product-images/skin-care/vaseline-men-body-and-face-lotion/2.webp,https://cdn.dummyjson.com/product-images/skin-care/vaseline-men-body-and-face-lotion/3.webp', '折扣', '男士身体乳，清爽不黏腻，滋润保湿一抹即吸收。', '[{"name":"规格","options":["250ml"]}]', 1, 'APPROVED');

INSERT IGNORE INTO product_sku (id, product_id, specs) VALUES
(31, 93, '{"颜色":"黑色"}'),
(32, 93, '{"颜色":"白色"}'),
(33, 115, '{"尺码":"M"}'),
(34, 115, '{"尺码":"L"}'),
(35, 128, '{"颜色":"白色"}'),
(36, 120, '{"尺码":"S"}'),
(37, 120, '{"尺码":"M"}');

INSERT IGNORE INTO product_category_rel (product_id, category_id) VALUES
(93, 2),
(94, 2),
(95, 2),
(96, 2),
(97, 2),
(98, 4),
(98, 41),
(99, 4),
(99, 41),
(100, 4),
(100, 41),
(101, 4),
(101, 42),
(102, 4),
(102, 42),
(103, 4),
(104, 4),
(105, 4),
(106, 4),
(107, 4),
(108, 4),
(109, 4),
(110, 4),
(111, 4),
(112, 4),
(113, 4),
(114, 4),
(115, 3),
(115, 31),
(116, 3),
(116, 31),
(117, 3),
(117, 32),
(118, 3),
(118, 32),
(119, 3),
(119, 32),
(120, 3),
(121, 3),
(122, 3),
(123, 3),
(124, 3),
(125, 3),
(126, 3),
(127, 3),
(128, 1),
(128, 12),
(129, 1),
(129, 12),
(130, 1),
(130, 12),
(131, 1),
(131, 12),
(132, 1),
(132, 13),
(133, 1),
(133, 11),
(134, 6),
(135, 6),
(136, 6),
(137, 6),
(138, 6),
(139, 6),
(140, 6),
(141, 6);
