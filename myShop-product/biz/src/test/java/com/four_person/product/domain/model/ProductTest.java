package com.four_person.product.domain.model;

import com.four_person.common.exception.BusinessException;
import com.four_person.product.enums.ProductErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    @Test
    void shouldDeductStockAndIncreaseSales() {
        Product product = product(1, 5, 10);

        assertThatCode(() -> product.ensureAvailable(3)).doesNotThrowAnyException();
        product.decreaseStock(3);

        assertThat(product.getStock()).isEqualTo(2);
        assertThat(product.getSales()).isEqualTo(13);
    }

    @Test
    void shouldRejectWhenStockInsufficient() {
        Product product = product(1, 2, 0);

        assertThatThrownBy(() -> product.decreaseStock(3))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ProductErrorCode.STOCK_INSUFFICIENT.getCode());
    }

    @Test
    void shouldRejectOffShelfProduct() {
        Product product = product(0, 10, 0);

        assertThatThrownBy(() -> product.ensureAvailable(1))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ProductErrorCode.PRODUCT_OFF_SHELF.getCode());
    }

    @Test
    void shouldRestoreStockWithoutNegativeSales() {
        Product product = product(1, 2, 1);
        product.restoreStock(5);

        assertThat(product.getStock()).isEqualTo(7);
        assertThat(product.getSales()).isZero();
    }

    private Product product(int status, int stock, int sales) {
        Product product = new Product();
        product.setName("测试商品");
        product.setStatus(status);
        product.setStock(stock);
        product.setSales(sales);
        return product;
    }
}
