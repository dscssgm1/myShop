package com.four_person.platform.application;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 区域契约：树节点供配置中心管理与地址选择器共用。
 */
public final class RegionDtos {

    private RegionDtos() {
    }

    @Data
    public static class TreeNodeVO {

        private Long id;
        private String name;
        private Integer level;
        /** 1 启用 0 停用（公开树不含停用节点） */
        private Integer status;
        private List<TreeNodeVO> children = new ArrayList<>();
    }

    @Data
    public static class RegionRequest {

        /** 父级代码，0 或空表示新增省级行政区 */
        private Long parentId;
        private String name;
        /** 1 启用 0 停用 */
        private Integer status;
    }
}
