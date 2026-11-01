-- 平台服务：审核中心、运营配置、日志

CREATE TABLE IF NOT EXISTS audit_record (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    type           VARCHAR(32)   NOT NULL COMMENT '商家入驻/商品上架/商家信息变更/活动报名/退款审核/举报投诉',
    biz_id         BIGINT        NULL COMMENT '业务主键',
    title          VARCHAR(200)  NOT NULL DEFAULT '',
    detail         VARCHAR(1000) NOT NULL DEFAULT '',
    applicant_id   BIGINT        NULL,
    applicant_name VARCHAR(50)   NOT NULL DEFAULT '',
    status         VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/CANCELLED',
    reason         VARCHAR(255)  NOT NULL DEFAULT '',
    payload        TEXT          NULL COMMENT '业务扩展数据 JSON',
    audited_by     BIGINT        NULL,
    audited_at     DATETIME      NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_type_status (type, status),
    KEY idx_applicant (applicant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审核记录';

CREATE TABLE IF NOT EXISTS config_item (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    config_group VARCHAR(32)  NOT NULL COMMENT 'banner/pay-method/aftersale-reason/report-type/sensitive-word/brand/spec-template',
    config_key   VARCHAR(64)  NOT NULL,
    config_value TEXT         NOT NULL,
    description  VARCHAR(200) NOT NULL DEFAULT '',
    updated_by   BIGINT       NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_key (config_group, config_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '运营配置';

CREATE TABLE IF NOT EXISTS log_operation (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NULL,
    user_name  VARCHAR(50)  NOT NULL DEFAULT '',
    action     VARCHAR(50)  NOT NULL DEFAULT '',
    module     VARCHAR(50)  NOT NULL DEFAULT '',
    detail     VARCHAR(500) NOT NULL DEFAULT '',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_time (created_at),
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作日志';

CREATE TABLE IF NOT EXISTS log_login (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NULL,
    username   VARCHAR(32)  NOT NULL DEFAULT '',
    method     VARCHAR(32)  NOT NULL DEFAULT '密码登录',
    ip         VARCHAR(64)  NOT NULL DEFAULT '',
    result     VARCHAR(32)  NOT NULL DEFAULT '成功',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_time (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '登录日志';

CREATE TABLE IF NOT EXISTS log_system (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    level      VARCHAR(16)  NOT NULL DEFAULT 'INFO',
    module     VARCHAR(50)  NOT NULL DEFAULT '',
    message    VARCHAR(500) NOT NULL DEFAULT '',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_level_time (level, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统日志';

INSERT IGNORE INTO config_item (id, config_group, config_key, config_value, description) VALUES
(1, 'pay-method', 'alipay', '{"name":"支付宝","enabled":true}', '支付宝'),
(2, 'pay-method', 'wechat', '{"name":"微信支付","enabled":true}', '微信支付'),
(3, 'pay-method', 'card', '{"name":"银行卡","enabled":true}', '银行卡'),
(4, 'aftersale-reason', 'default', '["不想要了","商品质量问题","商品与描述不符","发错货/漏发","其他原因"]', '售后原因'),
(5, 'report-type', 'default', '["假货/仿冒","违禁品","侵权","虚假宣传","其他"]', '举报类型'),
(6, 'brand', 'default', '["华为","小米","耐克","阿迪达斯","苹果","三星"]', '品牌库'),
(7, 'sensitive-word', 'default', '["假冒伪劣","违禁品","走私"]', '敏感词'),
(8, 'banner', 'default', '[{"title":"双 11 全球狂欢节","subtitle":"每满 300 减 50 · 上不封顶"},{"title":"数码焕新季","subtitle":"手机数码 满 2000 减 200"}]', '首页 Banner');

INSERT IGNORE INTO audit_record (id, type, title, detail, applicant_name, status) VALUES
(1, '商家入驻', '书虫书店申请入驻', '经营类目：图书文具 ｜ 联系人：钱经理', '钱经理', 'PENDING'),
(2, '商品上架', '新商品上架 - 无线充电器', '价格 ¥129，库存 200', '店铺业务员', 'PENDING');

INSERT IGNORE INTO log_system (id, level, module, message) VALUES
(1, 'INFO', '系统', '平台审核服务启动'),
(2, 'INFO', '定时任务', '每日数据统计任务执行完成');
