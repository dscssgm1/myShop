package com.four_person.merchant.interfaces.rest;

import com.four_person.common.api.Result;
import com.four_person.merchant.api.dto.MerchantDtos;
import com.four_person.merchant.application.ShopStaffApplicationService;
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
@RequestMapping("/merchant/staff")
@RequiredArgsConstructor
public class ShopStaffController {

    private final ShopStaffApplicationService staffApplicationService;

    @GetMapping
    public Result<List<MerchantDtos.StaffVO>> list() {
        return Result.success(staffApplicationService.list());
    }

    @PostMapping
    public Result<MerchantDtos.StaffVO> create(@Valid @RequestBody MerchantDtos.StaffRequest request) {
        return Result.success("员工已添加", staffApplicationService.create(request));
    }

    @PutMapping("/{id}")
    public Result<MerchantDtos.StaffVO> update(@PathVariable Long id,
                                               @Valid @RequestBody MerchantDtos.StaffRequest request) {
        return Result.success("员工已更新", staffApplicationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        staffApplicationService.delete(id);
        return Result.success("员工已删除", null);
    }
}
