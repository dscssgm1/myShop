package com.four_person.discount.infrastructure.client;

import com.four_person.common.api.Results;
import com.four_person.discount.application.port.ProductQueryPort;
import com.four_person.discount.domain.model.Money;
import com.four_person.discount.domain.model.ProductSnapshot;
import com.four_person.product.api.ProductInternalApi;
import com.four_person.product.api.dto.ProductBatchRequest;
import com.four_person.product.api.dto.ProductVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProductQueryClientAdapter implements ProductQueryPort {

    private final ProductInternalApi productInternalApi;

    @Override
    public List<ProductSnapshot> batch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        ProductBatchRequest request = new ProductBatchRequest();
        request.setIds(ids);
        List<ProductVO> products = Results.data(productInternalApi.batch(request));
        return products == null ? List.of() : products.stream().map(this::toSnapshot).toList();
    }

    @Override
    public List<ProductSnapshot> candidates(int limit) {
        List<ProductVO> products = Results.data(productInternalApi.candidates(null, limit));
        return products == null ? List.of() : products.stream().map(this::toSnapshot).toList();
    }

    private ProductSnapshot toSnapshot(ProductVO product) {
        return new ProductSnapshot(
                product.getId(),
                product.getName(),
                Money.of(product.getPrice()),
                product.getCategoryId(),
                product.getCategoryName(),
                product.getShopId(),
                product.getShopName(),
                product.getEmoji(),
                product.getColor(),
                product.getTags(),
                product.getSales(),
                product.getRating());
    }
}
