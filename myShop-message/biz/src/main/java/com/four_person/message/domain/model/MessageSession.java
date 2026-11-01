package com.four_person.message.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("message_session")
public class MessageSession {

    public static final String TYPE_PLATFORM = "PLATFORM";
    public static final String TYPE_SHOP = "SHOP";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** PLATFORM / SHOP */
    private String type;

    private Long shopId;
    private String shopName;
    private String shopIcon;
    private Long customerId;
    private String customerName;
    private String lastContent;
    private LocalDateTime lastTime;
    private Integer buyerUnread;
    private Integer csUnread;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
