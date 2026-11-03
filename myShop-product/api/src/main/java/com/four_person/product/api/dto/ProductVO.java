package com.four_person.product.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ProductVO {

    private Long id;

    private String name;

    private Long brandId;

    private String brandName;

    private Long categoryId;

    private String categoryName;

    /** 多类目 */
    private List<Long> categoryIds;

    private List<String> categoryNames;

    private Long shopId;

    private String shopName;

    private BigDecimal price;

    private BigDecimal discountPrice;

    private Integer stock;

    private Integer sales;

    private BigDecimal rating;

    private String emoji;

    private String color;

    /** 主图 URL */
    private String coverImage;

    /** 图集 URL 列表 */
    private List<String> images;

    private List<String> tags;

    /** 服务保障 key 列表（名称/图标由前端经配置中心解析） */
    private List<String> serviceKeys;

    private String description;

    /** 商品详情（富文本 HTML） */
    private String detail;

    /** 规格定义 */
    private List<SpecGroupVO> specs;

    /** SKU 列表 */
    private List<SkuVO> skus;

    private Integer status;

    private String auditStatus;

    /** 最近一次审核驳回原因（驳回后商家据此修改重新提交） */
    private String auditReason;

    /** 上架中商品的待审核修改（Map 结构同 ProductRequest；空代表无待审核修改） */
    private Map<String, Object> pendingData;

    private String offReason;

    @Data
    public static class SpecGroupVO {
        private String name;
        private List<String> options;
    }

    @Data
    public static class SkuVO {
        private Long id;
        private Map<String, String> specs;
        private BigDecimal price;
        private Integer stock;
        private Integer sales;
    }
}
