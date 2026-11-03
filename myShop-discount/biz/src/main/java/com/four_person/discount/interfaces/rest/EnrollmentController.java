package com.four_person.discount.controller;

import com.four_person.common.api.PageResult;
import com.four_person.common.api.Result;
import com.four_person.common.web.AccessGuard;
import com.four_person.common.web.UserContext;
import com.four_person.discount.api.dto.EnrollmentApplyRequest;
import com.four_person.discount.api.dto.EnrollmentReviewRequest;
import com.four_person.discount.api.dto.EnrollmentVO;
import com.four_person.discount.api.dto.PromotionVO;
import com.four_person.discount.application.EnrollmentApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 招商活动报名：商家挑商品报名 → 平台审核 → 通过的商品享受活动价。
 */
@RestController
@RequestMapping("/promotions/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentApplicationService enrollmentApplicationService;

    /** 商家可报名的平台活动 */
    @GetMapping("/available")
    public Result<List<PromotionVO>> available() {
        AccessGuard.requireGroup("MERCHANT");
        return Result.success(enrollmentApplicationService.available(UserContext.requireShopId()));
    }

    /** 商家批量报名商品 */
    @PostMapping
    public Result<Integer> apply(@Valid @RequestBody EnrollmentApplyRequest request) {
        AccessGuard.requireGroup("MERCHANT");
        return Result.success(enrollmentApplicationService.apply(
                UserContext.requireShopId(), request.getPromotionId(), request.getProductIds()));
    }

    /** 商家自己的报名记录 */
    @GetMapping("/mine")
    public Result<List<EnrollmentVO>> mine() {
        AccessGuard.requireGroup("MERCHANT");
        return Result.success(enrollmentApplicationService.mine(UserContext.requireShopId()));
    }

    /** 平台审核列表 */
    @GetMapping
    public Result<PageResult<EnrollmentVO>> page(@RequestParam(required = false) Long promotionId,
                                                 @RequestParam(required = false) Long shopId,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        AccessGuard.requireGroup("PLATFORM");
        return Result.success(enrollmentApplicationService.page(promotionId, shopId, status, page, size));
    }

    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        AccessGuard.requireGroup("PLATFORM");
        enrollmentApplicationService.approve(id);
        return Result.success();
    }

    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody(required = false) EnrollmentReviewRequest request) {
        AccessGuard.requireGroup("PLATFORM");
        enrollmentApplicationService.reject(id, request == null ? null : request.getReason());
        return Result.success();
    }
}
