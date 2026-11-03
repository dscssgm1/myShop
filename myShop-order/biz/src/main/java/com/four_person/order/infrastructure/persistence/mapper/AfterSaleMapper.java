package com.four_person.order.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.four_person.order.domain.model.AfterSale;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AfterSaleMapper extends BaseMapper<AfterSale> {

    @Update("""
            UPDATE after_sale
               SET status = 'APPROVED', merchant_reason = #{reason}, reviewed_at = #{time}
             WHERE id = #{id} AND status = 'PENDING_MERCHANT' AND deleted = 0
            """)
    int merchantApprove(@Param("id") Long id, @Param("reason") String reason,
                        @Param("time") LocalDateTime time);

    @Update("""
            UPDATE after_sale
               SET status = 'MERCHANT_REJECTED', merchant_reason = #{reason}, reviewed_at = #{time}
             WHERE id = #{id} AND status = 'PENDING_MERCHANT' AND deleted = 0
            """)
    int merchantReject(@Param("id") Long id, @Param("reason") String reason,
                       @Param("time") LocalDateTime time);

    @Update("""
            UPDATE after_sale
               SET status = 'PENDING_PLATFORM'
             WHERE id = #{id} AND status = 'MERCHANT_REJECTED' AND deleted = 0
            """)
    int escalate(@Param("id") Long id);

    @Update("""
            UPDATE after_sale
               SET status = 'PENDING_PLATFORM', merchant_reason = '商家超时未审核，系统自动转平台处理'
             WHERE id = #{id} AND status = 'PENDING_MERCHANT' AND deleted = 0
               AND deadline_at IS NOT NULL AND deadline_at <= NOW()
            """)
    int autoEscalate(@Param("id") Long id);

    @Update("""
            UPDATE after_sale
               SET status = 'APPROVED', platform_reason = #{reason}, reviewed_at = #{time}
             WHERE id = #{id} AND status = 'PENDING_PLATFORM' AND deleted = 0
            """)
    int platformApprove(@Param("id") Long id, @Param("reason") String reason,
                        @Param("time") LocalDateTime time);

    @Update("""
            UPDATE after_sale
               SET status = 'PLATFORM_REJECTED', platform_reason = #{reason}, reviewed_at = #{time}
             WHERE id = #{id} AND status = 'PENDING_PLATFORM' AND deleted = 0
            """)
    int platformReject(@Param("id") Long id, @Param("reason") String reason,
                       @Param("time") LocalDateTime time);

    @Update("""
            UPDATE after_sale
               SET status = 'REFUNDED', refunded_at = #{time}
             WHERE id = #{id} AND status = 'APPROVED' AND deleted = 0
            """)
    int markRefunded(@Param("id") Long id, @Param("time") LocalDateTime time);

    @Update("""
            UPDATE after_sale
               SET status = 'APPEALING', platform_reason = #{reason}
             WHERE id = #{id} AND status = 'REFUNDED' AND type = 'RETURN_REFUND' AND deleted = 0
            """)
    int markAppealing(@Param("id") Long id, @Param("reason") String reason);

    @Update("""
            UPDATE after_sale
               SET status = 'APPEAL_APPROVED', platform_reason = #{reason},
                   recovery_order_no = #{recoveryOrderNo}, reviewed_at = #{time}
             WHERE id = #{id} AND status = 'APPEALING' AND deleted = 0
            """)
    int appealApprove(@Param("id") Long id, @Param("reason") String reason,
                      @Param("recoveryOrderNo") String recoveryOrderNo,
                      @Param("time") LocalDateTime time);

    @Update("""
            UPDATE after_sale
               SET status = 'REFUNDED', platform_reason = #{reason}, reviewed_at = #{time}
             WHERE id = #{id} AND status = 'APPEALING' AND deleted = 0
            """)
    int appealReject(@Param("id") Long id, @Param("reason") String reason,
                     @Param("time") LocalDateTime time);

    @Update("""
            UPDATE after_sale
               SET return_tracking_no = #{trackingNo}, pickup_code = #{pickupCode}
             WHERE id = #{id} AND status = 'APPROVED' AND deleted = 0
            """)
    int setReturnShipment(@Param("id") Long id, @Param("trackingNo") String trackingNo,
                          @Param("pickupCode") String pickupCode);

    @Update("""
            UPDATE after_sale
               SET appointment_time = #{time}
             WHERE id = #{id} AND type = 'RETURN_REFUND' AND deleted = 0
               AND status IN ('APPROVED', 'REFUNDED', 'APPEALING', 'APPEAL_APPROVED')
            """)
    int setAppointment(@Param("id") Long id, @Param("time") LocalDateTime time);
}
