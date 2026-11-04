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
    stage1_by      BIGINT        NULL COMMENT '两级审核第一级（业务员初审）审核人',
    stage1_at      DATETIME      NULL COMMENT '两级审核第一级（业务员初审）审核时间',
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_type_status (type, status),
    KEY idx_applicant (applicant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审核记录';

CREATE TABLE IF NOT EXISTS config_item (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    config_group VARCHAR(32)  NOT NULL COMMENT 'banner/pay-method/aftersale-reason/report-type/sensitive-word/points-rule/service-guarantee/system 及各类原因预设（audit-reject-reason 等）',
    config_key   VARCHAR(64)  NOT NULL,
    config_value TEXT         NOT NULL,
    description  VARCHAR(200) NOT NULL DEFAULT '',
    updated_by   BIGINT       NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_key (config_group, config_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '运营配置';

-- 行政区划（省/市/县三级）：表结构在此，种子数据见 region.sql
CREATE TABLE IF NOT EXISTS region (
    id         BIGINT      NOT NULL COMMENT '行政区划代码（6 位）',
    parent_id  BIGINT      NOT NULL DEFAULT 0 COMMENT '父级代码，省级为 0',
    name       VARCHAR(50) NOT NULL,
    level      TINYINT     NOT NULL COMMENT '1 省 2 市 3 县区',
    status     TINYINT     NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用（停用后选择器不展示）',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_parent (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '行政区划（省/市/县三级）';

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
(8, 'banner', 'default', '[{"title":"双 11 全球狂欢节","subtitle":"每满 300 减 50 · 上不封顶","color":"linear-gradient(90deg,#ff416c,#ff4b2b)","link":"/products"},{"title":"数码焕新季","subtitle":"手机数码 满 2000 减 200","color":"linear-gradient(90deg,#396afc,#2948ff)","link":"/products"}]', '首页 Banner'),
(9, 'points-rule', 'earn-amount-per-point', '1', '每消费多少元累计 1 积分'),
(10, 'points-rule', 'deduct-points-per-yuan', '100', '下单抵现：多少积分抵 1 元（0 或未配置表示关闭抵现）'),
(11, 'points-rule', 'deduct-max-percent', '50', '下单抵现：最多抵应付金额的百分比'),
(12, 'points-rule', 'review-reward', '10', '发表评价奖励积分'),
(13, 'system', 'platformName', 'myShop 电商购物平台', '平台名称'),
(14, 'system', 'servicePhone', '400-888-8888', '客服电话'),
(15, 'system', 'serviceHours', '09:00-21:00', '客服服务时间'),
(16, 'system', 'freeShippingThreshold', '99', '包邮门槛（元）'),
(17, 'system', 'orderTimeoutMinutes', '30', '待支付订单自动取消时间（分钟）'),
(18, 'service-guarantee', 'seven-day-return', '{"name":"七天无理由退货","icon":"↩️","enabled":true}', '七天无理由退货'),
(19, 'service-guarantee', 'authentic-guarantee', '{"name":"正品保障","icon":"✅","enabled":true}', '正品保障'),
(20, 'service-guarantee', 'national-warranty', '{"name":"全国联保","icon":"🛡️","enabled":true}', '全国联保'),
(21, 'service-guarantee', 'fast-delivery', '{"name":"极速发货","icon":"🚀","enabled":true}', '极速发货'),
(22, 'audit-reject-reason', 'default', '["资料不完整","资质不符合要求","信息与实际不符","图片或文案违规","其他"]', '审核驳回原因'),
(23, 'aftersale-reject-reason', 'default', '["凭证不足","商品已使用影响二次销售","超出售后期限","商品无质量问题","其他"]', '售后拒绝原因'),
(24, 'off-shelf-reason', 'default', '["库存不足","商品信息待完善","换季/清仓临时下架","违规整改","其他"]', '商品下架原因'),
(25, 'shop-disable-reason', 'default', '["售假/发布违禁商品","服务质量差、投诉集中","违规经营","长时间未经营","其他"]', '店铺停用原因'),
(26, 'dividend', 'platform-ratio', '15', '平台与物流公司的运费分成比例（平台占运费的百分比）');

INSERT IGNORE INTO log_system (id, level, module, message) VALUES
(1, 'INFO', '系统', '平台审核服务启动'),
(2, 'INFO', '定时任务', '每日数据统计任务执行完成');
