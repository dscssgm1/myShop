package com.four_person.merchant.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.four_person.common.api.PageResult;
import com.four_person.common.exception.BusinessException;
import com.four_person.common.web.UserContext;
import com.four_person.merchant.api.dto.MerchantDtos;
import com.four_person.merchant.domain.model.Shop;
import com.four_person.merchant.domain.model.ShopApply;
import com.four_person.merchant.enums.MerchantErrorCode;
import com.four_person.merchant.infrastructure.persistence.mapper.ShopApplyMapper;
import com.four_person.merchant.infrastructure.persistence.mapper.ShopMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShopApplicationService {

    private final ShopMapper shopMapper;
    private final ShopApplyMapper shopApplyMapper;
    private final ObjectMapper objectMapper;

    public PageResult<MerchantDtos.ShopVO> page(String keyword, String category, BigDecimal minRating, int page, int size) {
        int pageNo = Math.max(page, 1);
        int pageSize = size < 1 ? 10 : Math.min(size, 100);

        LambdaQueryWrapper<Shop> wrapper = Wrappers.<Shop>lambdaQuery().eq(Shop::getStatus, 1);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Shop::getName, keyword.trim());
        }
        if (StringUtils.hasText(category)) {
            wrapper.like(Shop::getCategories, "\"" + category.trim() + "\"");
        }
        if (minRating != null) {
            wrapper.ge(Shop::getRating, minRating);
        }
        wrapper.orderByDesc(Shop::getSales);

        Page<Shop> result = shopMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(result.getRecords().stream().map(this::toVO).toList(),
                result.getTotal(), pageNo, pageSize);
    }

    public MerchantDtos.ShopVO detail(Long id) {
        Shop shop = shopMapper.selectById(id);
        if (shop == null) {
            throw new BusinessException(MerchantErrorCode.SHOP_NOT_FOUND);
        }
        return toVO(shop);
    }

    public MerchantDtos.ShopVO myShop() {
        return detail(UserContext.requireShopId());
    }

    public MerchantDtos.ShopVO updateMy(MerchantDtos.ShopRequest request) {
        Long shopId = UserContext.requireShopId();
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            throw new BusinessException(MerchantErrorCode.SHOP_NOT_FOUND);
        }
        shop.setName(request.getName());
        shop.setIcon(request.getIcon());
        shop.setColor(request.getColor());
        shop.setDescription(request.getDescription());
        shop.setContact(request.getContact());
        shop.setPhone(request.getPhone());
        shop.setRegion(request.getRegion());
        shop.setAddress(request.getAddress());
        shop.setCategories(toJson(request.getCategories()));
        applyLicense(shop, request.getLicenseNo(), request.getLicenseImage(), request.getLegalPerson(),
                request.getRegisteredAddress(), request.getBusinessScope(), request.getBusinessTerm());
        shopMapper.updateById(shop);
        return toVO(shop);
    }

    @Transactional
    public MerchantDtos.ApplyVO apply(MerchantDtos.ApplyRequest request) {
        Long userId = UserContext.getUserId();
        // 已是商家（账号绑定了店铺）无需重复申请
        if (UserContext.getShopId() != null) {
            throw new BusinessException(MerchantErrorCode.ALREADY_MERCHANT);
        }
        // 仅买家账号可开店，商家/物流/平台账号不参与
        if (!"user".equals(UserContext.getRole())) {
            throw new BusinessException(MerchantErrorCode.NOT_BUYER_ACCOUNT);
        }
        long pending = shopApplyMapper.selectCount(Wrappers.<ShopApply>lambdaQuery()
                .eq(ShopApply::getUserId, userId)
                .eq(ShopApply::getStatus, "PENDING"));
        if (pending > 0) {
            throw new BusinessException(MerchantErrorCode.APPLY_PENDING);
        }
        ShopApply apply = new ShopApply();
        apply.setUserId(userId);
        apply.setShopName(request.getShopName());
        apply.setCategories(toJson(request.getCategories()));
        apply.setContact(request.getContact());
        apply.setPhone(request.getPhone());
        apply.setRegion(request.getRegion());
        apply.setAddress(request.getAddress());
        apply.setDescription(request.getDescription());
        apply.setLicenseNo(trim(request.getLicenseNo()));
        apply.setLicenseImage(trim(request.getLicenseImage()));
        apply.setLegalPerson(trim(request.getLegalPerson()));
        apply.setRegisteredAddress(trim(request.getRegisteredAddress()));
        apply.setBusinessScope(trim(request.getBusinessScope()));
        apply.setBusinessTerm(trim(request.getBusinessTerm()));
        apply.setStatus("PENDING");
        shopApplyMapper.insert(apply);
        return toApplyVO(apply);
    }

    public List<MerchantDtos.ApplyVO> myApplies() {
        return shopApplyMapper.selectList(Wrappers.<ShopApply>lambdaQuery()
                        .eq(ShopApply::getUserId, UserContext.getUserId())
                        .orderByDesc(ShopApply::getId))
                .stream().map(this::toApplyVO).toList();
    }

    public MerchantDtos.ShopVO internalGet(Long id) {
        return detail(id);
    }

    private MerchantDtos.ShopVO toVO(Shop shop) {
        MerchantDtos.ShopVO vo = new MerchantDtos.ShopVO();
        vo.setId(shop.getId());
        vo.setName(shop.getName());
        vo.setIcon(shop.getIcon());
        vo.setColor(shop.getColor());
        vo.setDescription(shop.getDescription());
        vo.setContact(shop.getContact());
        vo.setPhone(shop.getPhone());
        vo.setRegion(shop.getRegion());
        vo.setAddress(shop.getAddress());
        vo.setCategories(parseCategories(shop.getCategories()));
        vo.setSales(shop.getSales());
        vo.setRating(shop.getRating());
        vo.setStatus(shop.getStatus());
        vo.setLicenseNo(shop.getLicenseNo());
        vo.setLicenseImage(shop.getLicenseImage());
        vo.setLegalPerson(shop.getLegalPerson());
        vo.setRegisteredAddress(shop.getRegisteredAddress());
        vo.setBusinessScope(shop.getBusinessScope());
        vo.setBusinessTerm(shop.getBusinessTerm());
        return vo;
    }

    private MerchantDtos.ApplyVO toApplyVO(ShopApply apply) {
        MerchantDtos.ApplyVO vo = new MerchantDtos.ApplyVO();
        vo.setId(apply.getId());
        vo.setShopName(apply.getShopName());
        vo.setCategories(parseCategories(apply.getCategories()));
        vo.setContact(apply.getContact());
        vo.setPhone(apply.getPhone());
        vo.setRegion(apply.getRegion());
        vo.setAddress(apply.getAddress());
        vo.setDescription(apply.getDescription());
        vo.setLicenseNo(apply.getLicenseNo());
        vo.setLicenseImage(apply.getLicenseImage());
        vo.setLegalPerson(apply.getLegalPerson());
        vo.setRegisteredAddress(apply.getRegisteredAddress());
        vo.setBusinessScope(apply.getBusinessScope());
        vo.setBusinessTerm(apply.getBusinessTerm());
        vo.setStatus(apply.getStatus());
        vo.setReason(apply.getReason());
        vo.setShopId(apply.getShopId());
        vo.setAuditedAt(apply.getAuditedAt());
        vo.setCreatedAt(apply.getCreatedAt());
        return vo;
    }

    /** 把资质字段写入店铺（空值统一为空串，避免列 NULL） */
    private void applyLicense(Shop shop, String licenseNo, String licenseImage, String legalPerson,
                              String registeredAddress, String businessScope, String businessTerm) {
        shop.setLicenseNo(trim(licenseNo));
        shop.setLicenseImage(trim(licenseImage));
        shop.setLegalPerson(trim(legalPerson));
        shop.setRegisteredAddress(trim(registeredAddress));
        shop.setBusinessScope(trim(businessScope));
        shop.setBusinessTerm(trim(businessTerm));
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private List<String> parseCategories(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }
}
