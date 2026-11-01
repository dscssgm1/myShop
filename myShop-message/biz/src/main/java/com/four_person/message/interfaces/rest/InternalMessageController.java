package com.four_person.message.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.message.api.dto.MessageDtos;
import com.four_person.message.application.MessageApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部接口：其他服务推送系统通知。
 */
@RestController
@RequestMapping("/internal/messages")
@RequiredArgsConstructor
public class InternalMessageController {

    private final MessageApplicationService messageApplicationService;

    @PostMapping("/notices")
    public Result<Void> pushNotice(@RequestBody MessageDtos.NoticeRequest request) {
        messageApplicationService.pushNotice(request);
        return Result.success();
    }
}
