package com.four_person.system.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.system.api.dto.ProfileDtos;
import com.four_person.system.application.BankCardApplicationService;
import com.four_person.system.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/cards")
@RequiredArgsConstructor
public class BankCardController {

    private final BankCardApplicationService bankCardApplicationService;

    @GetMapping
    public Result<List<ProfileDtos.BankCardVO>> list() {
        return Result.success(bankCardApplicationService.list(SecurityUtils.currentUserId()));
    }

    @PostMapping
    public Result<ProfileDtos.BankCardVO> create(@Valid @RequestBody ProfileDtos.BankCardRequest request) {
        return Result.success("银行卡已绑定", bankCardApplicationService.create(SecurityUtils.currentUserId(), request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        bankCardApplicationService.delete(SecurityUtils.currentUserId(), id);
        return Result.success("银行卡已解绑", null);
    }
}
