-- 物流服务：物流公司、快递员、运单、轨迹

CREATE TABLE IF NOT EXISTS logistics_company (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    code       VARCHAR(16)  NOT NULL DEFAULT '' COMMENT '运单号前缀',
    contact    VARCHAR(50)  NOT NULL DEFAULT '',
    phone      VARCHAR(20)  NOT NULL DEFAULT '',
    color      VARCHAR(32)  NOT NULL DEFAULT '#666',
    status     TINYINT      NOT NULL DEFAULT 0 COMMENT '1 已入驻 0 停用',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '物流公司';

CREATE TABLE IF NOT EXISTS courier (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    company_id BIGINT      NOT NULL,
    name       VARCHAR(50) NOT NULL,
    phone      VARCHAR(20) NOT NULL DEFAULT '',
    region     VARCHAR(50) NOT NULL DEFAULT '' COMMENT '负责区域',
    status     TINYINT     NOT NULL DEFAULT 1,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_company_region (company_id, region)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '快递员';

CREATE TABLE IF NOT EXISTS shipment (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    tracking_no      VARCHAR(32)  NOT NULL,
    order_no         VARCHAR(32)  NOT NULL,
    shop_id          BIGINT       NOT NULL DEFAULT 0,
    shop_name        VARCHAR(100) NOT NULL DEFAULT '',
    pickup_address   VARCHAR(255) NOT NULL DEFAULT '',
    pickup_region    VARCHAR(50)  NOT NULL DEFAULT '',
    company_id       BIGINT       NOT NULL,
    company_name     VARCHAR(100) NOT NULL DEFAULT '',
    receiver_name    VARCHAR(50)  NOT NULL DEFAULT '',
    receiver_phone   VARCHAR(20)  NOT NULL DEFAULT '',
    receiver_address VARCHAR(255) NOT NULL DEFAULT '',
    receiver_region  VARCHAR(50)  NOT NULL DEFAULT '',
    items_text       VARCHAR(500) NOT NULL DEFAULT '',
    status           VARCHAR(16)  NOT NULL DEFAULT 'WAITING_PICKUP' COMMENT 'WAITING_PICKUP/PICKED/IN_TRANSIT/DELIVERING/SIGNED',
    courier_id       BIGINT       NULL,
    courier_name     VARCHAR(50)  NOT NULL DEFAULT '',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted          TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tracking_no (tracking_no),
    KEY idx_order (order_no),
    KEY idx_company_status (company_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '运单';

CREATE TABLE IF NOT EXISTS shipment_trace (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    shipment_id BIGINT       NOT NULL,
    content     VARCHAR(255) NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_shipment (shipment_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '物流轨迹';

INSERT IGNORE INTO logistics_company (id, name, code, contact, phone, color, status) VALUES
(1, '顺丰速运', 'SF', '王经理', '13900001001', '#c7000b', 1),
(2, '京东物流', 'JD', '李经理', '13900001002', '#e1251b', 1),
(3, '圆通速递', 'YTO', '张经理', '13900001003', '#0b6fb3', 1),
(4, '中通快递', 'ZTO', '刘经理', '13900001004', '#0b4f9c', 0);

INSERT IGNORE INTO courier (id, company_id, name, phone, region, status) VALUES
(1, 1, '快递员小李', '13900002001', '北京市·朝阳区', 1),
(2, 1, '快递员小王', '13900002002', '北京市·海淀区', 1),
(3, 1, '快递员小张', '13900002003', '上海市·浦东新区', 1);
