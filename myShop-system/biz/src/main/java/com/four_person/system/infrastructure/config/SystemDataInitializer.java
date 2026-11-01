package com.four_person.system.infrastructure.config;

import com.four_person.system.domain.model.SysUser;
import com.four_person.system.domain.repository.MembershipRepository;
import com.four_person.system.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 演示账号初始化：首次启动时补齐各端角色账号，密码统一为 123456。
 * 已存在的账号不会被覆盖，可安全重复执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemDataInitializer implements ApplicationRunner {

    private static final String DEFAULT_PASSWORD = "123456";

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        try {
            seedAll();
        } catch (Exception ex) {
            // 数据库未就绪时不阻塞服务启动，表初始化后重启即可补种数据
            log.warn("演示账号初始化失败，请确认已导入 sql/system.sql：{}", ex.getMessage());
        }
    }

    private void seedAll() {
        // 用户端
        seed("buyer", "购物达人", "user", null, null, null, 1280, 3500);
        // 商家端
        seed("merchant", "数码旗舰店", "merchant", 1L, null, null, 0, 0);
        seed("sales", "店铺业务员", "merchant-sales", 1L, null, null, 0, 0);
        seed("shopcs", "商家客服", "merchant-cs", 1L, null, null, 0, 0);
        // 物流端
        seed("logistics", "顺丰速运", "logistics", null, 1L, null, 0, 0);
        seed("ltops", "物流调度员", "logistics-ops", null, 1L, null, 0, 0);
        seed("courier", "快递员小李", "logistics-courier", null, 1L, 1L, 0, 0);
        seed("courier2", "快递员小王", "logistics-courier", null, 1L, 2L, 0, 0);
        seed("courier3", "快递员小张", "logistics-courier", null, 1L, 3L, 0, 0);
        // 平台端
        seed("admin", "平台管理员", "admin", null, null, null, 0, 0);
        seed("ops", "平台业务员", "admin-sales", null, null, null, 0, 0);
        seed("ptcs", "平台客服", "admin-cs", null, null, null, 0, 0);
        seed("devops", "平台运维员", "admin-ops", null, null, null, 0, 0);

        log.info("系统演示账号初始化完成，默认密码：{}", DEFAULT_PASSWORD);
    }

    private void seed(String username, String nickname, String roleCode,
                      Long shopId, Long companyId, Long courierId,
                      int points, int growth) {
        if (userRepository.existsByUsername(username)) {
            return;
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setNickname(nickname);
        user.setRoleCode(roleCode);
        user.setShopId(shopId);
        user.setCompanyId(companyId);
        user.setCourierId(courierId);
        user.setEmail(username + "@myshop.com");
        user.setStatus(1);
        userRepository.save(user);

        membershipRepository.createAccount(user.getId());
        if (points > 0 || growth > 0) {
            membershipRepository.addPoints(user.getId(), points, growth);
        }
    }
}
