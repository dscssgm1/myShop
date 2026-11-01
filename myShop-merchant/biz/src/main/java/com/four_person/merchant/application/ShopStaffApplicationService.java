package com.four_person.merchant.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.four_person.common.exception.BusinessException;
import com.four_person.common.web.UserContext;
import com.four_person.merchant.api.dto.MerchantDtos;
import com.four_person.merchant.domain.model.ShopStaff;
import com.four_person.merchant.enums.MerchantErrorCode;
import com.four_person.merchant.infrastructure.persistence.mapper.ShopStaffMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShopStaffApplicationService {

    private final ShopStaffMapper staffMapper;

    public List<MerchantDtos.StaffVO> list() {
        Long shopId = UserContext.requireShopId();
        return staffMapper.selectList(Wrappers.<ShopStaff>lambdaQuery()
                        .eq(ShopStaff::getShopId, shopId)
                        .orderByAsc(ShopStaff::getId))
                .stream().map(this::toVO).toList();
    }

    public MerchantDtos.StaffVO create(MerchantDtos.StaffRequest request) {
        ShopStaff staff = new ShopStaff();
        staff.setShopId(UserContext.requireShopId());
        staff.setName(request.getName());
        staff.setPhone(request.getPhone());
        staff.setRoleCode(request.getRoleCode());
        staff.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        staffMapper.insert(staff);
        return toVO(staff);
    }

    public MerchantDtos.StaffVO update(Long id, MerchantDtos.StaffRequest request) {
        ShopStaff staff = require(id);
        staff.setName(request.getName());
        staff.setPhone(request.getPhone());
        staff.setRoleCode(request.getRoleCode());
        if (request.getStatus() != null) {
            staff.setStatus(request.getStatus());
        }
        staffMapper.updateById(staff);
        return toVO(staff);
    }

    public void delete(Long id) {
        require(id);
        staffMapper.deleteById(id);
    }

    private ShopStaff require(Long id) {
        ShopStaff staff = staffMapper.selectById(id);
        if (staff == null || !staff.getShopId().equals(UserContext.requireShopId())) {
            throw new BusinessException(MerchantErrorCode.STAFF_NOT_FOUND);
        }
        return staff;
    }

    private MerchantDtos.StaffVO toVO(ShopStaff staff) {
        MerchantDtos.StaffVO vo = new MerchantDtos.StaffVO();
        vo.setId(staff.getId());
        vo.setName(staff.getName());
        vo.setPhone(staff.getPhone());
        vo.setRoleCode(staff.getRoleCode());
        vo.setStatus(staff.getStatus());
        return vo;
    }
}
