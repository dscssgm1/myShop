package com.four_person.system.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_points_log")
public class PointsLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 正数增加、负数扣减 */
    private Integer changeAmount;

    /** 变动后的余额 */
    private Integer balance;

    /** EARN / SPEND / REFUND 等 */
    private String type;

    private String remark;

    private LocalDateTime createdAt;
}
