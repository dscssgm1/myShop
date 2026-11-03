-- 存量库升级：积分发放/扣回 + 积分商城（2026-11-03）
-- 1) 积分明细增加业务单号，售后退款时按单扣回订单完成发放的积分；
-- 2) 新增积分商城商品表与兑换记录表。
-- 新库直接导入 system.sql 即可，无需执行本脚本。
-- 用法：docker exec -i myshop-mysql mysql -uroot -proot --default-character-set=utf8mb4 myshop < sql/upgrade-20261103-points-mall.sql

ALTER TABLE sys_points_log
    ADD COLUMN biz_no VARCHAR(32) NOT NULL DEFAULT '' COMMENT '关联业务单号（订单号），用于售后扣回按单定位' AFTER type,
    ADD KEY idx_user_biz (user_id, biz_no);

CREATE TABLE IF NOT EXISTS sys_points_mall_item (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    product_id    BIGINT        NOT NULL COMMENT '来源商品 id，兑换下单用它取商品',
    product_name  VARCHAR(128)  NOT NULL,
    product_emoji VARCHAR(16)   NOT NULL DEFAULT '',
    product_color VARCHAR(16)   NOT NULL DEFAULT '',
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
    points     INT         NOT NULL COMMENT '消耗积分',
    order_no   VARCHAR(32) NOT NULL DEFAULT '' COMMENT '兑换生成的订单号',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '积分兑换记录';
