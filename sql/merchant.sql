-- 商家服务：店铺、店铺员工、入驻申请

CREATE TABLE IF NOT EXISTS shop (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100)  NOT NULL,
    icon        VARCHAR(16)   NOT NULL DEFAULT '',
    color       VARCHAR(255)  NOT NULL DEFAULT '',
    description VARCHAR(500)  NOT NULL DEFAULT '',
    contact     VARCHAR(50)   NOT NULL DEFAULT '',
    phone       VARCHAR(20)   NOT NULL DEFAULT '',
    region      VARCHAR(50)   NOT NULL DEFAULT '',
    address     VARCHAR(255)  NOT NULL DEFAULT '',
    categories  VARCHAR(500)  NOT NULL DEFAULT '[]' COMMENT '经营类目 JSON 数组',
    sales       INT           NOT NULL DEFAULT 0,
    status      TINYINT       NOT NULL DEFAULT 1 COMMENT '1 启用 0 禁用',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '店铺';

CREATE TABLE IF NOT EXISTS shop_staff (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    shop_id    BIGINT       NOT NULL,
    user_id    BIGINT       NULL COMMENT '关联的系统账号，可为空',
    name       VARCHAR(50)  NOT NULL,
    phone      VARCHAR(20)  NOT NULL DEFAULT '',
    role_code  VARCHAR(32)  NOT NULL COMMENT 'merchant-sales / merchant-cs',
    status     TINYINT      NOT NULL DEFAULT 1,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_shop (shop_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '店铺员工';

CREATE TABLE IF NOT EXISTS shop_apply (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    shop_name   VARCHAR(100) NOT NULL,
    categories  VARCHAR(500) NOT NULL DEFAULT '[]',
    contact     VARCHAR(50)  NOT NULL DEFAULT '',
    phone       VARCHAR(20)  NOT NULL DEFAULT '',
    description VARCHAR(500) NOT NULL DEFAULT '',
    status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    reason      VARCHAR(255) NOT NULL DEFAULT '',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_user (user_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '入驻申请';

INSERT IGNORE INTO shop (id, name, icon, color, description, contact, phone, region, address, categories, sales, status) VALUES
(1, '数码旗舰店', '📱', 'linear-gradient(135deg,#667eea,#764ba2)', '专注数码好物，正品保障', '张经理', '13800000001', '北京市·海淀区', '北京市海淀区中关村大街 1 号数码大厦', '["手机数码","家用电器"]', 128900, 1),
(2, '家电优选', '📺', 'linear-gradient(135deg,#4facfe,#00f2fe)', '品质家电，送货入户', '李经理', '13800000002', '北京市·朝阳区', '北京市朝阳区望京 SOHO T2 家电仓', '["家用电器"]', 86300, 1),
(3, '潮流服饰馆', '👔', 'linear-gradient(135deg,#fa709a,#fee140)', '潮流穿搭，品质之选', '王经理', '13800000003', '北京市·朝阳区', '北京市朝阳区三里屯太古里仓储中心', '["服饰鞋包"]', 54200, 1),
(4, '鲜丰水果', '🍎', 'linear-gradient(135deg,#ff9a9e,#fecfef)', '产地直发，新鲜到家', '赵经理', '13800000004', '上海市·浦东新区', '上海市浦东新区张江路 88 号冷链仓', '["食品生鲜"]', 31700, 1),
(5, '书虫书店', '📚', 'linear-gradient(135deg,#a18cd1,#fbc2eb)', '正版好书，阅读相伴', '钱经理', '13800000005', '北京市·海淀区', '北京市海淀区学院路 30 号书库', '["图书文具"]', 15600, 1),
(6, '美妍美妆', '💄', 'linear-gradient(135deg,#fbc2eb,#a6c1ee)', '正品美妆，美丽无忧', '孙经理', '13800000006', '北京市·朝阳区', '北京市朝阳区国贸商城 B1 仓', '["美妆个护"]', 23400, 1);

INSERT IGNORE INTO shop_staff (id, shop_id, name, phone, role_code, status) VALUES
(1, 1, '店铺业务员', '13800001101', 'merchant-sales', 1),
(2, 1, '商家客服', '13800001102', 'merchant-cs', 1);
