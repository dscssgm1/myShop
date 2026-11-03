package com.four_person.product.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.four_person.common.exception.BusinessException;
import com.four_person.product.enums.ProductErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Long brandId;

    /** 主分类（大类） */
    private Long categoryId;

    private Long shopId;

    private String shopName;

    private BigDecimal price;

    /** 促销价，空则无折扣 */
    private BigDecimal discountPrice;

    private Integer stock;

    private Integer sales;

    private BigDecimal rating;

    private String emoji;

    private String color;

    /** 主图 URL，空则前端用 emoji 占位 */
    private String coverImage;

    /** 图集 URL，逗号分隔 */
    private String images;

    /** 逗号分隔的标签，例如：自营,秒杀 */
    private String tags;

    private String description;

    /** 商品详情（富文本 HTML） */
    private String detail;

    /** 商品规格定义（JSON 数组） */
    private String specs;

    /** 1 上架，0 下架 */
    private Integer status;

    /** PENDING / APPROVED / REJECTED */
    private String auditStatus;

    /** 最近一次审核驳回原因 */
    private String auditReason;

    /** 上架中商品的待审核修改（JSON，审核通过后应用，驳回即丢弃；空代表无待审核修改） */
    private String pendingData;

    /** 下架原因 */
    private String offReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    public boolean isOnSale() {
        return status != null && status == 1;
    }

    public boolean hasEnoughStock(int quantity) {
        return stock != null && stock >= quantity;
    }

    /**
     * 校验商品是否可售且库存充足，供下单/加购等场景使用。
     */
    public void ensureAvailable(int quantity) {
        if (!isOnSale()) {
            throw new BusinessException(ProductErrorCode.PRODUCT_OFF_SHELF, name + " 已下架");
        }
        if (!hasEnoughStock(quantity)) {
            throw new BusinessException(ProductErrorCode.STOCK_INSUFFICIENT, name + " 库存不足");
        }
    }

    /**
     * 补货：增加库存。
     */
    public void increaseStock(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ProductErrorCode.STOCK_INSUFFICIENT, "补货数量必须大于 0");
        }
        this.stock = (stock == null ? 0 : stock) + quantity;
    }

    /**
     * 内存中扣减库存，用于领域内校验与单元测试；实际落库由仓储的条件更新保证并发安全。
     */
    public void decreaseStock(int quantity) {
        ensureAvailable(quantity);
        this.stock -= quantity;
        this.sales = (sales == null ? 0 : sales) + quantity;
    }

    public void restoreStock(int quantity) {
        this.stock = (stock == null ? 0 : stock) + quantity;
        this.sales = Math.max((sales == null ? 0 : sales) - quantity, 0);
    }
}
