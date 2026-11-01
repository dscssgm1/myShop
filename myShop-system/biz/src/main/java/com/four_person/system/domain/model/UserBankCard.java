package com.four_person.system.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user_bank_card")
public class UserBankCard {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String bankName;

    private String cardNo;

    /** 储蓄卡 / 信用卡 */
    private String cardType;

    private String holder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
