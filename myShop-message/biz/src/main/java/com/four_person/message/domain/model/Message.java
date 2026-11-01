package com.four_person.message.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("message")
public class Message {

    public static final String SENDER_BUYER = "BUYER";
    public static final String SENDER_CS = "CS";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    /** BUYER / CS */
    private String senderType;

    private String content;
    private LocalDateTime createdAt;
}
