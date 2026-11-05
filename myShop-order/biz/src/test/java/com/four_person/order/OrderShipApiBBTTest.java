package com.four_person.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.four_person.logistics.api.dto.LogisticsDtos;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 发货权限黑盒测试（真实 MySQL + MockMvc 走完整 HTTP 栈）。
 * <p>
 * 发货是商家专属动作：
 * - 平台方（PLATFORM）无权发货——即便历史会话里还残留 admin:orders:ship 权限码也必须被拒；
 * - 商家只能发本店铺订单，他店订单同样被拒；
 * - 商家账号未绑定店铺时不能发货；
 * - 本店商家发货成功：订单转待收货并生成物流运单。
 */
class OrderShipApiBBTTest extends OrderApiBBTSupport {

    private static final long COMPANY_ID = 900801L;

    // ==================== 平台方无权发货 ====================

    @Test
    void platformCannotShipEvenWithStaleShipPerm() throws Exception {
        String orderNo = paidOrderOfShopA();

        // 平台账号：权限集里仍带着历史遗留的 admin:orders:ship，后端必须按所属端拦截
        JsonNode root = expectCode(post("/orders/" + orderNo + "/ship",
                body("companyId", COMPANY_ID), platform()), 403);
        assertThat(root.get("message").asText()).contains("没有权限");

        assertThat(statusOf(orderNo)).as("平台发货必须失败，订单仍待发货").isEqualTo("PAID");
        verify(logisticsInternalApi, never()).createShipment(any());
    }

    @Test
    void platformWithoutShipPermAlsoRejected() throws Exception {
        String orderNo = paidOrderOfShopA();

        expectCode(post("/orders/" + orderNo + "/ship",
                body("companyId", COMPANY_ID), platformWithoutShipPerm()), 403);

        assertThat(statusOf(orderNo)).isEqualTo("PAID");
        verify(logisticsInternalApi, never()).createShipment(any());
    }

    @Test
    void buyerCannotShipOwnOrder() throws Exception {
        String orderNo = paidOrderOfShopA();

        expectCode(post("/orders/" + orderNo + "/ship",
                body("companyId", COMPANY_ID), customer(USER_1)), 403);

        assertThat(statusOf(orderNo)).isEqualTo("PAID");
        verify(logisticsInternalApi, never()).createShipment(any());
    }

    // ==================== 商家只能发本店铺订单 ====================

    @Test
    void merchantOfOtherShopCannotShip() throws Exception {
        String orderNo = paidOrderOfShopA();

        // B 店商家想发 A 店的订单
        JsonNode root = expectCode(post("/orders/" + orderNo + "/ship",
                body("companyId", COMPANY_ID), merchantB()), 40301);
        assertThat(root.get("message").asText()).contains("只有订单所属商家可以发货");

        assertThat(statusOf(orderNo)).isEqualTo("PAID");
        verify(logisticsInternalApi, never()).createShipment(any());
    }

    @Test
    void merchantWithoutShopBindingCannotShip() throws Exception {
        String orderNo = paidOrderOfShopA();

        JsonNode root = expectCode(post("/orders/" + orderNo + "/ship",
                body("companyId", COMPANY_ID), merchantWithoutShop()), 403);
        assertThat(root.get("message").asText()).contains("未绑定店铺");

        assertThat(statusOf(orderNo)).isEqualTo("PAID");
        verify(logisticsInternalApi, never()).createShipment(any());
    }

    // ==================== 本店商家发货成功 ====================

    @Test
    void ownerMerchantCanShipOwnShopOrder() throws Exception {
        String orderNo = paidOrderOfShopA();

        expectCode(post("/orders/" + orderNo + "/ship",
                body("companyId", COMPANY_ID), merchantA()), 200);

        assertThat(statusOf(orderNo)).as("发货成功后订单转为待收货").isEqualTo("SHIPPED");
        assertThat(orderRow(orderNo).get("ship_time")).isNotNull();

        ArgumentCaptor<LogisticsDtos.ShipmentCreateRequest> captor =
                ArgumentCaptor.forClass(LogisticsDtos.ShipmentCreateRequest.class);
        verify(logisticsInternalApi, times(1)).createShipment(captor.capture());
        assertThat(captor.getValue().getOrderNo()).isEqualTo(orderNo);
        assertThat(captor.getValue().getShopId()).isEqualTo(SHOP_A);
        assertThat(captor.getValue().getCompanyId()).isEqualTo(COMPANY_ID);
    }

    @Test
    void shippingTwiceIsRejected() throws Exception {
        String orderNo = paidOrderOfShopA();

        expectCode(post("/orders/" + orderNo + "/ship",
                body("companyId", COMPANY_ID), merchantA()), 200);
        // 重复发货：订单状态已推进到待收货
        JsonNode root = expectCode(post("/orders/" + orderNo + "/ship",
                body("companyId", COMPANY_ID), merchantA()), 40010);
        assertThat(root.get("message").asText()).contains("只有待发货订单可以发货");

        verify(logisticsInternalApi, times(1)).createShipment(any());
    }

    // ==================== 辅助 ====================

    /** 下单 + 支付成功，返回 A 店的订单号 */
    private String paidOrderOfShopA() throws Exception {
        JsonNode data = createOrder(USER_1, List.of(orderItem(PRODUCT_A1, 1)), null);
        String orderNo = data.get("orderNo").asText();
        paySingle(orderNo);
        assertThat(statusOf(orderNo)).as("前置：订单应为待发货").isEqualTo("PAID");
        return orderNo;
    }
}
