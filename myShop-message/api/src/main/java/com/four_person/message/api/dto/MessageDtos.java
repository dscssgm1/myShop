package com.four_person.message.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class MessageDtos {

    private MessageDtos() {
    }

    @Data
    public static class SessionVO {
        private Long id;
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
    }

    @Data
    public static class MessageVO {
        private Long id;
        private String senderType;
        private String content;
        private LocalDateTime createdAt;
    }

    @Data
    public static class SessionDetailVO {
        private SessionVO session;
        private List<MessageVO> messages;
    }

    @Data
    public static class SendRequest {
        @NotBlank(message = "消息内容不能为空")
        private String content;
    }

    @Data
    public static class NoticeVO {
        private Long id;
        private String title;
        private String content;
        private Boolean read;
        private LocalDateTime createdAt;
    }

    @Data
    public static class NoticeRequest {
        private Long userId;
        private String title;
        private String content;
    }
}
