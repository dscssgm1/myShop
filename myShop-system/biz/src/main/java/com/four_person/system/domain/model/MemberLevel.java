package com.four_person.system.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sys_member_level")
public class MemberLevel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 达到该等级所需成长值 */
    private Integer minGrowth;

    /** 积分倍率 */
    private BigDecimal pointsRate;

    /** 会员折扣，9.50 表示 9.5 折 */
    private BigDecimal discount;

    private Integer freeShipping;

    private Integer sort;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
