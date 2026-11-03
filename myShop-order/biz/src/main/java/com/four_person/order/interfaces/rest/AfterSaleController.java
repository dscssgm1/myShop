package com.four_person.order.interfaces.rest;

import com.four_person.common.api.PageResult;
import com.four_person.common.api.Result;
import com.four_person.common.web.AccessGuard;
import com.four_person.common.web.UserContext;
import com.four_person.order.api.dto.AfterSaleAppointmentRequest;
import com.four_person.order.api.dto.AfterSaleCreateRequest;
import com.four_person.order.api.dto.AfterSaleReviewRequest;
import com.four_person.order.api.dto.AfterSaleVO;
import com.four_person.order.application.AfterSaleApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 售后：用户申请、商家审核/退款/申诉、平台仲裁。
 */
@RestController
@RequestMapping("/orders/after-sales")
@RequiredArgsConstructor
public class AfterSaleController {

    private final AfterSaleApplicationService afterSaleApplicationService;

    /** 创建售后：用户本人发起，或平台/商家客服代用户发起（填写 userId） */
    @PostMapping
    public Result<AfterSaleVO> create(@Valid @RequestBody AfterSaleCreateRequest request) {
        return Result.success("售后申请已提交", afterSaleApplicationService.create(
                UserContext.getUserId(), UserContext.getGroup(), UserContext.getShopId(), request));
    }

    /** 我的售后列表 */
    @GetMapping
    public Result<PageResult<AfterSaleVO>> page(@RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return Result.success(afterSaleApplicationService.page(
                UserContext.getUserId(), status, page, size));
    }

    /** 售后详情：用户本人 / 本店商家 / 平台 */
    @GetMapping("/{afterSaleNo}")
    public Result<AfterSaleVO> detail(@PathVariable String afterSaleNo) {
        return Result.success(afterSaleApplicationService.detail(
                UserContext.getUserId(), UserContext.getGroup(), UserContext.getShopId(), afterSaleNo));
    }

    /** 用户申请平台介入 */
    @PostMapping("/{afterSaleNo}/escalate")
    public Result<AfterSaleVO> escalate(@PathVariable String afterSaleNo) {
        return Result.success("已提交平台审核", afterSaleApplicationService.escalate(
                UserContext.getUserId(), afterSaleNo));
    }

    /** 商家审核超时（前端倒计时结束）触发转平台；服务端会校验截止时间确实已过 */
    @PostMapping("/{afterSaleNo}/timeout-escalate")
    public Result<AfterSaleVO> escalateTimeout(@PathVariable String afterSaleNo) {
        return Result.success("审核已超时，已转交平台审核", afterSaleApplicationService.escalateTimeout(
                UserContext.getUserId(), UserContext.getGroup(), UserContext.getShopId(), afterSaleNo));
    }

    /** 用户预约上门取件时间（仅退货退款） */
    @PostMapping("/{afterSaleNo}/appointment")
    public Result<AfterSaleVO> appointment(@PathVariable String afterSaleNo,
                                           @Valid @RequestBody AfterSaleAppointmentRequest request) {
        return Result.success("预约成功", afterSaleApplicationService.appointment(
                UserContext.getUserId(), afterSaleNo, request.getAppointmentTime()));
    }

    // ---------------- 商家端 ----------------

    @GetMapping("/merchant")
    public Result<PageResult<AfterSaleVO>> merchantPage(@RequestParam(required = false) String status,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "10") int size) {
        AccessGuard.requireGroup("MERCHANT");
        return Result.success(afterSaleApplicationService.merchantPage(
                UserContext.requireShopId(), status, page, size));
    }

    /** 商家审核：同意/拒绝 */
    @PostMapping("/{afterSaleNo}/merchant-review")
    public Result<AfterSaleVO> merchantReview(@PathVariable String afterSaleNo,
                                              @Valid @RequestBody AfterSaleReviewRequest request) {
        AccessGuard.requireGroup("MERCHANT");
        return Result.success("审核完成", afterSaleApplicationService.merchantReview(
                UserContext.requireShopId(), afterSaleNo, request));
    }

    /** 商家触发退款（平台可代为操作） */
    @PostMapping("/{afterSaleNo}/refund")
    public Result<AfterSaleVO> refund(@PathVariable String afterSaleNo) {
        AccessGuard.requireGroup("MERCHANT", "PLATFORM");
        Long shopId = "MERCHANT".equals(UserContext.getGroup()) ? UserContext.requireShopId() : null;
        return Result.success("退款成功", afterSaleApplicationService.refund(shopId, afterSaleNo));
    }

    /** 商家退款后对退货不满足退款条件发起申诉 */
    @PostMapping("/{afterSaleNo}/appeal")
    public Result<AfterSaleVO> appeal(@PathVariable String afterSaleNo,
                                      @RequestBody(required = false) AfterSaleReviewRequest request) {
        AccessGuard.requireGroup("MERCHANT");
        return Result.success("申诉已提交", afterSaleApplicationService.appeal(
                UserContext.requireShopId(), afterSaleNo, request == null ? null : request.getReason()));
    }

    // ---------------- 平台端 ----------------

    @GetMapping("/platform")
    public Result<PageResult<AfterSaleVO>> platformPage(@RequestParam(required = false) String status,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "10") int size) {
        AccessGuard.requireGroup("PLATFORM");
        return Result.success(afterSaleApplicationService.platformPage(status, page, size));
    }

    /** 平台审核用户售后申请 */
    @PostMapping("/{afterSaleNo}/platform-review")
    public Result<AfterSaleVO> platformReview(@PathVariable String afterSaleNo,
                                              @Valid @RequestBody AfterSaleReviewRequest request) {
        AccessGuard.requireGroup("PLATFORM");
        return Result.success("审核完成", afterSaleApplicationService.platformReview(afterSaleNo, request));
    }

    /** 平台处理商家申诉 */
    @PostMapping("/{afterSaleNo}/appeal-review")
    public Result<AfterSaleVO> appealReview(@PathVariable String afterSaleNo,
                                            @Valid @RequestBody AfterSaleReviewRequest request) {
        AccessGuard.requireGroup("PLATFORM");
        return Result.success("申诉处理完成", afterSaleApplicationService.appealReview(afterSaleNo, request));
    }
}
