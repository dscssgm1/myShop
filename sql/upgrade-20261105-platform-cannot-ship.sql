-- 发货权限收敛为商家专属：平台方（admin / admin-sales 等平台角色）只查看订单，不得发货
-- 后端：OrderController.ship 改为 requireGroup("MERCHANT") + requirePerm("merchant:orders:ship")
--       OrderApplicationService.ship 的店铺归属校验不再放过 shopId 为空的账号
-- 前端：平台端订单列表移除「发货」按钮，perm/tree.js 移除 admin:orders:ship 权限码
-- 说明：已在库中的平台角色授权行一并删除，避免历史会话仍持有该权限码

DELETE FROM sys_role_menu WHERE perm_key = 'admin:orders:ship';

-- 清理已失效的权限码后，核对平台侧剩余订单权限
SELECT role_code, perm_key FROM sys_role_menu WHERE perm_key LIKE 'admin:orders%';
