package com.four_person.platform.interfaces.rest;

import com.four_person.common.api.PageResult;
import com.four_person.common.api.Result;
import com.four_person.platform.api.dto.PlatformDtos;
import com.four_person.platform.application.ShopAdminApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform/shops")
@RequiredArgsConstructor
public class ShopAdminController {

    private final ShopAdminApplicationService shopAdminApplicationService;

    /** 平台：店铺列表（含禁用店铺、最近操作原因） */
    @GetMapping
    public Result<PageResult<PlatformDtos.ShopVO>> page(@RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) Integer status,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "10") int size) {
        return Result.success(shopAdminApplicationService.page(keyword, status, page, size));
    }

    /** 平台：启用 / 停用店铺（reason 记录到店铺，供列表展示） */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @RequestParam Integer status,
                                     @RequestParam(required = false) String reason) {
        shopAdminApplicationService.updateStatus(id, status, reason);
        return Result.success("状态已更新", null);
    }

    // ---------------- 商家入驻审核 ----------------

    /** 平台：入驻申请分页（status=PENDING/APPROVED/REJECTED） */
    @GetMapping("/applies")
    public Result<PageResult<PlatformDtos.ShopApplyVO>> pageApplies(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(shopAdminApplicationService.pageApplies(status, keyword, page, size));
    }

    /** 平台：待审核申请数（菜单角标） */
    @GetMapping("/applies/pending-count")
    public Result<Long> pendingCount() {
        return Result.success(shopAdminApplicationService.pendingCount());
    }

    /** 平台：审核通过 → 自动建店并把申请人升级为商家 */
    @PutMapping("/applies/{id}/approve")
    public Result<PlatformDtos.ShopApplyVO> approve(@PathVariable Long id,
                                                    @RequestBody(required = false) PlatformDtos.ReviewRequest request) {
        return Result.success("入驻审核已通过",
                shopAdminApplicationService.approveApply(id, request == null ? null : request.getReason()));
    }

    /** 平台：驳回入驻申请（原因必填） */
    @PutMapping("/applies/{id}/reject")
    public Result<PlatformDtos.ShopApplyVO> reject(@PathVariable Long id,
                                                   @RequestBody(required = false) PlatformDtos.ReviewRequest request) {
        return Result.success("入驻申请已驳回",
                shopAdminApplicationService.rejectApply(id, request == null ? null : request.getReason()));
    }
}
