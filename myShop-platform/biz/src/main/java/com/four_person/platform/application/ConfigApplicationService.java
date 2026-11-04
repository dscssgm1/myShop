package com.four_person.platform.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.four_person.common.web.AccessGuard;
import com.four_person.common.web.UserContext;
import com.four_person.platform.api.dto.PlatformDtos;
import com.four_person.platform.domain.model.ConfigItem;
import com.four_person.platform.infrastructure.persistence.mapper.ConfigItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConfigApplicationService {

    private final ConfigItemMapper configMapper;

    public List<PlatformDtos.ConfigItemVO> list(String group) {
        return configMapper.selectList(Wrappers.<ConfigItem>lambdaQuery()
                        .eq(ConfigItem::getConfigGroup, group)
                        .orderByAsc(ConfigItem::getId))
                .stream().map(this::toVO).toList();
    }

    /** 按 key 覆盖保存，未提供的 key 保持不变 */
    @Transactional
    public List<PlatformDtos.ConfigItemVO> save(String group, PlatformDtos.ConfigSaveRequest request) {
        AccessGuard.requirePerm("admin:config");
        if (request.getItems() == null) {
            return list(group);
        }
        for (PlatformDtos.ConfigItemVO item : request.getItems()) {
            ConfigItem existing = configMapper.selectOne(Wrappers.<ConfigItem>lambdaQuery()
                    .eq(ConfigItem::getConfigGroup, group)
                    .eq(ConfigItem::getConfigKey, item.getKey())
                    .last("LIMIT 1"));
            if (existing == null) {
                ConfigItem config = new ConfigItem();
                config.setConfigGroup(group);
                config.setConfigKey(item.getKey());
                config.setConfigValue(item.getValue());
                config.setDescription(item.getDescription());
                config.setUpdatedBy(UserContext.getUserId());
                configMapper.insert(config);
            } else {
                existing.setConfigValue(item.getValue());
                existing.setDescription(item.getDescription());
                existing.setUpdatedBy(UserContext.getUserId());
                configMapper.updateById(existing);
            }
        }
        return list(group);
    }

    /** 删除配置项（如配置中心中可增删的服务保障 key） */
    @Transactional
    public void deleteItem(String group, String key) {
        AccessGuard.requirePerm("admin:config:save");
        configMapper.delete(Wrappers.<ConfigItem>lambdaQuery()
                .eq(ConfigItem::getConfigGroup, group)
                .eq(ConfigItem::getConfigKey, key));
    }

    private PlatformDtos.ConfigItemVO toVO(ConfigItem item) {
        PlatformDtos.ConfigItemVO vo = new PlatformDtos.ConfigItemVO();
        vo.setId(item.getId());
        vo.setKey(item.getConfigKey());
        vo.setValue(item.getConfigValue());
        vo.setDescription(item.getDescription());
        return vo;
    }
}
