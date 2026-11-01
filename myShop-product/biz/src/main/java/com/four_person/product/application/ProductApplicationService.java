package com.four_person.product.application;

import com.four_person.common.api.PageResult;
import com.four_person.common.exception.BusinessException;
import com.four_person.product.api.dto.ProductQuery;
import com.four_person.product.api.dto.ProductRequest;
import com.four_person.product.api.dto.ProductVO;
import com.four_person.product.api.dto.StockChangeRequest;
import com.four_person.product.domain.model.Product;
import com.four_person.product.domain.model.ProductCategory;
import com.four_person.product.domain.model.ProductFilter;
import com.four_person.product.domain.repository.CategoryRepository;
import com.four_person.product.domain.repository.ProductRepository;
import com.four_person.product.enums.ProductErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductApplicationService {

    private static final Long DEFAULT_SHOP_ID = 1L;
    private static final String DEFAULT_SHOP_NAME = "数码旗舰店";
    private static final String DEFAULT_EMOJI = "🎁";
    private static final String DEFAULT_COLOR = "linear-gradient(135deg,#667eea,#764ba2)";

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public PageResult<ProductVO> page(ProductQuery query) {
        ProductFilter filter = new ProductFilter(
                query.getKeyword(),
                query.getCategoryId(),
                query.getMinPrice(),
                query.getMaxPrice(),
                query.getTag(),
                query.getSort(),
                query.getPage() == null ? 1 : query.getPage(),
                query.getSize() == null ? 8 : query.getSize());
        PageResult<Product> page = productRepository.page(filter);
        Map<Long, String> categoryNames = categoryNames();
        List<ProductVO> records = page.getList().stream()
                .map(product -> toVO(product, categoryNames))
                .toList();
        return PageResult.of(records, page.getTotal(), page.getPage(), page.getSize());
    }

    public ProductVO get(Long id) {
        return toVO(requireProduct(id), categoryNames());
    }

    public List<ProductVO> hot(int limit) {
        Map<Long, String> categoryNames = categoryNames();
        return productRepository.findHot(limit).stream()
                .map(product -> toVO(product, categoryNames))
                .toList();
    }

    public ProductVO create(ProductRequest request) {
        requireCategory(request.getCategoryId());
        Product product = new Product();
        applyRequest(product, request);
        product.setSales(0);
        product.setRating(BigDecimal.valueOf(5.0));
        productRepository.save(product);
        return get(product.getId());
    }

    public ProductVO update(Long id, ProductRequest request) {
        Product product = requireProduct(id);
        requireCategory(request.getCategoryId());
        applyRequest(product, request);
        productRepository.save(product);
        return get(id);
    }

    public void delete(Long id) {
        requireProduct(id);
        productRepository.deleteById(id);
    }

    public void restock(Long id, int quantity) {
        Product product = requireProduct(id);
        product.increaseStock(quantity);
        productRepository.save(product);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deductStock(List<StockChangeRequest.StockItem> items) {
        for (StockChangeRequest.StockItem item : items) {
            if (!productRepository.deductStock(item.getProductId(), item.getQuantity())) {
                Product product = productRepository.findById(item.getProductId()).orElse(null);
                String name = product == null ? "商品 " + item.getProductId() : product.getName();
                throw new BusinessException(ProductErrorCode.STOCK_INSUFFICIENT, name + " 库存不足");
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void restoreStock(List<StockChangeRequest.StockItem> items) {
        for (StockChangeRequest.StockItem item : items) {
            productRepository.restoreStock(item.getProductId(), item.getQuantity());
        }
    }

    public List<ProductVO> batch(List<Long> ids) {
        Map<Long, String> categoryNames = categoryNames();
        return productRepository.findByIds(ids).stream()
                .map(product -> toVO(product, categoryNames))
                .toList();
    }

    public List<ProductVO> candidates(List<Long> excludeIds, int limit) {
        Map<Long, String> categoryNames = categoryNames();
        return productRepository.findCandidates(excludeIds, limit).stream()
                .map(product -> toVO(product, categoryNames))
                .toList();
    }

    private Product requireProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }

    private void requireCategory(Long categoryId) {
        categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND));
    }

    private void applyRequest(Product product, ProductRequest request) {
        product.setName(request.getName().trim());
        product.setCategoryId(request.getCategoryId());
        product.setShopId(request.getShopId() == null ? DEFAULT_SHOP_ID : request.getShopId());
        product.setShopName(StringUtils.hasText(request.getShopName()) ? request.getShopName() : DEFAULT_SHOP_NAME);
        product.setPrice(request.getPrice());
        product.setOriginPrice(request.getOriginPrice() == null ? request.getPrice() : request.getOriginPrice());
        product.setStock(request.getStock());
        product.setEmoji(StringUtils.hasText(request.getEmoji()) ? request.getEmoji() : DEFAULT_EMOJI);
        product.setColor(StringUtils.hasText(request.getColor()) ? request.getColor() : DEFAULT_COLOR);
        product.setTags(request.getTags() == null ? "" : String.join(",", request.getTags()));
        product.setDescription(request.getDescription() == null ? "" : request.getDescription());
        product.setSpecs(request.getSpecs() == null ? "" : String.join("\n", request.getSpecs()));
        product.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }

    private Map<Long, String> categoryNames() {
        return categoryRepository.findAll().stream()
                .collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getName, (a, b) -> a));
    }

    private ProductVO toVO(Product product, Map<Long, String> categoryNames) {
        ProductVO vo = new ProductVO();
        vo.setId(product.getId());
        vo.setCategoryId(product.getCategoryId());
        vo.setCategoryName(categoryNames.getOrDefault(product.getCategoryId(), ""));
        vo.setShopId(product.getShopId());
        vo.setShopName(product.getShopName());
        vo.setName(product.getName());
        vo.setPrice(product.getPrice());
        vo.setOriginPrice(product.getOriginPrice());
        vo.setStock(product.getStock());
        vo.setSales(product.getSales());
        vo.setRating(product.getRating());
        vo.setEmoji(product.getEmoji());
        vo.setColor(product.getColor());
        vo.setTags(split(product.getTags()));
        vo.setDescription(product.getDescription());
        vo.setSpecs(splitLines(product.getSpecs()));
        vo.setStatus(product.getStatus());
        return vo;
    }

    private List<String> split(String value) {
        if (!StringUtils.hasText(value)) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<String> splitLines(String value) {
        if (!StringUtils.hasText(value)) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split("\\R"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
