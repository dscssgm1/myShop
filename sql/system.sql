-- 系统服务：用户、角色、个人资产（地址/银行卡/收藏/足迹）、会员等级与积分
-- 可导入 myshop（共库）或独立的系统库（分库），表名不含库名

CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(32)  NOT NULL COMMENT '角色编码，与前端约定一致',
    name        VARCHAR(50)  NOT NULL COMMENT '角色名称',
    group_code  VARCHAR(16)  NOT NULL COMMENT '所属端：USER/MERCHANT/LOGISTICS/PLATFORM',
    icon        VARCHAR(16)  NOT NULL DEFAULT '',
    description VARCHAR(200) NOT NULL DEFAULT '',
    sort        INT          NOT NULL DEFAULT 0,
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code),
    KEY idx_group (group_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色';

CREATE TABLE IF NOT EXISTS sys_user (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    username      VARCHAR(32)   NOT NULL,
    password      VARCHAR(100)  NOT NULL COMMENT 'BCrypt 密文',
    nickname      VARCHAR(50)   NOT NULL DEFAULT '',
    email         VARCHAR(100)  NULL,
    phone         VARCHAR(20)   NULL,
    avatar        VARCHAR(255)  NULL,
    role_code     VARCHAR(32)   NOT NULL,
    shop_id       BIGINT        NULL COMMENT '商家端角色所属店铺',
    company_id    BIGINT        NULL COMMENT '物流端角色所属物流公司',
    courier_id    BIGINT        NULL COMMENT '快递员对应的快递员档案',
    status        TINYINT       NOT NULL DEFAULT 1 COMMENT '1 启用 0 禁用',
    last_login_at DATETIME      NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_role (role_code),
    KEY idx_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户';

CREATE TABLE IF NOT EXISTS sys_user_address (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    receiver_name  VARCHAR(50)  NOT NULL DEFAULT '',
    receiver_phone VARCHAR(20)  NOT NULL DEFAULT '',
    address        VARCHAR(255) NOT NULL DEFAULT '',
    is_default     TINYINT      NOT NULL DEFAULT 0,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '收货地址';

CREATE TABLE IF NOT EXISTS sys_user_bank_card (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    bank_name  VARCHAR(50)  NOT NULL DEFAULT '',
    card_no    VARCHAR(32)  NOT NULL DEFAULT '',
    card_type  VARCHAR(16)  NOT NULL DEFAULT '储蓄卡',
    holder     VARCHAR(50)  NOT NULL DEFAULT '',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '银行卡';

CREATE TABLE IF NOT EXISTS sys_user_favorite (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    user_id    BIGINT   NOT NULL,
    product_id BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_product (user_id, product_id),
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品收藏';

CREATE TABLE IF NOT EXISTS sys_user_footprint (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    user_id    BIGINT   NOT NULL,
    product_id BIGINT   NOT NULL,
    viewed_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_product (user_id, product_id),
    KEY idx_user_time (user_id, viewed_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '浏览足迹';

CREATE TABLE IF NOT EXISTS sys_member_level (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    name          VARCHAR(50)   NOT NULL,
    min_growth    INT           NOT NULL DEFAULT 0,
    points_rate   DECIMAL(4, 2) NOT NULL DEFAULT 1.00,
    discount      DECIMAL(4, 2) NOT NULL DEFAULT 10.00 COMMENT '折扣，如 9.50 表示 9.5 折',
    free_shipping TINYINT       NOT NULL DEFAULT 0,
    sort          INT           NOT NULL DEFAULT 0,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_growth (min_growth)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '会员等级';

CREATE TABLE IF NOT EXISTS sys_points_account (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    user_id    BIGINT   NOT NULL,
    points     INT      NOT NULL DEFAULT 0,
    growth     INT      NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '积分与成长值';

CREATE TABLE IF NOT EXISTS sys_points_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    change_amount INT          NOT NULL,
    balance       INT          NOT NULL DEFAULT 0,
    type          VARCHAR(16)  NOT NULL DEFAULT 'EARN',
    biz_no        VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '关联业务单号（订单号），用于售后扣回按单定位',
    remark        VARCHAR(200) NOT NULL DEFAULT '',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_time (user_id, created_at),
    KEY idx_user_biz (user_id, biz_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '积分明细';

CREATE TABLE IF NOT EXISTS sys_points_mall_item (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    product_id    BIGINT        NOT NULL COMMENT '来源商品 id，兑换下单用它取商品',
    product_name  VARCHAR(128)  NOT NULL,
    product_emoji VARCHAR(16)   NOT NULL DEFAULT '',
    product_color VARCHAR(16)   NOT NULL DEFAULT '',
    product_cover_image VARCHAR(512) NULL COMMENT '商品主图快照',
    product_price DECIMAL(10, 2) NOT NULL COMMENT '上架时的原价快照（元）',
    shop_id       BIGINT        NOT NULL,
    shop_name     VARCHAR(64)   NOT NULL DEFAULT '',
    status        TINYINT       NOT NULL DEFAULT 1 COMMENT '1 上架 0 下架',
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_product (product_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '积分商城商品';

CREATE TABLE IF NOT EXISTS sys_points_exchange (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    item_id    BIGINT      NOT NULL COMMENT '积分商城商品 id',
    item_name  VARCHAR(128) NOT NULL,
    item_emoji VARCHAR(16) NOT NULL DEFAULT '',
    item_cover VARCHAR(512) NULL COMMENT '商品主图快照',
    points     INT         NOT NULL COMMENT '消耗积分',
    order_no   VARCHAR(32) NOT NULL DEFAULT '' COMMENT '兑换生成的订单号',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '积分兑换记录';

CREATE TABLE IF NOT EXISTS sys_email_code (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    email      VARCHAR(100) NOT NULL,
    code       VARCHAR(8)  NOT NULL,
    purpose    VARCHAR(32) NOT NULL DEFAULT 'CHANGE_EMAIL',
    expire_at  DATETIME    NOT NULL,
    used       TINYINT     NOT NULL DEFAULT 0,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_email_purpose (email, purpose, used)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '邮箱验证码';

CREATE TABLE IF NOT EXISTS sys_login_record (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    ip          VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '登录 IP（X-Forwarded-For 解析）',
    device_id   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '前端持久设备 UUID（浏览器无法读取 MAC）',
    device_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT '设备描述，如 Windows · Chrome',
    login_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user (user_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '登录记录';

CREATE TABLE IF NOT EXISTS sys_trusted_device (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    device_id   VARCHAR(64)  NOT NULL COMMENT '前端持久设备 UUID',
    device_name VARCHAR(128) NOT NULL DEFAULT '',
    trusted_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_device (user_id, device_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '信任设备';

-- ---------------------------------------------------------------- 种子数据

INSERT IGNORE INTO sys_role (id, code, name, group_code, icon, description, sort) VALUES
(1,  'user',              '买家',       'USER',      '🙋', '浏览商品、下单支付、评价售后', 1),
(2,  'merchant',          '店长',       'MERCHANT',  '🏪', '店铺全部管理权限与员工管理', 2),
(3,  'merchant-sales',    '商家业务员', 'MERCHANT',  '👔', '补货、折扣、商品属性、营销活动', 3),
(4,  'merchant-cs',       '商家客服',   'MERCHANT',  '🎧', '响应买家咨询与售后', 4),
(5,  'logistics',         '物流管理员', 'LOGISTICS', '🚚', '运单总览与快递员管理', 5),
(6,  'logistics-ops',     '调度员',     'LOGISTICS', '📦', '揽收调度与运输管理', 6),
(7,  'logistics-courier', '快递员',     'LOGISTICS', '🛵', '负责区域内的揽收与派送', 7),
(8,  'admin',             '平台管理员', 'PLATFORM',  '🛠️', '平台全部管理权限与员工管理', 8),
(9,  'admin-sales',       '平台业务员', 'PLATFORM',  '📋', '审核、促销活动、分类与运营配置', 9),
(10, 'admin-cs',          '平台客服',   'PLATFORM',  '🛡️', '客服工作台与事务登记', 10),
(11, 'admin-ops',         '平台运维员', 'PLATFORM',  '🔧', '系统日志与运维', 11);

INSERT IGNORE INTO sys_member_level (id, name, min_growth, points_rate, discount, free_shipping, sort) VALUES
(1, '普通会员', 0,     1.00, 10.00, 0, 1),
(2, '黄金会员', 2000,  1.20, 9.80,  0, 2),
(3, '铂金会员', 5000,  1.50, 9.50,  1, 3),
(4, '钻石会员', 10000, 2.00, 9.00,  1, 4);
