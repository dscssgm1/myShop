package com.four_person.product.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.four_person.product.domain.model.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 条件扣减库存，库存不足或商品不可售时返回 0。
     */
    @Update("""
            UPDATE product
               SET stock = stock - #{quantity},
                   sales = sales + #{quantity}
             WHERE id = #{productId}
               AND deleted = 0
               AND status = 1
               AND stock >= #{quantity}
            """)
    int deductStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * 取消订单时恢复库存与销量。
     */
    @Update("""
            UPDATE product
               SET stock = stock + #{quantity},
                   sales = GREATEST(sales - #{quantity}, 0)
             WHERE id = #{productId}
               AND deleted = 0
            """)
    int restoreStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}
