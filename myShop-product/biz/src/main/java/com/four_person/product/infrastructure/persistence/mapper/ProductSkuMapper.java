package com.four_person.product.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.four_person.product.domain.model.ProductSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductSkuMapper extends BaseMapper<ProductSku> {

    @Update("""
            UPDATE product_sku
               SET stock = stock - #{quantity},
                   sales = sales + #{quantity}
             WHERE id = #{skuId}
               AND deleted = 0
               AND status = 1
               AND stock >= #{quantity}
            """)
    int deductStock(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    @Update("""
            UPDATE product_sku
               SET stock = stock + #{quantity},
                   sales = GREATEST(sales - #{quantity}, 0)
             WHERE id = #{skuId}
               AND deleted = 0
            """)
    int restoreStock(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);
}
