package com.four_person.message.api;

import com.four_person.common.api.Result;
import com.four_person.message.api.dto.MessageDtos;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 消息服务内部接口：其他服务推送系统通知。
 */
@FeignClient(name = "myshop-message", url = "${myshop.clients.message-url:}", path = "/internal/messages")
public interface MessageInternalApi {

    @PostMapping("/notices")
    Result<Void> pushNotice(@RequestBody MessageDtos.NoticeRequest request);
}
