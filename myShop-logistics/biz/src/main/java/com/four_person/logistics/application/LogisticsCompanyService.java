package com.four_person.logistics.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.four_person.common.exception.BusinessException;
import com.four_person.common.web.UserContext;
import com.four_person.logistics.api.dto.LogisticsDtos;
import com.four_person.logistics.domain.model.Courier;
import com.four_person.logistics.domain.model.LogisticsCompany;
import com.four_person.logistics.enums.LogisticsErrorCode;
import com.four_person.logistics.infrastructure.persistence.mapper.CourierMapper;
import com.four_person.logistics.infrastructure.persistence.mapper.LogisticsCompanyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LogisticsCompanyService {

    private final LogisticsCompanyMapper companyMapper;
    private final CourierMapper courierMapper;

    /** all=false 只返回已入驻公司（商家发货选择用） */
    public List<LogisticsDtos.CompanyVO> list(boolean all) {
        return companyMapper.selectList(Wrappers.<LogisticsCompany>lambdaQuery()
                        .eq(!all, LogisticsCompany::getStatus, 1)
                        .orderByAsc(LogisticsCompany::getId))
                .stream().map(this::toVO).toList();
    }

    public LogisticsDtos.CompanyVO create(LogisticsDtos.CompanyRequest request) {
        long exists = companyMapper.selectCount(Wrappers.<LogisticsCompany>lambdaQuery()
                .eq(LogisticsCompany::getCode, request.getCode().toUpperCase()));
        if (exists > 0) {
            throw new BusinessException(40902, "物流公司编码已存在");
        }
        LogisticsCompany company = new LogisticsCompany();
        company.setName(request.getName());
        company.setCode(request.getCode().toUpperCase());
        company.setContact(request.getContact());
        company.setPhone(request.getPhone());
        company.setColor(request.getColor() == null ? "#666" : request.getColor());
        company.setStatus(request.getStatus() == null ? 0 : request.getStatus());
        companyMapper.insert(company);
        return toVO(company);
    }

    public void updateStatus(Long id, Integer status) {
        LogisticsCompany company = companyMapper.selectById(id);
        if (company == null) {
            throw new BusinessException(LogisticsErrorCode.COMPANY_NOT_FOUND);
        }
        company.setStatus(status);
        companyMapper.updateById(company);
    }

    // ---------------- 快递员 ----------------

    public List<LogisticsDtos.CourierVO> couriers(Long companyId) {
        Long scope = companyId != null ? companyId : UserContext.getCompanyId();
        return courierMapper.selectList(Wrappers.<Courier>lambdaQuery()
                        .eq(scope != null, Courier::getCompanyId, scope)
                        .orderByAsc(Courier::getId))
                .stream().map(this::toCourierVO).toList();
    }

    public LogisticsDtos.CourierVO createCourier(LogisticsDtos.CourierRequest request) {
        Courier courier = new Courier();
        courier.setCompanyId(UserContext.requireCompanyId());
        courier.setName(request.getName());
        courier.setPhone(request.getPhone());
        courier.setRegion(request.getRegion());
        courier.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        courierMapper.insert(courier);
        return toCourierVO(courier);
    }

    public LogisticsDtos.CourierVO updateCourier(Long id, LogisticsDtos.CourierRequest request) {
        Courier courier = requireCourier(id);
        courier.setName(request.getName());
        courier.setPhone(request.getPhone());
        courier.setRegion(request.getRegion());
        if (request.getStatus() != null) {
            courier.setStatus(request.getStatus());
        }
        courierMapper.updateById(courier);
        return toCourierVO(courier);
    }

    public void deleteCourier(Long id) {
        requireCourier(id);
        courierMapper.deleteById(id);
    }

    private Courier requireCourier(Long id) {
        Courier courier = courierMapper.selectById(id);
        if (courier == null || !courier.getCompanyId().equals(UserContext.requireCompanyId())) {
            throw new BusinessException(LogisticsErrorCode.COURIER_NOT_FOUND);
        }
        return courier;
    }

    private LogisticsDtos.CompanyVO toVO(LogisticsCompany company) {
        LogisticsDtos.CompanyVO vo = new LogisticsDtos.CompanyVO();
        vo.setId(company.getId());
        vo.setName(company.getName());
        vo.setCode(company.getCode());
        vo.setContact(company.getContact());
        vo.setPhone(company.getPhone());
        vo.setColor(company.getColor());
        vo.setStatus(company.getStatus());
        return vo;
    }

    private LogisticsDtos.CourierVO toCourierVO(Courier courier) {
        LogisticsDtos.CourierVO vo = new LogisticsDtos.CourierVO();
        vo.setId(courier.getId());
        vo.setCompanyId(courier.getCompanyId());
        vo.setName(courier.getName());
        vo.setPhone(courier.getPhone());
        vo.setRegion(courier.getRegion());
        vo.setStatus(courier.getStatus());
        return vo;
    }
}
