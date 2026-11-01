package com.four_person.discount.application.port;

import com.four_person.discount.domain.model.ProductSnapshot;

import java.util.List;

/**
 * 商品查询端口，基础设施层用商品服务 Feign 契约实现。
 */
public interface ProductQueryPort {

    List<ProductSnapshot> batch(List<Long> ids);

    List<ProductSnapshot> candidates(int limit);
}
