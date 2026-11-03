package com.four_person.product.api.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductQuery {

    private String keyword;

    /** 大类 */
    private Long categoryId;

    /** 小类 */
    private Long subCategoryId;

    private Long brandId;

    /** 店铺 ID（店铺页） */
    private Long shopId;

    private BigDecimal minPrice;

    private BigDecimal maxPrice;

    private String tag;

    /** default / sales / priceAsc / priceDesc / random（random 用于首页推荐流，每次查询重新洗牌） */
    private String sort = "default";

    /** 排除的商品 ID（首页推荐流据此保证同一轮内不重复展示） */
    private List<Long> excludeIds;

    /** 平台管理端筛选：上架状态（1 上架 0 下架），不传则默认只看上架 */
    private Integer status;

    /** 平台管理端筛选：PENDING / APPROVED / REJECTED，不传则默认只看已通过 */
    private String auditStatus;

    /** 平台管理端筛选：true 时只看有待审核修改（pending_data 非空）的商品 */
    private Boolean pendingEdit;

    /** 商家管理端：true 时包含未上架/未审核商品，商家身份自动限定为本店 */
    private Boolean includeAll;

    private Integer page = 1;

    private Integer size = 8;
}
