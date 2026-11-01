-- 商品服务：分类与商品
-- 可导入 myshop（共库）或独立的商品库（分库），表名不含库名

CREATE TABLE IF NOT EXISTS product_category (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(50)  NOT NULL,
    icon       VARCHAR(32)  NOT NULL DEFAULT '',
    color      VARCHAR(128) NOT NULL DEFAULT '',
    sort       INT          NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_sort (sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品分类';

INSERT IGNORE INTO product_category (id, name, icon, color, sort) VALUES
(1, '手机数码', '📱', '#e6f4ff', 1),
(2, '家用电器', '📺', '#fff7e6', 2),
(3, '服饰鞋包', '👔', '#f9f0ff', 3),
(4, '食品生鲜', '🍎', '#f6ffed', 4),
(5, '图书文具', '📚', '#fff0f6', 5),
(6, '美妆个护', '💄', '#fff1f0', 6);

CREATE TABLE IF NOT EXISTS product (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    category_id  BIGINT        NOT NULL,
    shop_id      BIGINT        NOT NULL DEFAULT 1,
    shop_name    VARCHAR(100)  NOT NULL DEFAULT '',
    name         VARCHAR(200)  NOT NULL,
    price        DECIMAL(10, 2) NOT NULL,
    origin_price DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    stock        INT           NOT NULL DEFAULT 0,
    sales        INT           NOT NULL DEFAULT 0,
    rating       DECIMAL(3, 1) NOT NULL DEFAULT 5.0,
    emoji        VARCHAR(32)   NOT NULL DEFAULT '',
    color        VARCHAR(255)  NOT NULL DEFAULT '',
    tags         VARCHAR(255)  NOT NULL DEFAULT '',
    description  TEXT          NULL,
    specs        TEXT          NULL,
    status       TINYINT       NOT NULL DEFAULT 1,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_category (category_id),
    KEY idx_status_sales (status, sales),
    KEY idx_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品';

INSERT IGNORE INTO product
(id, category_id, shop_id, shop_name, name, price, origin_price, stock, sales, rating, emoji, color, tags, description, specs, status) VALUES
(1, 1, 1, '数码旗舰店', '星辰 X5 Pro 5G 智能手机 12GB+256GB', 3999.00, 4599.00, 500, 2314, 4.8, '📱', 'linear-gradient(135deg,#667eea,#764ba2)', '自营,秒杀',
 '6.7 英寸 2K 高刷屏，1 亿像素影像系统，5500mAh 大电池 + 100W 快充。',
 '颜色：曜石黑 / 星河银\n内存：12GB+256GB\n屏幕：6.7 英寸 OLED', 1),
(2, 1, 1, '数码旗舰店', '无线降噪蓝牙耳机 主动降噪 40h 续航', 399.00, 599.00, 1200, 8765, 4.7, '🎧', 'linear-gradient(135deg,#5b86e5,#36d1dc)', '满减',
 '主动降噪，入耳式设计，单次续航 10 小时，配合充电仓可达 40 小时。',
 '颜色：云白 / 曜黑\n连接：蓝牙 5.3\n续航：40 小时', 1),
(3, 2, 2, '家电优选', '4K 超高清智能电视机 65 英寸', 3299.00, 3999.00, 200, 1102, 4.6, '📺', 'linear-gradient(135deg,#f7971e,#ffd200)', '自营,满减',
 '65 英寸 4K 全面屏，支持 HDR10+ 与远场语音，内置智能系统。',
 '尺寸：65 英寸\n分辨率：3840×2160\n接口：HDMI×3 / USB×2', 1),
(4, 2, 2, '家电优选', '家用滚筒洗衣机 10KG 变频洗烘一体', 2699.00, 3199.00, 150, 876, 4.5, '🧺', 'linear-gradient(135deg,#8e9eab,#eef2f3)', '满减',
 '10KG 大容量，变频电机，洗烘一体，支持除菌与筒自洁。',
 '容量：10KG\n能效：一级\n功能：洗烘一体', 1),
(5, 3, 3, '潮流服饰馆', '复古牛仔外套 男士宽松潮流夹克', 229.00, 399.00, 800, 3421, 4.4, '🧥', 'linear-gradient(135deg,#4568dc,#b06ab3)', '折扣',
 '复古水洗牛仔面料，宽松版型，春秋季百搭夹克。',
 '颜色：复古蓝\n尺码：M / L / XL / XXL\n面料：棉', 1),
(6, 3, 3, '潮流服饰馆', '轻便缓震跑步鞋 男女同款运动鞋', 299.00, 499.00, 1000, 5608, 4.7, '👟', 'linear-gradient(135deg,#11998e,#38ef7d)', '秒杀',
 '轻量缓震中底，透气网面，适合日常慢跑与通勤。',
 '颜色：黑白 / 灰蓝\n尺码：36-45\n鞋面：飞织网面', 1),
(7, 4, 4, '鲜丰水果', '红富士苹果 5KG 装 新鲜时令水果', 39.90, 59.90, 2000, 12345, 4.9, '🍎', 'linear-gradient(135deg,#f83600,#f9d423)', '秒杀,产地直发',
 '产地直发，脆甜多汁，单果约 200g，5KG 家庭装。',
 '规格：5KG / 箱\n产地：山东烟台\n储存：冷藏', 1),
(8, 4, 4, '鲜丰水果', '进口牛排套餐 原切西冷 10 片装', 169.00, 259.00, 600, 2345, 4.6, '🥩', 'linear-gradient(135deg,#c31432,#240b36)', '满减',
 '原切西冷牛排，单片 150g，共 10 片，全程冷链配送。',
 '规格：150g×10 片\n部位：西冷\n储存：-18℃ 冷冻', 1),
(9, 5, 3, '潮流服饰馆', '《深入理解计算机系统》中文版 精装', 99.00, 139.00, 300, 987, 4.9, '📚', 'linear-gradient(135deg,#1e3c72,#2a5298)', '满减',
 '计算机系统经典教材，从程序员视角理解计算机系统。',
 '版本：中文版\n装帧：精装\n出版社：机械工业出版社', 1),
(10, 5, 3, '潮流服饰馆', '高颜值按动中性笔 0.5mm 12 支装', 19.90, 35.00, 5000, 8907, 4.5, '🖊️', 'linear-gradient(135deg,#ff9966,#ff5e62)', '折扣',
 '按动式设计，书写顺滑，速干墨水，12 支混色装。',
 '笔尖：0.5mm\n数量：12 支\n颜色：黑 / 蓝 / 红', 1),
(11, 6, 3, '潮流服饰馆', '烟酰胺美白精华液 30ml 提亮肤色', 159.00, 239.00, 900, 4567, 4.6, '🧴', 'linear-gradient(135deg,#fd1d1d,#fcb045)', '满减,热卖',
 '烟酰胺复配传明酸，提亮肤色，改善暗沉，温和好吸收。',
 '容量：30ml\n成分：烟酰胺 / 传明酸\n肤质：多种肤质', 1),
(12, 6, 3, '潮流服饰馆', '氨基酸温和洁面乳 100g 敏感肌可用', 59.00, 89.00, 2000, 6789, 4.7, '🧼', 'linear-gradient(135deg,#43cea2,#185a9d)', '折扣',
 '氨基酸表活，温和清洁不紧绷，敏感肌可用。',
 '容量：100g\n成分：氨基酸表活\n肤质：敏感肌适用', 1);
