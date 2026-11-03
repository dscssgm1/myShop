package com.four_person.platform.infrastructure.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.four_person.platform.domain.model.ConfigItem;
import com.four_person.platform.infrastructure.persistence.mapper.ConfigItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * 配置中心默认值：仅在配置项缺失时写入，不覆盖平台已保存的值。
 * <p>
 * 老库升级后无需手工执行 SQL 即可在配置中心看到积分规则与系统参数。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformConfigInitializer implements ApplicationRunner {

    private final ConfigItemMapper configMapper;

    @Override
    public void run(ApplicationArguments args) {
        try {
            seed("points-rule", "earn-amount-per-point", "1", "每消费多少元累计 1 积分");
            seed("points-rule", "mall-rate", "1", "积分商城倍数：积分价 = 原价 × 倍数");
            seed("points-rule", "review-reward", "10", "发表评价奖励积分");

            seed("system", "platformName", "myShop 电商购物平台", "平台名称");
            seed("system", "servicePhone", "400-888-8888", "客服电话");
            seed("system", "serviceHours", "09:00-21:00", "客服服务时间");
            seed("system", "freeShippingThreshold", "99", "包邮门槛（元）");
            seed("system", "orderTimeoutMinutes", "30", "待支付订单自动取消时间（分钟）");

            seed("banner", "default",
                    "[{\"title\":\"双 11 全球狂欢节\",\"subtitle\":\"每满 300 减 50 · 上不封顶\","
                            + "\"color\":\"linear-gradient(90deg,#ff416c,#ff4b2b)\",\"link\":\"/products\"}]",
                    "首页 Banner");
            seed("pay-method", "alipay", "{\"name\":\"支付宝\",\"enabled\":true}", "支付宝");
            seed("pay-method", "wechat", "{\"name\":\"微信支付\",\"enabled\":true}", "微信支付");
            seed("pay-method", "card", "{\"name\":\"银行卡\",\"enabled\":true}", "银行卡");
            seed("aftersale-reason", "default",
                    "[\"不想要了\",\"商品质量问题\",\"商品与描述不符\",\"发错货/漏发\",\"其他原因\"]", "售后原因");
            seed("report-type", "default",
                    "[\"假货/仿冒\",\"违禁品\",\"侵权\",\"虚假宣传\",\"其他\"]", "举报类型");
            seed("sensitive-word", "default", "[\"假冒伪劣\",\"违禁品\",\"走私\"]", "敏感词");

            // 服务保障（商家发布商品时选择，商品通过 product_config_rel 关联 key）
            seed("service-guarantee", "seven-day-return", "{\"name\":\"七天无理由退货\",\"icon\":\"↩️\",\"enabled\":true}", "七天无理由退货");
            seed("service-guarantee", "authentic-guarantee", "{\"name\":\"正品保障\",\"icon\":\"✅\",\"enabled\":true}", "正品保障");
            seed("service-guarantee", "national-warranty", "{\"name\":\"全国联保\",\"icon\":\"🛡️\",\"enabled\":true}", "全国联保");
            seed("service-guarantee", "fast-delivery", "{\"name\":\"极速发货\",\"icon\":\"🚀\",\"enabled\":true}", "极速发货");

            // 原因类预设：审核驳回 / 售后拒绝 / 商品下架 / 店铺停用
            seed("audit-reject-reason", "default",
                    "[\"资料不完整\",\"资质不符合要求\",\"信息与实际不符\",\"图片或文案违规\",\"其他\"]", "审核驳回原因");
            seed("aftersale-reject-reason", "default",
                    "[\"凭证不足\",\"商品已使用影响二次销售\",\"超出售后期限\",\"商品无质量问题\",\"其他\"]", "售后拒绝原因");
            seed("off-shelf-reason", "default",
                    "[\"库存不足\",\"商品信息待完善\",\"换季/清仓临时下架\",\"违规整改\",\"其他\"]", "商品下架原因");
            seed("shop-disable-reason", "default",
                    "[\"售假/发布违禁商品\",\"服务质量差、投诉集中\",\"违规经营\",\"长时间未经营\",\"其他\"]", "店铺停用原因");
        } catch (DataAccessException ex) {
            log.warn("初始化运营配置失败（config_item 表可能尚未创建）：{}", ex.getMessage());
        }
    }

    private void seed(String group, String key, String value, String description) {
        Long exists = configMapper.selectCount(Wrappers.<ConfigItem>lambdaQuery()
                .eq(ConfigItem::getConfigGroup, group)
                .eq(ConfigItem::getConfigKey, key));
        if (exists != null && exists > 0) {
            return;
        }
        ConfigItem item = new ConfigItem();
        item.setConfigGroup(group);
        item.setConfigKey(key);
        item.setConfigValue(value);
        item.setDescription(description);
        configMapper.insert(item);
    }
}
