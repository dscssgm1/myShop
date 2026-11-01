package com.four_person.product.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.four_person.common.api.PageResult;
import com.four_person.common.exception.BusinessException;
import com.four_person.product.api.dto.ProductQuery;
import com.four_person.product.api.dto.ProductRequest;
import com.four_person.product.api.dto.ProductVO;
import com.four_person.product.api.dto.StockChangeRequest;
import com.four_person.product.domain.model.Brand;
import com.four_person.product.domain.model.Product;
import com.four_person.product.domain.model.ProductCategory;
import com.four_person.product.domain.model.ProductFilter;
import com.four_person.product.domain.model.ProductSku;
import com.four_person.product.domain.repository.CategoryRepository;
import com.four_person.product.domain.repository.ProductRepository;
import com.four_person.product.enums.ProductErrorCode;
import com.four_person.product.infrastructure.persistence.mapper.BrandMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductApplicationService {

    private static final String DEFAULT_EMOJI = "🎁";
    private static final String DEFAULT_COLOR = "linear-gradient(135deg,#667eea,#764ba2)";
    private static final String AUDIT_PENDING = "PENDING";
    private static final String AUDIT_APPROVED = "APPROVED";
    private static final String AUDIT_REJECTED = "REJECTED";

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandMapper brandMapper;
    private final ObjectMapper objectMapper;

    public PageResult<ProductVO> page(ProductQuery query) {
        ProductFilter filter = new ProductFilter(
                query.getKeyword(), query.getCategoryId(), query.getSubCategoryId(), query.getBrandId(), query.getShopId(),
                query.getMinPrice(), query.getMaxPrice(), query.getTag(), query.getSort(),
                query.getPage() == null ? 1 : query.getPage(),
                query.getSize() == null ? 8 : query.getSize());
        PageResult<Product> page = productRepository.page(filter);
        Map<Long, String> categoryNames = categoryNames();
        Map<Long, String> brandNames = brandNames();
        List<ProductVO> records = page.getList().stream()
                .map(product -> toVO(product, categoryNames, brandNames))
                .toList();
        return PageResult.of(records, page.getTotal(), page.getPage(), page.getSize());
    }

    public ProductVO get(Long id) {
        return toVO(requireProduct(id), categoryNames(), brandNames());
    }

    public List<ProductVO> hot(int limit) {
        Map<Long, String> categoryNames = categoryNames();
        Map<Long, String> brandNames = brandNames();
        return productRepository.findHot(limit).stream()
                .map(product -> toVO(product, categoryNames, brandNames))
                .toList();
    }

    @Transactional
    public ProductVO create(ProductRequest request) {
        requireCategory(request.getCategoryId());
        Product product = new Product();
        applyRequest(product, request);
        product.setSales(0);
        product.setRating(BigDecimal.valueOf(5.0));
        // 商家发布默认待审核
        product.setAuditStatus(AUDIT_PENDING);
        product.setStatus(0);
        productRepository.save(product);

        saveRels(product.getId(), request);
        saveSkus(product.getId(), request);
        return get(product.getId());
    }

    @Transactional
    public ProductVO update(Long id, ProductRequest request) {
        Product product = requireProduct(id);
        requireCategory(request.getCategoryId());
        applyRequest(product, request);
        productRepository.save(product);

        saveRels(product.getId(), request);
        saveSkus(product.getId(), request);
        return get(id);
    }

    public void delete(Long id) {
        requireProduct(id);
        productRepository.deleteSkus(id);
        productRepository.deleteCategoryRels(id);
        productRepository.deleteById(id);
    }

    /** 补货（商品总库存） */
    public void restock(Long id, int quantity) {
        Product product = requireProduct(id);
        product.increaseStock(quantity);
        productRepository.save(product);
    }

    /** 设置促销价 */
    public ProductVO setDiscount(Long id, BigDecimal discountPrice) {
        Product product = requireProduct(id);
        if (discountPrice != null && discountPrice.compareTo(product.getPrice()) >= 0) {
            throw new BusinessException(40003, "促销价需低于售价");
        }
        product.setDiscountPrice(discountPrice);
        productRepository.save(product);
        return get(id);
    }

    /** 上架（需已通过审核） */
    public void onSale(Long id) {
        Product product = requireProduct(id);
        if (!AUDIT_APPROVED.equals(product.getAuditStatus())) {
            throw new BusinessException(ProductErrorCode.PRODUCT_NOT_APPROVED);
        }
        product.setStatus(1);
        product.setOffReason("");
        productRepository.save(product);
    }

    /** 下架 */
    public void offSale(Long id, String reason) {
        Product product = requireProduct(id);
        product.setStatus(0);
        product.setOffReason(reason == null ? "" : reason);
        productRepository.save(product);
    }

    /** 审核（平台） */
    public void audit(Long id, boolean approved) {
        Product product = requireProduct(id);
        product.setAuditStatus(approved ? AUDIT_APPROVED : AUDIT_REJECTED);
        if (!approved) {
            product.setStatus(0);
        }
        productRepository.save(product);
    }

    // ---------------- 内部接口 ----------------

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
        Map<Long, String> brandNames = brandNames();
        return productRepository.findByIds(ids).stream()
                .map(product -> toVO(product, categoryNames, brandNames))
                .toList();
    }

    public List<ProductVO> candidates(List<Long> excludeIds, int limit) {
        Map<Long, String> categoryNames = categoryNames();
        Map<Long, String> brandNames = brandNames();
        return productRepository.findCandidates(excludeIds, limit).stream()
                .map(product -> toVO(product, categoryNames, brandNames))
                .toList();
    }

    // ---------------- 私有方法 ----------------

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
        product.setBrandId(request.getBrandId());
        product.setCategoryId(request.getCategoryId());
        product.setShopId(request.getShopId() == null ? 0 : request.getShopId());
        product.setShopName(StringUtils.hasText(request.getShopName()) ? request.getShopName() : "");
        product.setPrice(request.getPrice());
        product.setOriginPrice(request.getOriginPrice() == null ? request.getPrice() : request.getOriginPrice());
        product.setDiscountPrice(request.getDiscountPrice());
        product.setStock(request.getStock());
        product.setEmoji(StringUtils.hasText(request.getEmoji()) ? request.getEmoji() : DEFAULT_EMOJI);
        product.setColor(StringUtils.hasText(request.getColor()) ? request.getColor() : DEFAULT_COLOR);
        product.setTags(request.getTags() == null ? "" : String.join(",", request.getTags()));
        product.setDescription(request.getDescription() == null ? "" : request.getDescription());
        product.setSpecs(writeJson(request.getSpecs()));
        if (request.getStatus() != null) {
            product.setStatus(request.getStatus());
        }
    }

    private void saveRels(Long productId, ProductRequest request) {
        productRepository.deleteCategoryRels(productId);
        List<Long> categoryIds = request.getCategoryIds() == null || request.getCategoryIds().isEmpty()
                ? List.of(request.getCategoryId())
                : request.getCategoryIds();
        // 去重
        List<Long> distinct = categoryIds.stream().distinct().toList();
        productRepository.saveCategoryRels(productId, distinct);
    }

    private void saveSkus(Long productId, ProductRequest request) {
        productRepository.deleteSkus(productId);
        if (request.getSkus() == null || request.getSkus().isEmpty()) {
            // 无规格商品生成一个默认 SKU
            ProductSku sku = new ProductSku();
            sku.setSpecs("{}");
            sku.setPrice(request.getPrice());
            sku.setOriginPrice(request.getOriginPrice() == null ? request.getPrice() : request.getOriginPrice());
            sku.setStock(request.getStock());
            sku.setSales(0);
            sku.setStatus(1);
            productRepository.saveSkus(productId, List.of(sku));
            return;
        }
        List<ProductSku> skus = request.getSkus().stream().map(item -> {
            ProductSku sku = new ProductSku();
            sku.setSpecs(writeJson(item.getSpecs()));
            sku.setPrice(item.getPrice());
            sku.setOriginPrice(item.getOriginPrice() == null ? item.getPrice() : item.getOriginPrice());
            sku.setStock(item.getStock() == null ? 0 : item.getStock());
            sku.setSales(0);
            sku.setStatus(1);
            return sku;
        }).toList();
        productRepository.saveSkus(productId, skus);
    }

    private Map<Long, String> categoryNames() {
        return categoryRepository.findAll().stream()
                .collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getName, (a, b) -> a));
    }

    private Map<Long, String> brandNames() {
        return brandMapper.selectList(null).stream()
                .collect(Collectors.toMap(Brand::getId, Brand::getName, (a, b) -> a));
    }

    private ProductVO toVO(Product product, Map<Long, String> categoryNames, Map<Long, String> brandNames) {
        ProductVO vo = new ProductVO();
        vo.setId(product.getId());
        vo.setName(product.getName());
        vo.setBrandId(product.getBrandId());
        vo.setBrandName(product.getBrandId() == null ? "" : brandNames.getOrDefault(product.getBrandId(), ""));
        vo.setCategoryId(product.getCategoryId());
        vo.setCategoryName(categoryNames.getOrDefault(product.getCategoryId(), ""));

        List<Long> categoryIds = productRepository.findCategoryIds(product.getId());
        vo.setCategoryIds(categoryIds);
        vo.setCategoryNames(categoryIds.stream()
                .map(id -> categoryNames.getOrDefault(id, ""))
                .filter(StringUtils::hasText)
                .toList());

        vo.setShopId(product.getShopId());
        vo.setShopName(product.getShopName());
        vo.setPrice(product.getPrice());
        vo.setOriginPrice(product.getOriginPrice());
        vo.setDiscountPrice(product.getDiscountPrice());
        vo.setStock(product.getStock());
        vo.setSales(product.getSales());
        vo.setRating(product.getRating());
        vo.setEmoji(product.getEmoji());
        vo.setColor(product.getColor());
        vo.setTags(split(product.getTags()));
        vo.setDescription(product.getDescription());
        vo.setSpecs(readSpecGroups(product.getSpecs()));
        vo.setSkus(productRepository.findSkus(product.getId()).stream().map(this::toSkuVO).toList());
        vo.setStatus(product.getStatus());
        vo.setAuditStatus(product.getAuditStatus());
        vo.setOffReason(product.getOffReason());
        return vo;
    }

    private ProductVO.SkuVO toSkuVO(ProductSku sku) {
        ProductVO.SkuVO vo = new ProductVO.SkuVO();
        vo.setId(sku.getId());
        vo.setSpecs(readMap(sku.getSpecs()));
        vo.setPrice(sku.getPrice());
        vo.setOriginPrice(sku.getOriginPrice());
        vo.setStock(sku.getStock());
        vo.setSales(sku.getSales());
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

    private String writeJson(Object value) {
        if (value == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private List<ProductVO.SpecGroupVO> readSpecGroups(String json) {
        if (!StringUtils.hasText(json) || "[]".equals(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ProductVO.SpecGroupVO>>() {
            });
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    private Map<String, String> readMap(String json) {
        if (!StringUtils.hasText(json) || "{}".equals(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
