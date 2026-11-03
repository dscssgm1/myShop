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
                query.getStatus(), query.getAuditStatus(), Boolean.TRUE.equals(query.getPendingEdit()),
                Boolean.TRUE.equals(query.getIncludeAll()), query.getExcludeIds(),
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
        saveServiceRels(product.getId(), request);
        saveSkus(product.getId(), request);
        return get(product.getId());
    }

    /**
     * 商家修改商品：任何修改都需平台重新审核。
     * 上架中的商品修改暂存为待审核草稿（pending_data），审核通过前买家仍看到原内容；
     * 未上架商品直接应用修改，仅审核状态回到待审核。
     */
    @Transactional
    public ProductVO update(Long id, ProductRequest request) {
        Product product = requireProduct(id);
        requireCategory(request.getCategoryId());
        if (product.isOnSale()) {
            product.setPendingData(writeRequestJson(request));
            productRepository.save(product);
            return get(id);
        }
        applyRequest(product, request);
        product.setAuditStatus(AUDIT_PENDING);
        productRepository.save(product);

        saveRels(product.getId(), request);
        saveServiceRels(product.getId(), request);
        saveSkus(product.getId(), request);
        return get(id);
    }

    public void delete(Long id) {
        requireProduct(id);
        productRepository.deleteSkus(id);
        productRepository.deleteCategoryRels(id);
        productRepository.deleteServiceRels(id);
        productRepository.deleteById(id);
    }

    /** 补货（商品总库存） */
    public void restock(Long id, int quantity) {
        Product product = requireProduct(id);
        product.increaseStock(quantity);
        productRepository.save(product);
    }

    /** 修改售价（直接生效，不走审核；多规格商品此价仅作展示基准，成交价按所选规格价） */
    public ProductVO setPrice(Long id, BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(40003, "售价需大于 0");
        }
        Product product = requireProduct(id);
        product.setPrice(price);
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

    /**
     * 审核（平台）：通过即自动上架；驳回必须说明原因。
     * 上架中商品的修改被驳回时丢弃草稿、保留原内容继续销售；
     * 新商品/未上架商品被驳回时标记 REJECTED，商家可据原因修改后重新提交。
     */
    /** 平台侧角标：待审核商品数 */
    public long countPendingAudit() {
        return productRepository.countPendingAudit();
    }

    public void audit(Long id, boolean approved, String reason) {
        Product product = requireProduct(id);
        if (!approved && !StringUtils.hasText(reason)) {
            throw new BusinessException(40003, "请填写驳回原因");
        }
        boolean hasPendingEdit = StringUtils.hasText(product.getPendingData());
        if (approved) {
            if (hasPendingEdit) {
                ProductRequest pending = readRequest(product.getPendingData());
                pending.setShopId(product.getShopId());
                pending.setShopName(product.getShopName());
                pending.setStatus(null);
                applyRequest(product, pending);
                saveRels(product.getId(), pending);
                saveServiceRels(product.getId(), pending);
                saveSkus(product.getId(), pending);
            }
            product.setAuditStatus(AUDIT_APPROVED);
            product.setStatus(1);
            product.setOffReason("");
            product.setAuditReason("");
            product.setPendingData(null);
            productRepository.save(product);
            if (hasPendingEdit) {
                productRepository.clearPendingEdit(product.getId());
            }
        } else if (hasPendingEdit) {
            productRepository.clearPendingEdit(product.getId());
            product.setPendingData(null);
            product.setAuditReason(reason.trim());
            productRepository.save(product);
        } else {
            product.setAuditStatus(AUDIT_REJECTED);
            product.setAuditReason(reason.trim());
            product.setStatus(0);
            productRepository.save(product);
        }
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
            // 有规格的商品同步扣减对应 SKU 库存（同一事务，失败一起回滚）
            if (item.getSkuId() != null && item.getSkuId() > 0
                    && !productRepository.deductSkuStock(item.getSkuId(), item.getQuantity())) {
                throw new BusinessException(ProductErrorCode.STOCK_INSUFFICIENT, "所选规格库存不足，请重新选择");
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void restoreStock(List<StockChangeRequest.StockItem> items) {
        for (StockChangeRequest.StockItem item : items) {
            productRepository.restoreStock(item.getProductId(), item.getQuantity());
            if (item.getSkuId() != null && item.getSkuId() > 0) {
                productRepository.restoreSkuStock(item.getSkuId(), item.getQuantity());
            }
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
        product.setDiscountPrice(request.getDiscountPrice());
        product.setStock(request.getStock());
        product.setEmoji(StringUtils.hasText(request.getEmoji()) ? request.getEmoji() : DEFAULT_EMOJI);
        product.setColor(StringUtils.hasText(request.getColor()) ? request.getColor() : DEFAULT_COLOR);
        product.setCoverImage(StringUtils.hasText(request.getCoverImage()) ? request.getCoverImage().trim() : "");
        product.setImages(request.getImages() == null ? ""
                : request.getImages().stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .collect(Collectors.joining(",")));
        product.setTags(request.getTags() == null ? "" : String.join(",", request.getTags()));
        product.setDetail(request.getDetail() == null ? "" : request.getDetail());
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

    /** 服务保障关联：key 去空白去重、限制长度与数量；传空即清空 */
    private void saveServiceRels(Long productId, ProductRequest request) {
        productRepository.deleteServiceRels(productId);
        if (request.getServiceKeys() == null) {
            return;
        }
        List<String> keys = request.getServiceKeys().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(key -> key.length() <= 64)
                .distinct()
                .limit(20)
                .toList();
        productRepository.saveServiceRels(productId, keys);
    }

    private void saveSkus(Long productId, ProductRequest request) {
        productRepository.deleteSkus(productId);
        if (request.getSkus() == null || request.getSkus().isEmpty()) {
            // 无规格商品生成一个默认 SKU
            ProductSku sku = new ProductSku();
            sku.setSpecs("{}");
            sku.setPrice(request.getPrice());
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
        vo.setDiscountPrice(product.getDiscountPrice());
        vo.setStock(product.getStock());
        vo.setSales(product.getSales());
        vo.setRating(product.getRating());
        vo.setEmoji(product.getEmoji());
        vo.setColor(product.getColor());
        vo.setCoverImage(product.getCoverImage());
        vo.setImages(split(product.getImages()));
        vo.setTags(split(product.getTags()));
        vo.setServiceKeys(productRepository.findServiceKeys(product.getId()));
        vo.setDescription(product.getDescription());
        vo.setDetail(product.getDetail());
        vo.setSpecs(readSpecGroups(product.getSpecs()));
        vo.setSkus(productRepository.findSkus(product.getId()).stream().map(this::toSkuVO).toList());
        vo.setStatus(product.getStatus());
        vo.setAuditStatus(product.getAuditStatus());
        vo.setAuditReason(product.getAuditReason());
        vo.setPendingData(readPending(product.getPendingData()));
        vo.setOffReason(product.getOffReason());
        return vo;
    }

    private ProductVO.SkuVO toSkuVO(ProductSku sku) {
        ProductVO.SkuVO vo = new ProductVO.SkuVO();
        vo.setId(sku.getId());
        vo.setSpecs(readMap(sku.getSpecs()));
        vo.setPrice(sku.getPrice());
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

    private String writeRequestJson(ProductRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception ex) {
            throw new BusinessException(40003, "商品修改信息序列化失败，请重试");
        }
    }

    private ProductRequest readRequest(String json) {
        try {
            return objectMapper.readValue(json, ProductRequest.class);
        } catch (Exception ex) {
            throw new BusinessException(40003, "待审核修改数据损坏，请驳回后由商家重新提交");
        }
    }

    /** 待审核修改草稿（JSON → Map 供前端对比展示；无草稿返回 null） */
    private Map<String, Object> readPending(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return null;
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
