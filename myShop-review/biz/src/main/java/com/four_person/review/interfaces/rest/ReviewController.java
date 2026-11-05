package com.four_person.review.interfaces.rest;

import com.four_person.common.api.PageResult;
import com.four_person.common.api.Result;
import com.four_person.common.web.AccessGuard;
import com.four_person.common.web.UserContext;
import com.four_person.review.api.dto.ReviewDtos;
import com.four_person.review.application.ReviewApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewApplicationService reviewApplicationService;

    /** 提交评价（仅本人已完成订单中的商品） */
    @PostMapping
    public Result<ReviewDtos.ReviewVO> create(@Valid @RequestBody ReviewDtos.CreateRequest request) {
        return Result.success("评价已提交", reviewApplicationService.create(UserContext.getUserId(), request));
    }

    /** 商品评价列表（公开） */
    @GetMapping("/product/{productId}")
    public Result<PageResult<ReviewDtos.ReviewVO>> pageByProduct(@PathVariable Long productId,
                                                                 @RequestParam(defaultValue = "1") int page,
                                                                 @RequestParam(defaultValue = "10") int size) {
        return Result.success(reviewApplicationService.pageByProduct(productId, page, size));
    }

    /** 商品评分汇总（公开） */
    @GetMapping("/product/{productId}/summary")
    public Result<ReviewDtos.SummaryVO> summary(@PathVariable Long productId) {
        return Result.success(reviewApplicationService.summary(productId));
    }

    /** 订单中可评价的商品 */
    @GetMapping("/order/{orderNo}/reviewable")
    public Result<List<ReviewDtos.ReviewableVO>> reviewable(@PathVariable String orderNo) {
        return Result.success(reviewApplicationService.reviewable(UserContext.getUserId(), orderNo));
    }

    /** 我的评价 */
    @GetMapping("/mine")
    public Result<PageResult<ReviewDtos.ReviewVO>> mine(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "10") int size) {
        return Result.success(reviewApplicationService.mine(UserContext.getUserId(), page, size));
    }

    /** 买家删除自己的评价（扣回评价奖励积分，删除后不可再评价） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        reviewApplicationService.delete(UserContext.getUserId(), id);
        return Result.success("评价已删除", null);
    }

    /** 商家端：本店评价（含待审核、已隐藏） */
    @GetMapping("/merchant")
    public Result<PageResult<ReviewDtos.ReviewVO>> merchantPage(@RequestParam(defaultValue = "1") int page,
                                                                @RequestParam(defaultValue = "10") int size) {
        AccessGuard.requirePerm("merchant:reviews");
        return Result.success(reviewApplicationService.pageByShop(
                UserContext.requireShopId(), page, size));
    }

    /** 商家举报违规评价 */
    @PostMapping("/{id}/report")
    public Result<Void> report(@PathVariable Long id,
                               @Valid @RequestBody ReviewDtos.ReportRequest request) {
        AccessGuard.requirePerm("merchant:reviews:report");
        reviewApplicationService.report(UserContext.requireShopId(), UserContext.getUserId(),
                id, request.getReason());
        return Result.success("举报已提交，等待平台审核", null);
    }

    /** 商家回复本店评价 */
    @PutMapping("/{id}/reply")
    public Result<Void> reply(@PathVariable Long id,
                              @Valid @RequestBody ReviewDtos.ReplyRequest request) {
        AccessGuard.requirePerm("merchant:reviews:reply");
        reviewApplicationService.reply(UserContext.requireShopId(), id, request.getReply().trim());
        return Result.success("回复已发布", null);
    }

    /** 平台：评价审核列表 */
    @GetMapping("/admin")
    public Result<PageResult<ReviewDtos.ReviewVO>> adminPage(@RequestParam(required = false) String status,
                                                             @RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "10") int size) {
        AccessGuard.requirePerm("admin:audit:review");
        return Result.success(reviewApplicationService.adminPage(status, page, size));
    }

    /** 平台：评价审核统计 */
    @GetMapping("/admin/stats")
    public Result<ReviewDtos.AdminStatsVO> adminStats() {
        AccessGuard.requirePerm("admin:audit:review");
        return Result.success(reviewApplicationService.adminStats());
    }

    /** 平台：通过 / 隐藏评价 */
    @PutMapping("/admin/{id}")
    public Result<Void> moderate(@PathVariable Long id,
                                 @Valid @RequestBody ReviewDtos.ModerateRequest request) {
        AccessGuard.requirePerm("admin:audit:review");
        reviewApplicationService.moderate(UserContext.getUserId(), id,
                request.getAction(), request.getReason());
        return Result.success("审核完成", null);
    }
}
