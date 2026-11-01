package com.four_person.system.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.system.api.dto.ProfileDtos;
import com.four_person.system.application.AddressApplicationService;
import com.four_person.system.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressApplicationService addressApplicationService;

    @GetMapping
    public Result<List<ProfileDtos.AddressVO>> list() {
        return Result.success(addressApplicationService.list(SecurityUtils.currentUserId()));
    }

    @PostMapping
    public Result<ProfileDtos.AddressVO> create(@Valid @RequestBody ProfileDtos.AddressRequest request) {
        return Result.success("地址已添加", addressApplicationService.create(SecurityUtils.currentUserId(), request));
    }

    @PutMapping("/{id}")
    public Result<ProfileDtos.AddressVO> update(@PathVariable Long id,
                                                @Valid @RequestBody ProfileDtos.AddressRequest request) {
        return Result.success("地址已更新", addressApplicationService.update(SecurityUtils.currentUserId(), id, request));
    }

    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        addressApplicationService.setDefault(SecurityUtils.currentUserId(), id);
        return Result.success("已设为默认地址", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        addressApplicationService.delete(SecurityUtils.currentUserId(), id);
        return Result.success("地址已删除", null);
    }
}
