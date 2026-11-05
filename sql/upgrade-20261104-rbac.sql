-- RBAC 菜单/按钮权限：角色勾选 sys_role_menu，网关将会话中的权限集下发，前后端共用权限码
-- 种子按各内置角色现状全量对齐；新角色默认无权限，由角色管理配置

CREATE TABLE IF NOT EXISTS sys_role_menu (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    role_code  VARCHAR(32)  NOT NULL COMMENT '角色编码（sys_role.code）',
    perm_key   VARCHAR(100) NOT NULL COMMENT '权限码（前端 perm/tree.js 定义）',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_key (role_code, perm_key),
    KEY idx_role (role_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色菜单/按钮权限';

-- ==================== 平台管理员：全量 ====================
INSERT IGNORE INTO sys_role_menu (role_code, perm_key) VALUES
('admin', 'admin:dashboard'),
('admin', 'admin:orders'),
('admin', 'admin:products'), ('admin', 'admin:products:create'), ('admin', 'admin:products:edit'), ('admin', 'admin:products:status'), ('admin', 'admin:products:delete'),
('admin', 'admin:categories'), ('admin', 'admin:categories:create'), ('admin', 'admin:categories:update'), ('admin', 'admin:categories:delete'),
('admin', 'admin:brands'), ('admin', 'admin:brands:create'), ('admin', 'admin:brands:update'), ('admin', 'admin:brands:delete'),
('admin', 'admin:users'), ('admin', 'admin:users:status'), ('admin', 'admin:users:role'), ('admin', 'admin:users:session'),
('admin', 'admin:shops'), ('admin', 'admin:shops:status'),
('admin', 'admin:promotions'), ('admin', 'admin:promotions:create'), ('admin', 'admin:promotions:update'), ('admin', 'admin:promotions:delete'), ('admin', 'admin:promotions:enrollment'),
('admin', 'admin:coupons'), ('admin', 'admin:coupons:create'), ('admin', 'admin:coupons:end'),
('admin', 'admin:companies'), ('admin', 'admin:companies:create'), ('admin', 'admin:companies:update'),
('admin', 'admin:audit'), ('admin', 'admin:audit:product'), ('admin', 'admin:audit:shopApply'), ('admin', 'admin:audit:record'), ('admin', 'admin:audit:review'), ('admin', 'admin:audit:aftersale'), ('admin', 'admin:audit:logistics-first'), ('admin', 'admin:audit:logistics-final'),
('admin', 'admin:config'), ('admin', 'admin:config:save'), ('admin', 'admin:config:spec'), ('admin', 'admin:config:member'), ('admin', 'admin:config:region'),
('admin', 'admin:points-goods'), ('admin', 'admin:points-goods:create'), ('admin', 'admin:points-goods:status'), ('admin', 'admin:points-goods:delete'),
('admin', 'admin:dividend'), ('admin', 'admin:dividend:adjust'),
('admin', 'admin:commission'),
('admin', 'admin:logs'),
('admin', 'admin:cs'),
('admin', 'admin:roles'), ('admin', 'admin:roles:save'), ('admin', 'admin:roles:delete'), ('admin', 'admin:roles:menus'),
('admin', 'admin:staff'), ('admin', 'admin:staff:create'), ('admin', 'admin:staff:update'), ('admin', 'admin:staff:status'), ('admin', 'admin:staff:password'), ('admin', 'admin:staff:delete');

-- ==================== 平台业务员：商家管理/审核中心/分类/运营配置（对齐原可见范围） ====================
INSERT IGNORE INTO sys_role_menu (role_code, perm_key) VALUES
('admin-sales', 'admin:shops'), ('admin-sales', 'admin:shops:status'),
('admin-sales', 'admin:audit'), ('admin-sales', 'admin:audit:product'), ('admin-sales', 'admin:audit:shopApply'), ('admin-sales', 'admin:audit:record'), ('admin-sales', 'admin:audit:review'), ('admin-sales', 'admin:audit:aftersale'), ('admin-sales', 'admin:audit:logistics-first'),
('admin-sales', 'admin:categories'), ('admin-sales', 'admin:categories:create'), ('admin-sales', 'admin:categories:update'), ('admin-sales', 'admin:categories:delete'),
('admin-sales', 'admin:config'), ('admin-sales', 'admin:config:save'), ('admin-sales', 'admin:config:spec'), ('admin-sales', 'admin:config:member'), ('admin-sales', 'admin:config:region');

-- ==================== 平台客服 / 运维：单页 ====================
INSERT IGNORE INTO sys_role_menu (role_code, perm_key) VALUES
('admin-cs', 'admin:cs'),
('admin-ops', 'admin:logs');

-- ==================== 店长：全量 ====================
INSERT IGNORE INTO sys_role_menu (role_code, perm_key) VALUES
('merchant', 'merchant:shop'), ('merchant', 'merchant:shop:edit'), ('merchant', 'merchant:shop:apply'),
('merchant', 'merchant:products'), ('merchant', 'merchant:products:create'), ('merchant', 'merchant:products:edit'), ('merchant', 'merchant:products:status'), ('merchant', 'merchant:products:price'), ('merchant', 'merchant:products:delete'),
('merchant', 'merchant:specs'), ('merchant', 'merchant:specs:create'), ('merchant', 'merchant:specs:delete'),
('merchant', 'merchant:orders'), ('merchant', 'merchant:orders:ship'),
('merchant', 'merchant:marketing'), ('merchant', 'merchant:marketing:promo-create'), ('merchant', 'merchant:marketing:promo-update'), ('merchant', 'merchant:marketing:promo-end'), ('merchant', 'merchant:marketing:coupon-create'), ('merchant', 'merchant:marketing:coupon-end'),
('merchant', 'merchant:enrollment'), ('merchant', 'merchant:enrollment:apply'), ('merchant', 'merchant:enrollment:cancel'),
('merchant', 'merchant:bill'),
('merchant', 'merchant:after-sales'), ('merchant', 'merchant:after-sales:review'), ('merchant', 'merchant:after-sales:refund'), ('merchant', 'merchant:after-sales:appeal'),
('merchant', 'merchant:reviews'), ('merchant', 'merchant:reviews:reply'), ('merchant', 'merchant:reviews:report'),
('merchant', 'merchant:cs'),
('merchant', 'merchant:roles'), ('merchant', 'merchant:roles:save'), ('merchant', 'merchant:roles:delete'), ('merchant', 'merchant:roles:menus'),
('merchant', 'merchant:staff'), ('merchant', 'merchant:staff:create'), ('merchant', 'merchant:staff:update'), ('merchant', 'merchant:staff:status'), ('merchant', 'merchant:staff:password'), ('merchant', 'merchant:staff:delete');

-- ==================== 商家业务员：对齐原可见范围 ====================
INSERT IGNORE INTO sys_role_menu (role_code, perm_key) VALUES
('merchant-sales', 'merchant:products'), ('merchant-sales', 'merchant:products:create'), ('merchant-sales', 'merchant:products:edit'), ('merchant-sales', 'merchant:products:status'), ('merchant-sales', 'merchant:products:price'), ('merchant-sales', 'merchant:products:delete'),
('merchant-sales', 'merchant:orders'), ('merchant-sales', 'merchant:orders:ship'),
('merchant-sales', 'merchant:marketing'), ('merchant-sales', 'merchant:marketing:promo-create'), ('merchant-sales', 'merchant:marketing:promo-update'), ('merchant-sales', 'merchant:marketing:promo-end'), ('merchant-sales', 'merchant:marketing:coupon-create'), ('merchant-sales', 'merchant:marketing:coupon-end'),
('merchant-sales', 'merchant:enrollment'), ('merchant-sales', 'merchant:enrollment:apply'), ('merchant-sales', 'merchant:enrollment:cancel'),
('merchant-sales', 'merchant:bill'),
('merchant-sales', 'merchant:after-sales'), ('merchant-sales', 'merchant:after-sales:review'), ('merchant-sales', 'merchant:after-sales:refund'), ('merchant-sales', 'merchant:after-sales:appeal'),
('merchant-sales', 'merchant:reviews'), ('merchant-sales', 'merchant:reviews:reply'), ('merchant-sales', 'merchant:reviews:report');

-- ==================== 商家客服：单页 ====================
INSERT IGNORE INTO sys_role_menu (role_code, perm_key) VALUES
('merchant-cs', 'merchant:cs');
