package com.four_person.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.four_person.common.api.Result;
import com.four_person.discount.api.dto.CouponReleaseRequest;
import com.four_person.discount.api.dto.CouponUseRequest;
import com.four_person.discount.api.dto.DiscountResultVO;
import com.four_person.payment.api.dto.RefundRequest;
import com.four_person.payment.api.dto.RefundResultVO;
import com.four_person.product.api.dto.StockChangeRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 跨店合单（父订单/订单组）黑盒测试：走完整 HTTP 栈 + 真实 MySQL。
 * 覆盖黄金不变量：
 * - 只要还有一个子订单在享受券优惠，券就不能释放；整组取消才全额退回；
 * - 未支付整组取消 → 券自动退回（含单点取消未付子单升级为整组取消）；
 * - 部分已支付 → 只取消未付子单，券不退回；已付子单取消按分摊金额退款；
 * - 已付子单全部退款关闭后，券随组关闭一次性退回。
 */
class OrderGroupApiBBTTest extends OrderApiBBTSupport {

    // ==================== 创建：父订单与分摊 ====================

    @Test
    void crossShopCreateBuildsParentGroupWithAllocatedChildren() throws Exception {
        JsonNode data = createCrossShopGroup(USER_1);

        String groupNo = data.get("groupNo").asText();
        assertThat(groupNo).startsWith("MG");
        JsonNode orders = data.get("orders");
        assertThat(orders).hasSize(2);
        String orderA = orders.get(0).get("orderNo").asText();
        String orderB = orders.get(1).get("orderNo").asText();

        // 子单只存分摊后的数字（尾差给最后一个店铺 B）：A 25.00 / B 15.00，合计 = 组实付
        assertThat(dec(orders.get(0), "payAmount")).isEqualByComparingTo("25.00");
        assertThat(dec(orders.get(1), "payAmount")).isEqualByComparingTo("15.00");
        assertThat(dec(data, "payAmount")).isEqualByComparingTo("40.00");

        // 父订单（组）存分摊前总额，与子单之和严格相等
        Map<String, Object> group = groupRow(groupNo);
        assertThat(group.get("status")).isEqualTo("UNPAID");
        assertThat(((Number) group.get("shop_count")).intValue()).isEqualTo(2);
        assertThat(decOf(group, "original_amount")).isEqualByComparingTo("53.33");
        assertThat(decOf(group, "promotion_discount")).isEqualByComparingTo("3.33");
        assertThat(decOf(group, "coupon_discount")).isEqualByComparingTo("10.00");
        assertThat(decOf(group, "total_discount")).isEqualByComparingTo("13.33");
        assertThat(decOf(group, "pay_amount")).isEqualByComparingTo("40.00");
        assertThat(decOf(group, "merchant_bear_amount")).isEqualByComparingTo("4.00");
        assertThat(decOf(group, "platform_bear_amount")).isEqualByComparingTo("9.33");
        assertThat((String) group.get("coupon_use_order_no")).isEqualTo(orderA);

        // 子订单全部挂到组上，创建顺序 A → B
        assertThat(childOrderNos(groupNo)).containsExactly(orderA, orderB);
        assertThat(orderRow(orderA).get("group_no")).isEqualTo(groupNo);
        assertThat(orderRow(orderB).get("group_no")).isEqualTo(groupNo);

        // 券核销恰好一次，核销单号 = 组的 couponUseOrderNo（第一张子单）
        ArgumentCaptor<CouponUseRequest> useCaptor = ArgumentCaptor.forClass(CouponUseRequest.class);
        verify(discountInternalApi, times(1)).useCoupon(eq(COUPON_1), useCaptor.capture());
        assertThat(useCaptor.getValue().getOrderNo()).isEqualTo(orderA);
        assertThat(useCaptor.getValue().getUserId()).isEqualTo(USER_1);
    }

    @Test
    void singleShopCreateDoesNotBuildGroup() throws Exception {
        JsonNode data = createOrder(USER_1,
                List.of(orderItem(PRODUCT_A1, 1), orderItem(PRODUCT_A2, 2)), null);

        assertThat(data.get("groupNo").isNull()).isTrue();
        JsonNode orders = data.get("orders");
        assertThat(orders).hasSize(1);
        assertThat(dec(data, "payAmount")).isEqualByComparingTo("166.65"); // 33.33 + 66.66×2，无优惠
        Integer groups = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_group WHERE user_id = ?", Integer.class, USER_1);
        assertThat(groups).isZero();
    }

    @Test
    void previewSplitsAmountsPerShopForCrossShopCart() throws Exception {
        stubCrossShopDiscount();
        Map<String, Object> requestBody = body("items",
                List.of(orderItem(PRODUCT_A1, 1), orderItem(PRODUCT_B1, 1)));
        JsonNode data = okData(post("/orders/preview", requestBody, customer(USER_1)));

        assertThat(data.get("shops")).hasSize(2);
        assertThat(dec(data.get("shops").get(0), "payAmount")).isEqualByComparingTo("25.00");
        assertThat(dec(data.get("shops").get(1), "payAmount")).isEqualByComparingTo("15.00");
        assertThat(dec(data, "originalAmount")).isEqualByComparingTo("53.33");
        assertThat(dec(data, "couponDiscount")).isEqualByComparingTo("10.00");
        assertThat(dec(data, "payAmount")).isEqualByComparingTo("40.00");
    }

    // ==================== 结算页活动选择 / 逐商品优惠 ====================

    /** 行级明细齐全的算价返回：活动只命中 A 店（3.33），平台券 10 拆为 A 6.25 / B 3.75 */
    private void stubItemizedCrossShopDiscount() {
        DiscountResultVO discount = discountResult(new BigDecimal("53.33"), "3.33", "10.00",
                new BigDecimal("40.00"), "4.00", "9.33");
        discount.setAppliedPromotionId(700001L);
        discount.setAppliedPromotionTitle("BBT跨店满减");
        discount.setAppliedPromotionIds(List.of(700001L));
        DiscountResultVO.PromotionOption option = new DiscountResultVO.PromotionOption();
        option.setPromotionId(700001L);
        option.setTitle("BBT跨店满减");
        option.setShopId(0L);
        option.setScopeText("全品类");
        option.setRuleText("满 50 减 3.33");
        option.setDiscount(new BigDecimal("3.33"));
        option.setCouponStackable(true);
        option.setChosen(true);
        discount.setPromotionOptions(List.of(option));
        discount.setItemDiscounts(List.of(
                itemRow(PRODUCT_A1, "33.33", "3.33", "6.25"),
                itemRow(PRODUCT_B1, "20.00", "0.00", "3.75")));
        discount.setAppliedCouponId(COUPON_1);
        discount.setAppliedCouponTitle("BBT满减券");
        discount.setAppliedCoupons(List.of(usage(COUPON_1, "BBT满减券", "10.00")));
        doReturn(Result.success(discount)).when(discountInternalApi).calculate(any());
    }

    private DiscountResultVO.ItemDiscount itemRow(long productId, String original, String promo,
                                                   String coupon) {
        DiscountResultVO.ItemDiscount row = new DiscountResultVO.ItemDiscount();
        row.setProductId(productId);
        row.setOriginalSubtotal(new BigDecimal(original));
        row.setPromotionDiscount(new BigDecimal(promo));
        row.setCouponDiscount(new BigDecimal(coupon));
        row.setFinalSubtotal(new BigDecimal(original).subtract(new BigDecimal(promo))
                .subtract(new BigDecimal(coupon)));
        row.setPromotions(number(promo).signum() > 0
                ? List.of(appliedHit(700001L, "BBT跨店满减", promo)) : List.of());
        row.setCoupons(List.of(appliedHit(COUPON_1, "BBT满减券", coupon)));
        return row;
    }

    private DiscountResultVO.AppliedHit appliedHit(long id, String title, String amount) {
        DiscountResultVO.AppliedHit hit = new DiscountResultVO.AppliedHit();
        hit.setId(id);
        hit.setTitle(title);
        hit.setAmount(number(amount));
        return hit;
    }

    private BigDecimal number(String value) {
        return new BigDecimal(value);
    }

    @Test
    void previewExposesPromotionChoicesAndItemBreakdown() throws Exception {
        stubItemizedCrossShopDiscount();
        Map<String, Object> requestBody = body(
                "items", List.of(orderItem(PRODUCT_A1, 1), orderItem(PRODUCT_B1, 1)),
                "promotionIds", List.of(700001L));
        JsonNode data = okData(post("/orders/preview", requestBody, customer(USER_1)));

        // 活动选项与生效集合透传到预览
        assertThat(data.get("appliedPromotionIds").get(0).asLong()).isEqualTo(700001L);
        JsonNode option = data.get("promotionOptions").get(0);
        assertThat(option.get("promotionId").asLong()).isEqualTo(700001L);
        assertThat(option.get("chosen").asBoolean()).isTrue();
        assertThat(option.get("ruleText").asText()).isEqualTo("满 50 减 3.33");
        assertThat(dec(option, "discount")).isEqualByComparingTo("3.33");

        // 店铺级优惠取行级明细之和：活动只落 A 店，券按 6.25 / 3.75 拆
        JsonNode shopA = data.get("shops").get(0);
        JsonNode shopB = data.get("shops").get(1);
        assertThat(dec(shopA, "promotionDiscount")).isEqualByComparingTo("3.33");
        assertThat(dec(shopA, "couponDiscount")).isEqualByComparingTo("6.25");
        assertThat(dec(shopA, "payAmount")).isEqualByComparingTo("23.75");
        assertThat(dec(shopB, "promotionDiscount")).isEqualByComparingTo("0");
        assertThat(dec(shopB, "couponDiscount")).isEqualByComparingTo("3.75");
        assertThat(dec(shopB, "payAmount")).isEqualByComparingTo("16.25");

        // 逐商品：原价 → 优惠后，以及命中了哪些活动/券
        JsonNode itemA = data.get("items").get(0);
        assertThat(itemA.get("productId").asLong()).isEqualTo(PRODUCT_A1);
        assertThat(dec(itemA, "subtotal")).isEqualByComparingTo("33.33");
        assertThat(dec(itemA, "promotionDiscount")).isEqualByComparingTo("3.33");
        assertThat(dec(itemA, "couponDiscount")).isEqualByComparingTo("6.25");
        assertThat(dec(itemA, "finalSubtotal")).isEqualByComparingTo("23.75");
        assertThat(itemA.get("itemPromotions").get(0).get("title").asText()).isEqualTo("BBT跨店满减");
        assertThat(itemA.get("itemCoupons").get(0).get("title").asText()).isEqualTo("BBT满减券");
        JsonNode itemB = data.get("items").get(1);
        assertThat(itemB.get("itemPromotions")).isEmpty();
        assertThat(dec(itemB, "finalSubtotal")).isEqualByComparingTo("16.25");
        assertThat(dec(itemB.get("itemCoupons").get(0), "amount")).isEqualByComparingTo("3.75");
    }

    @Test
    void createPersistsPerItemDiscountSnapshots() throws Exception {
        stubItemizedCrossShopDiscount();
        JsonNode data = createOrder(USER_1,
                List.of(orderItem(PRODUCT_A1, 1), orderItem(PRODUCT_B1, 1)),
                List.of(COUPON_1), List.of(700001L));

        List<String> children = childOrderNos(data.get("groupNo").asText());
        Map<Long, Map<String, Object>> rows = new HashMap<>();
        for (String orderNo : children) {
            jdbcTemplate.queryForList(
                            "SELECT product_id, promotion_discount, coupon_discount FROM order_item WHERE order_no = ?",
                            orderNo)
                    .forEach(row -> rows.put(((Number) row.get("product_id")).longValue(), row));
        }
        // 每行下单时固化行级优惠快照，跨店明细互不串账
        assertThat(decOf(rows.get(PRODUCT_A1), "promotion_discount")).isEqualByComparingTo("3.33");
        assertThat(decOf(rows.get(PRODUCT_A1), "coupon_discount")).isEqualByComparingTo("6.25");
        assertThat(decOf(rows.get(PRODUCT_B1), "promotion_discount")).isEqualByComparingTo("0");
        assertThat(decOf(rows.get(PRODUCT_B1), "coupon_discount")).isEqualByComparingTo("3.75");
    }

    // ==================== 支付：整组一次付清 ====================

    @Test
    void groupPaidMarksAllChildrenAndParentPaid() throws Exception {
        JsonNode data = createCrossShopGroup(USER_1);
        String groupNo = data.get("groupNo").asText();
        List<String> children = childOrderNos(groupNo);

        payGroup(groupNo);

        assertThat(statusOf(children.get(0))).isEqualTo("PAID");
        assertThat(statusOf(children.get(1))).isEqualTo("PAID");
        Map<String, Object> group = groupRow(groupNo);
        assertThat(group.get("status")).isEqualTo("PAID");
        assertThat(group.get("pay_time")).isNotNull();
    }

    @Test
    void groupPaidDuplicateCallbackIsIdempotent() throws Exception {
        JsonNode data = createCrossShopGroup(USER_1);
        String groupNo = data.get("groupNo").asText();
        payGroup(groupNo);
        Object payTimeBefore = groupRow(groupNo).get("pay_time");

        payGroup(groupNo); // 重复回调：静默返回，不报错

        assertThat(groupRow(groupNo).get("status")).isEqualTo("PAID");
        assertThat(groupRow(groupNo).get("pay_time")).isEqualTo(payTimeBefore);
        childOrderNos(groupNo).forEach(orderNo ->
                assertThat(statusOf(orderNo)).isEqualTo("PAID"));
    }

    // ==================== 取消黄金不变量 ====================

    @Test
    void cancelUnpaidGroupReleasesCouponOnceWithCanonicalOrderNo() throws Exception {
        JsonNode data = createCrossShopGroup(USER_1);
        String groupNo = data.get("groupNo").asText();
        String orderA = childOrderNos(groupNo).get(0);

        expectCode(post("/orders/groups/" + groupNo + "/cancel", null, customer(USER_1)), 200);

        // 整组取消：所有子单 CANCELLED，组 CANCELLED
        childOrderNos(groupNo).forEach(orderNo ->
                assertThat(statusOf(orderNo)).as("订单 %s", orderNo).isEqualTo("CANCELLED"));
        Map<String, Object> group = groupRow(groupNo);
        assertThat(group.get("status")).isEqualTo("CANCELLED");
        assertThat(group.get("cancel_time")).isNotNull();

        // 券恰好退回一次，且使用组核销单号（canonical），非子单自身单号
        ArgumentCaptor<CouponReleaseRequest> releaseCaptor =
                ArgumentCaptor.forClass(CouponReleaseRequest.class);
        verify(discountInternalApi, times(1)).releaseCoupon(eq(COUPON_1), releaseCaptor.capture());
        assertThat(releaseCaptor.getValue().getOrderNo()).isEqualTo(orderA);

        // 库存按商品一次性聚合归还（A1×1 + B1×1），不是每单各调一次
        ArgumentCaptor<StockChangeRequest> stockCaptor = ArgumentCaptor.forClass(StockChangeRequest.class);
        verify(productInternalApi, times(1)).restoreStock(stockCaptor.capture());
        Map<Long, Integer> restored = new HashMap<>();
        stockCaptor.getValue().getItems()
                .forEach(item -> restored.merge(item.getProductId(), item.getQuantity(), Integer::sum));
        assertThat(restored).hasSize(2)
                .containsEntry(PRODUCT_A1, 1)
                .containsEntry(PRODUCT_B1, 1);
    }

    @Test
    void cancelUnpaidChildEscalatesToWholeGroupCancel() throws Exception {
        JsonNode data = createCrossShopGroup(USER_1);
        String groupNo = data.get("groupNo").asText();
        List<String> children = childOrderNos(groupNo);
        String orderA = children.get(0);
        String orderB = children.get(1);

        // 用户在订单列表只点了 B 店子单的取消 → 组内全部未付，升级为整组取消
        expectCode(post("/orders/" + orderB + "/cancel", null, customer(USER_1)), 200);

        assertThat(statusOf(orderA)).isEqualTo("CANCELLED");
        assertThat(statusOf(orderB)).isEqualTo("CANCELLED");
        assertThat(groupRow(groupNo).get("status")).isEqualTo("CANCELLED");
        ArgumentCaptor<CouponReleaseRequest> releaseCaptor =
                ArgumentCaptor.forClass(CouponReleaseRequest.class);
        verify(discountInternalApi, times(1)).releaseCoupon(eq(COUPON_1), releaseCaptor.capture());
        assertThat(releaseCaptor.getValue().getOrderNo()).isEqualTo(orderA);
    }

    @Test
    void cancelGroupWithMixedPaymentCancelsUnpaidOnlyAndKeepsCoupon() throws Exception {
        JsonNode data = createCrossShopGroup(USER_1);
        String groupNo = data.get("groupNo").asText();
        List<String> children = childOrderNos(groupNo);
        String orderA = children.get(0);
        String orderB = children.get(1);

        paySingle(orderA); // 构造混合态：A 已支付，B 未支付
        assertThat(statusOf(orderA)).isEqualTo("PAID");

        expectCode(post("/orders/groups/" + groupNo + "/cancel", null, customer(USER_1)), 200);

        // 只取消未付子单；已付子单保留优惠，组不关闭
        assertThat(statusOf(orderA)).isEqualTo("PAID");
        assertThat(statusOf(orderB)).isEqualTo("CANCELLED");
        assertThat(groupRow(groupNo).get("status")).isEqualTo("UNPAID");

        // 黄金不变量：组内还有已付子单在享券 → 券绝不退回
        verify(discountInternalApi, never()).releaseCoupon(anyLong(), any());
        // 库存只归还被取消的 B
        ArgumentCaptor<StockChangeRequest> stockCaptor = ArgumentCaptor.forClass(StockChangeRequest.class);
        verify(productInternalApi, times(1)).restoreStock(stockCaptor.capture());
        assertThat(stockCaptor.getValue().getItems()).hasSize(1);
        assertThat(stockCaptor.getValue().getItems().get(0).getProductId()).isEqualTo(PRODUCT_B1);
    }

    @Test
    void cancelUnpaidChildInMixedGroupCancelsSelfOnlyAndKeepsCoupon() throws Exception {
        JsonNode data = createCrossShopGroup(USER_1);
        String groupNo = data.get("groupNo").asText();
        List<String> children = childOrderNos(groupNo);
        String orderA = children.get(0);
        String orderB = children.get(1);

        paySingle(orderA);
        expectCode(post("/orders/" + orderB + "/cancel", null, customer(USER_1)), 200);

        assertThat(statusOf(orderB)).isEqualTo("CANCELLED");
        assertThat(statusOf(orderA)).isEqualTo("PAID");
        assertThat(groupRow(groupNo).get("status")).isEqualTo("UNPAID");
        verify(discountInternalApi, never()).releaseCoupon(anyLong(), any());
    }

    @Test
    void cancelPaidGroupChildRefundsAllocatedShareAndKeepsCoupon() throws Exception {
        JsonNode data = createCrossShopGroup(USER_1);
        String groupNo = data.get("groupNo").asText();
        List<String> children = childOrderNos(groupNo);
        String orderA = children.get(0);
        String orderB = children.get(1);

        payGroup(groupNo);
        expectCode(post("/orders/" + orderA + "/cancel", null, customer(USER_1)), 200);

        // 已付子单取消 → REFUNDED（未发货退款），组内其余子单不受影响
        assertThat(statusOf(orderA)).isEqualTo("REFUNDED");
        assertThat(statusOf(orderB)).isEqualTo("PAID");
        assertThat(groupRow(groupNo).get("status")).isEqualTo("PAID");

        // 退款按分摊后的实付金额（A 分摊 25.00），共用组支付单
        ArgumentCaptor<RefundRequest> refundCaptor = ArgumentCaptor.forClass(RefundRequest.class);
        verify(paymentInternalApi, times(1)).refund(refundCaptor.capture());
        RefundRequest refund = refundCaptor.getValue();
        assertThat(refund.getOrderNo()).isEqualTo(orderA);
        assertThat(refund.getGroupNo()).isEqualTo(groupNo);
        assertThat(refund.getAmount()).isEqualByComparingTo("25.00");

        // 黄金不变量：B 还在享券 → 不释放；已付取消也不回库存
        verify(discountInternalApi, never()).releaseCoupon(anyLong(), any());
        verify(productInternalApi, never()).restoreStock(any());
    }

    @Test
    void cancelLastPaidChildClosesGroupAndReleasesCoupon() throws Exception {
        JsonNode data = createCrossShopGroup(USER_1);
        String groupNo = data.get("groupNo").asText();
        List<String> children = childOrderNos(groupNo);
        String orderA = children.get(0);
        String orderB = children.get(1);

        payGroup(groupNo);
        expectCode(post("/orders/" + orderA + "/cancel", null, customer(USER_1)), 200);
        expectCode(post("/orders/" + orderB + "/cancel", null, customer(USER_1)), 200);

        // 最后一个已付子单退款后：全部子单 REFUNDED → 组 CLOSED，券一次性全额退回
        assertThat(statusOf(orderA)).isEqualTo("REFUNDED");
        assertThat(statusOf(orderB)).isEqualTo("REFUNDED");
        Map<String, Object> group = groupRow(groupNo);
        assertThat(group.get("status")).isEqualTo("CLOSED");
        assertThat(group.get("closed_time")).isNotNull();

        ArgumentCaptor<CouponReleaseRequest> releaseCaptor =
                ArgumentCaptor.forClass(CouponReleaseRequest.class);
        verify(discountInternalApi, times(1)).releaseCoupon(eq(COUPON_1), releaseCaptor.capture());
        assertThat(releaseCaptor.getValue().getOrderNo()).isEqualTo(orderA); // canonical 核销单号

        // 关闭后的组不可再取消
        expectCode(post("/orders/groups/" + groupNo + "/cancel", null, customer(USER_1)), 40033);
    }

    @Test
    void cancelGroupRejectsWhenAnyChildShipped() throws Exception {
        JsonNode data = createCrossShopGroup(USER_1);
        String groupNo = data.get("groupNo").asText();
        String orderA = childOrderNos(groupNo).get(0);
        payGroup(groupNo);
        jdbcTemplate.update("UPDATE order_info SET status = 'SHIPPED' WHERE order_no = ?", orderA);

        expectCode(post("/orders/groups/" + groupNo + "/cancel", null, customer(USER_1)), 40033);

        // 已发货订单存在时整组拒绝：其余子单状态不变
        assertThat(statusOf(orderA)).isEqualTo("SHIPPED");
        assertThat(statusOf(childOrderNos(groupNo).get(1))).isEqualTo("PAID");
    }

    @Test
    void cancelShippedGroupChildDirectlyRejected() throws Exception {
        JsonNode data = createCrossShopGroup(USER_1);
        String groupNo = data.get("groupNo").asText();
        String orderA = childOrderNos(groupNo).get(0);
        payGroup(groupNo);
        jdbcTemplate.update("UPDATE order_info SET status = 'SHIPPED' WHERE order_no = ?", orderA);

        expectCode(post("/orders/" + orderA + "/cancel", null, customer(USER_1)), 40010);
        assertThat(statusOf(orderA)).isEqualTo("SHIPPED");
    }

    @Test
    void duplicateCancelOfCancelledGroupChildRejected() throws Exception {
        JsonNode data = createCrossShopGroup(USER_1);
        String groupNo = data.get("groupNo").asText();
        String orderA = childOrderNos(groupNo).get(0);

        expectCode(post("/orders/groups/" + groupNo + "/cancel", null, customer(USER_1)), 200);
        // 再点已取消的子单取消 → 状态不允许
        expectCode(post("/orders/" + orderA + "/cancel", null, customer(USER_1)), 40010);
        // 再整组取消：已无待付款子单可取消
        expectCode(post("/orders/groups/" + groupNo + "/cancel", null, customer(USER_1)), 40033);
    }

    // ==================== 查询与鉴权 ====================

    @Test
    void groupDetailOwnershipEnforced() throws Exception {
        JsonNode data = createCrossShopGroup(USER_1);
        String groupNo = data.get("groupNo").asText();

        expectCode(get("/orders/groups/" + groupNo, customer(USER_2)), 40301);

        JsonNode detail = okData(get("/orders/groups/" + groupNo, customer(USER_1)));
        assertThat(detail.get("groupNo").asText()).isEqualTo(groupNo);
        assertThat(detail.get("status").asText()).isEqualTo("UNPAID");
        assertThat(dec(detail, "payAmount")).isEqualByComparingTo("40.00");
        JsonNode orders = detail.get("orders");
        assertThat(orders).hasSize(2);
        assertThat(orders.get(0).get("groupNo").asText()).isEqualTo(groupNo);
        assertThat(orders.get(0).get("items")).isNotEmpty();
    }

    @Test
    void groupNotFoundReturns40411() throws Exception {
        expectCode(get("/orders/groups/MG-NOT-EXIST", customer(USER_1)), 40411);
        expectCode(post("/orders/groups/MG-NOT-EXIST/cancel", null, customer(USER_1)), 40411);
    }

    // ==================== 退款失败与下单边界 ====================

    @Test
    void refundFailureBlocksPaidChildCancel() throws Exception {
        JsonNode data = createCrossShopGroup(USER_1);
        String groupNo = data.get("groupNo").asText();
        String orderA = childOrderNos(groupNo).get(0);
        payGroup(groupNo);

        doReturn(Result.success(RefundResultVO.failure("渠道退款失败")))
                .when(paymentInternalApi).refund(any());

        expectCode(post("/orders/" + orderA + "/cancel", null, customer(USER_1)), 40030);

        // 退款失败：订单保持 PAID，组不动，券不退
        assertThat(statusOf(orderA)).isEqualTo("PAID");
        assertThat(groupRow(groupNo).get("status")).isEqualTo("PAID");
        verify(discountInternalApi, never()).releaseCoupon(anyLong(), any());
    }

    @Test
    void createRejectsUnknownProduct() throws Exception {
        Map<String, Object> requestBody = body(
                "items", List.of(orderItem(999999L, 1)),
                "receiverName", "黑盒收件人",
                "receiverPhone", "13900000001",
                "receiverAddress", "黑盒市测试区收件路 2 号");
        expectCode(post("/orders", requestBody, customer(USER_1)), 40407);
    }

    @Test
    void createRejectsInsufficientStock() throws Exception {
        Map<String, Object> requestBody = body(
                "items", List.of(orderItem(PRODUCT_A1, 101)), // 库存 100
                "receiverName", "黑盒收件人",
                "receiverPhone", "13900000001",
                "receiverAddress", "黑盒市测试区收件路 2 号");
        expectCode(post("/orders", requestBody, customer(USER_1)), 40013);
    }

    // ==================== 下游失败回滚 ====================

    @Test
    void createRollsBackGroupWhenCouponUseFails() throws Exception {
        stubCrossShopDiscount();
        when(discountInternalApi.useCoupon(eq(COUPON_1), any()))
                .thenReturn(Result.error(50001, "优惠券已被使用"));

        expectCode(post("/orders", body(
                "items", List.of(orderItem(PRODUCT_A1, 1), orderItem(PRODUCT_B1, 1)),
                "couponIds", List.of(COUPON_1),
                "receiverName", "黑盒收件人",
                "receiverPhone", "13900000001",
                "receiverAddress", "黑盒市测试区收件路 2 号"), customer(USER_1)), 50001);

        // 核销失败：订单组、子订单全部回滚，已扣库存归还
        Integer groups = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_group WHERE user_id = ?", Integer.class, USER_1);
        Integer orders = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_info WHERE user_id = ?", Integer.class, USER_1);
        assertThat(groups).isZero();
        assertThat(orders).isZero();
        ArgumentCaptor<StockChangeRequest> stockCaptor = ArgumentCaptor.forClass(StockChangeRequest.class);
        verify(productInternalApi, times(1)).restoreStock(stockCaptor.capture());
        assertThat(stockCaptor.getValue().getItems()).hasSize(2);
    }
}
