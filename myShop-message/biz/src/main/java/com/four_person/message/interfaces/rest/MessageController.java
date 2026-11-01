package com.four_person.message.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.message.api.dto.MessageDtos;
import com.four_person.message.application.MessageApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageApplicationService messageApplicationService;

    // ---------------- 买家 ----------------

    @GetMapping("/sessions")
    public Result<List<MessageDtos.SessionVO>> mySessions() {
        return Result.success(messageApplicationService.buyerSessions());
    }

    @PostMapping("/sessions/shop/{shopId}")
    public Result<MessageDtos.SessionDetailVO> shopSession(@PathVariable Long shopId,
                                                           @RequestParam(required = false) String shopName,
                                                           @RequestParam(required = false) String shopIcon) {
        return Result.success(messageApplicationService.shopSession(shopId, shopName, shopIcon));
    }

    @PostMapping("/sessions/platform")
    public Result<MessageDtos.SessionDetailVO> platformSession() {
        return Result.success(messageApplicationService.platformSession());
    }

    @GetMapping("/sessions/{id}")
    public Result<MessageDtos.SessionDetailVO> detail(@PathVariable Long id) {
        return Result.success(messageApplicationService.detail(id));
    }

    @PostMapping("/sessions/{id}/messages")
    public Result<MessageDtos.MessageVO> send(@PathVariable Long id,
                                              @Valid @RequestBody MessageDtos.SendRequest request) {
        return Result.success(messageApplicationService.send(id, request.getContent()));
    }

    @PostMapping("/sessions/{id}/read")
    public Result<Void> read(@PathVariable Long id) {
        messageApplicationService.readByBuyer(id);
        return Result.success();
    }

    // ---------------- 客服 ----------------

    /** type=SHOP 店铺客服；type=PLATFORM 平台客服 */
    @GetMapping("/cs/sessions")
    public Result<List<MessageDtos.SessionVO>> csSessions(@RequestParam(required = false) String type,
                                                          @RequestParam(required = false) Long shopId) {
        return Result.success(messageApplicationService.csSessions(type, shopId));
    }

    @PostMapping("/cs/sessions/{id}/reply")
    public Result<MessageDtos.MessageVO> csReply(@PathVariable Long id,
                                                 @Valid @RequestBody MessageDtos.SendRequest request) {
        return Result.success(messageApplicationService.csReply(id, request.getContent()));
    }

    @PostMapping("/cs/sessions/{id}/read")
    public Result<Void> csRead(@PathVariable Long id) {
        messageApplicationService.readByCs(id);
        return Result.success();
    }

    // ---------------- 通知 ----------------

    @GetMapping("/notices")
    public Result<List<MessageDtos.NoticeVO>> notices() {
        return Result.success(messageApplicationService.notices());
    }

    @PutMapping("/notices/{id}/read")
    public Result<Void> readNotice(@PathVariable Long id) {
        messageApplicationService.readNotice(id);
        return Result.success();
    }
}
