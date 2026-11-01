-- 消息服务：客服会话、消息、系统通知

CREATE TABLE IF NOT EXISTS message_session (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    type           VARCHAR(16)  NOT NULL COMMENT 'PLATFORM/SHOP',
    shop_id        BIGINT       NULL,
    shop_name      VARCHAR(100) NOT NULL DEFAULT '',
    shop_icon      VARCHAR(16)  NOT NULL DEFAULT '',
    customer_id    BIGINT       NOT NULL COMMENT '买家用户 ID',
    customer_name  VARCHAR(50)  NOT NULL DEFAULT '',
    last_content   VARCHAR(255) NOT NULL DEFAULT '',
    last_time      DATETIME     NULL,
    buyer_unread   INT          NOT NULL DEFAULT 0,
    cs_unread      INT          NOT NULL DEFAULT 0,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_type_shop_customer (type, shop_id, customer_id),
    KEY idx_customer (customer_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '客服会话';

CREATE TABLE IF NOT EXISTS message (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    session_id  BIGINT       NOT NULL,
    sender_type VARCHAR(16)  NOT NULL COMMENT 'BUYER/CS',
    content     VARCHAR(1000) NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_session_time (session_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '会话消息';

CREATE TABLE IF NOT EXISTS notice (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    user_id    BIGINT        NOT NULL,
    title      VARCHAR(100)  NOT NULL DEFAULT '',
    content    VARCHAR(500)  NOT NULL DEFAULT '',
    read_flag  TINYINT       NOT NULL DEFAULT 0,
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_read (user_id, read_flag)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统通知';
