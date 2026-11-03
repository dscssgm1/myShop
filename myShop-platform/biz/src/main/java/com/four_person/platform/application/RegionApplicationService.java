package com.four_person.platform.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.four_person.common.exception.BusinessException;
import com.four_person.platform.application.RegionDtos.TreeNodeVO;
import com.four_person.platform.domain.model.Region;
import com.four_person.platform.infrastructure.persistence.mapper.RegionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 行政区划：全国省/市/县三级种子数据，配置中心可增删改。
 * 新增节点的代码按父级前缀取未占用号段，保证主键稳定可读。
 */
@Service
@RequiredArgsConstructor
public class RegionApplicationService {

    private static final int MAX_LEVEL = 3;

    private final RegionMapper regionMapper;

    /** 完整树（配置中心管理用，含停用节点） */
    public List<TreeNodeVO> tree() {
        return buildTree(loadAll(), false);
    }

    /** 公开树（地址选择器用，仅启用节点） */
    public List<TreeNodeVO> publicTree() {
        return buildTree(loadAll(), true);
    }

    @Transactional
    public TreeNodeVO create(RegionDtos.RegionRequest request) {
        String name = requireName(request.getName());
        Long parentId = request.getParentId() == null ? 0L : request.getParentId();
        int level;
        long base;
        if (parentId == 0L) {
            level = 1;
            base = 830000L; // 省级代码形如 XX0000，真实区划用到 82，新增从 830000 起
        } else {
            Region parent = require(parentId);
            level = parent.getLevel() + 1;
            if (level > MAX_LEVEL) {
                throw new BusinessException(40001, "行政区划最多三级，不能再下挂子区域");
            }
            base = level == 2 ? parentId / 10000 * 10000 : parentId / 100 * 100;
        }
        long id = nextCode(base, level);
        if (id == 0) {
            throw new BusinessException(40903, "该层级号段已用尽，无法新增");
        }
        checkSiblingNameUnique(parentId, name, null);
        Region region = new Region();
        region.setId(id);
        region.setParentId(parentId);
        region.setName(name);
        region.setLevel(level);
        region.setStatus(1);
        regionMapper.insert(region);
        return toVO(region);
    }

    @Transactional
    public TreeNodeVO rename(Long id, RegionDtos.RegionRequest request) {
        Region region = require(id);
        String name = requireName(request.getName());
        checkSiblingNameUnique(region.getParentId(), name, id);
        region.setName(name);
        regionMapper.updateById(region);
        return toVO(region);
    }

    /** 停用后地址选择器不再展示该节点及其子树 */
    @Transactional
    public void updateStatus(Long id, Integer status) {
        Region region = require(id);
        region.setStatus(status == null || status != 0 ? 1 : 0);
        regionMapper.updateById(region);
    }

    @Transactional
    public void delete(Long id) {
        Region region = require(id);
        Long children = regionMapper.selectCount(Wrappers.<Region>lambdaQuery()
                .eq(Region::getParentId, region.getId()));
        if (children != null && children > 0) {
            throw new BusinessException(40001, "存在下级区域，请先删除下级");
        }
        regionMapper.deleteById(id);
    }

    // ==================== 内部逻辑 ====================

    private List<Region> loadAll() {
        return regionMapper.selectList(Wrappers.<Region>lambdaQuery().orderByAsc(Region::getId));
    }

    private List<TreeNodeVO> buildTree(List<Region> all, boolean enabledOnly) {
        Map<Long, TreeNodeVO> nodeMap = new LinkedHashMap<>();
        Map<Long, Long> parentMap = new LinkedHashMap<>();
        for (Region region : all) {
            parentMap.put(region.getId(), region.getParentId());
            if (!enabledOnly || region.getStatus() != 0) {
                nodeMap.put(region.getId(), toVO(region));
            }
        }
        List<TreeNodeVO> roots = new ArrayList<>();
        for (Map.Entry<Long, TreeNodeVO> entry : nodeMap.entrySet()) {
            TreeNodeVO parent = nodeMap.get(parentMap.get(entry.getKey()));
            if (parent == null) {
                roots.add(entry.getValue());
            } else {
                parent.getChildren().add(entry.getValue());
            }
        }
        return roots;
    }

    /** 在 base 号段下按层级步长找一个未占用的代码；0 表示无可用号段 */
    private long nextCode(long base, int level) {
        long step = level == 1 ? 10000L : level == 2 ? 100L : 1L;
        for (long i = 1; i <= 99; i++) {
            long candidate = base + i * step;
            if (regionMapper.selectById(candidate) == null) {
                return candidate;
            }
        }
        return 0;
    }

    private void checkSiblingNameUnique(Long parentId, String name, Long excludeId) {
        List<Region> siblings = regionMapper.selectList(Wrappers.<Region>lambdaQuery()
                .eq(Region::getParentId, parentId));
        for (Region sibling : siblings) {
            if (!Objects.equals(sibling.getId(), excludeId) && sibling.getName().equals(name)) {
                throw new BusinessException(40903, "同级已存在同名区域「" + name + "」");
            }
        }
    }

    private Region require(Long id) {
        Region region = regionMapper.selectById(id);
        if (region == null) {
            throw new BusinessException(40408, "区域不存在");
        }
        return region;
    }

    private String requireName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException(40001, "区域名称不能为空");
        }
        return name.trim();
    }

    private TreeNodeVO toVO(Region region) {
        TreeNodeVO vo = new TreeNodeVO();
        vo.setId(region.getId());
        vo.setName(region.getName());
        vo.setLevel(region.getLevel());
        vo.setStatus(region.getStatus());
        return vo;
    }
}
