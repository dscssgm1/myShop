package com.four_person.logistics.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.four_person.common.api.PageResult;
import com.four_person.common.exception.BusinessException;
import com.four_person.common.web.UserContext;
import com.four_person.logistics.api.dto.LogisticsDtos;
import com.four_person.logistics.domain.model.Courier;
import com.four_person.logistics.domain.model.LogisticsCompany;
import com.four_person.logistics.domain.model.Shipment;
import com.four_person.logistics.domain.model.ShipmentTrace;
import com.four_person.logistics.enums.LogisticsErrorCode;
import com.four_person.logistics.infrastructure.persistence.mapper.CourierMapper;
import com.four_person.logistics.infrastructure.persistence.mapper.LogisticsCompanyMapper;
import com.four_person.logistics.infrastructure.persistence.mapper.ShipmentMapper;
import com.four_person.logistics.infrastructure.persistence.mapper.ShipmentTraceMapper;
import com.four_person.logistics.infrastructure.support.RegionParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class ShipmentApplicationService {

    private static final Map<String, String> STATUS_TEXT = Map.of(
            Shipment.WAITING_PICKUP, "待揽收",
            Shipment.PICKED, "已揽收",
            Shipment.IN_TRANSIT, "运输中",
            Shipment.DELIVERING, "派送中",
            Shipment.SIGNED, "已签收");

    private final ShipmentMapper shipmentMapper;
    private final ShipmentTraceMapper traceMapper;
    private final LogisticsCompanyMapper companyMapper;
    private final CourierMapper courierMapper;

    /** 商家发货：创建运单 */
    @Transactional
    public LogisticsDtos.ShipmentVO create(LogisticsDtos.ShipmentCreateRequest request) {
        LogisticsCompany company = companyMapper.selectById(request.getCompanyId());
        if (company == null) {
            throw new BusinessException(LogisticsErrorCode.COMPANY_NOT_FOUND);
        }
        if (company.getStatus() == null || company.getStatus() != 1) {
            throw new BusinessException(LogisticsErrorCode.COMPANY_NOT_ACTIVE);
        }

        Shipment shipment = new Shipment();
        shipment.setTrackingNo(company.getCode() + System.currentTimeMillis()
                + ThreadLocalRandom.current().nextInt(10, 99));
        shipment.setOrderNo(request.getOrderNo());
        shipment.setShopId(request.getShopId());
        shipment.setShopName(request.getShopName());
        shipment.setPickupAddress(request.getPickupAddress());
        shipment.setPickupRegion(request.getPickupRegion());
        shipment.setCompanyId(company.getId());
        shipment.setCompanyName(company.getName());
        shipment.setReceiverName(request.getReceiverName());
        shipment.setReceiverPhone(request.getReceiverPhone());
        shipment.setReceiverAddress(request.getReceiverAddress());
        shipment.setReceiverRegion(RegionParser.parse(request.getReceiverAddress()));
        shipment.setItemsText(request.getItemsText());
        shipment.setStatus(Shipment.WAITING_PICKUP);
        shipmentMapper.insert(shipment);

        appendTrace(shipment.getId(), "商家已发货，等待快递员揽收");
        return detail(shipment.getId());
    }

    public PageResult<LogisticsDtos.ShipmentVO> page(Long companyId, String status, String region,
                                                    int page, int size) {
        int pageNo = Math.max(page, 1);
        int pageSize = size < 1 ? 10 : Math.min(size, 100);

        LambdaQueryWrapper<Shipment> wrapper = Wrappers.<Shipment>lambdaQuery()
                .eq(companyId != null, Shipment::getCompanyId, companyId)
                .eq(StringUtils.hasText(status), Shipment::getStatus, status);
        if (StringUtils.hasText(region)) {
            wrapper.and(w -> w.eq(Shipment::getPickupRegion, region)
                    .or().eq(Shipment::getReceiverRegion, region));
        }
        wrapper.orderByDesc(Shipment::getId);

        Page<Shipment> result = shipmentMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(result.getRecords().stream().map(this::toVO).toList(),
                result.getTotal(), pageNo, pageSize);
    }

    public LogisticsDtos.ShipmentVO detail(Long id) {
        Shipment shipment = shipmentMapper.selectById(id);
        if (shipment == null) {
            throw new BusinessException(LogisticsErrorCode.SHIPMENT_NOT_FOUND);
        }
        return toVO(shipment);
    }

    public LogisticsDtos.ShipmentVO byOrderNo(String orderNo) {
        Shipment shipment = shipmentMapper.selectOne(
                Wrappers.<Shipment>lambdaQuery().eq(Shipment::getOrderNo, orderNo).last("LIMIT 1"));
        return shipment == null ? null : toVO(shipment);
    }

    /** 快递员的任务：揽收按店铺区域，派送按收货区域 */
    public List<LogisticsDtos.ShipmentVO> courierTasks(Long courierId, String type) {
        Courier courier = courierMapper.selectById(courierId);
        if (courier == null) {
            throw new BusinessException(LogisticsErrorCode.COURIER_NOT_FOUND);
        }
        LambdaQueryWrapper<Shipment> wrapper = Wrappers.<Shipment>lambdaQuery()
                .eq(Shipment::getCompanyId, courier.getCompanyId());
        if ("pickup".equalsIgnoreCase(type)) {
            wrapper.eq(Shipment::getStatus, Shipment.WAITING_PICKUP)
                    .eq(Shipment::getPickupRegion, courier.getRegion());
        } else {
            wrapper.in(Shipment::getStatus, Shipment.IN_TRANSIT, Shipment.DELIVERING)
                    .eq(Shipment::getReceiverRegion, courier.getRegion());
        }
        wrapper.orderByAsc(Shipment::getId);
        return shipmentMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Transactional
    public LogisticsDtos.ShipmentVO pickup(Long id, Long courierId) {
        Shipment shipment = require(id);
        if (!Shipment.WAITING_PICKUP.equals(shipment.getStatus())) {
            throw new BusinessException(LogisticsErrorCode.ILLEGAL_STATUS);
        }
        Courier courier = courierId == null ? null : courierMapper.selectById(courierId);
        if (courier != null) {
            shipment.setCourierId(courier.getId());
            shipment.setCourierName(courier.getName());
        }
        shipment.setStatus(Shipment.PICKED);
        shipmentMapper.updateById(shipment);
        String courierLabel = StringUtils.hasText(shipment.getCourierName())
                ? shipment.getCourierName() : "快递员";
        appendTrace(id, "快件已被【" + courierLabel + "】揽收，运单号 " + shipment.getTrackingNo());
        return detail(id);
    }

    @Transactional
    public LogisticsDtos.ShipmentVO transit(Long id, String content) {
        Shipment shipment = require(id);
        if (!Shipment.PICKED.equals(shipment.getStatus()) && !Shipment.IN_TRANSIT.equals(shipment.getStatus())) {
            throw new BusinessException(LogisticsErrorCode.ILLEGAL_STATUS);
        }
        shipment.setStatus(Shipment.IN_TRANSIT);
        shipmentMapper.updateById(shipment);
        appendTrace(id, StringUtils.hasText(content) ? content : "快件运输中");
        return detail(id);
    }

    @Transactional
    public LogisticsDtos.ShipmentVO outForDelivery(Long id, Long courierId) {
        Shipment shipment = require(id);
        if (!Shipment.IN_TRANSIT.equals(shipment.getStatus())) {
            throw new BusinessException(LogisticsErrorCode.ILLEGAL_STATUS);
        }
        if (courierId != null) {
            Courier courier = courierMapper.selectById(courierId);
            if (courier != null) {
                shipment.setCourierId(courier.getId());
                shipment.setCourierName(courier.getName());
            }
        }
        shipment.setStatus(Shipment.DELIVERING);
        shipmentMapper.updateById(shipment);
        appendTrace(id, "快件已到达派送点，【" + shipment.getCourierName() + "】正在派送");
        return detail(id);
    }

    @Transactional
    public LogisticsDtos.ShipmentVO deliver(Long id, String signer) {
        Shipment shipment = require(id);
        if (!Shipment.DELIVERING.equals(shipment.getStatus())) {
            throw new BusinessException(LogisticsErrorCode.ILLEGAL_STATUS);
        }
        shipment.setStatus(Shipment.SIGNED);
        shipmentMapper.updateById(shipment);
        appendTrace(id, "快件已签收，签收人：" + (StringUtils.hasText(signer) ? signer : "本人"));
        return detail(id);
    }

    private Shipment require(Long id) {
        Shipment shipment = shipmentMapper.selectById(id);
        if (shipment == null) {
            throw new BusinessException(LogisticsErrorCode.SHIPMENT_NOT_FOUND);
        }
        return shipment;
    }

    private void appendTrace(Long shipmentId, String content) {
        ShipmentTrace trace = new ShipmentTrace();
        trace.setShipmentId(shipmentId);
        trace.setContent(content);
        traceMapper.insert(trace);
    }

    private LogisticsDtos.ShipmentVO toVO(Shipment shipment) {
        LogisticsDtos.ShipmentVO vo = new LogisticsDtos.ShipmentVO();
        vo.setId(shipment.getId());
        vo.setTrackingNo(shipment.getTrackingNo());
        vo.setOrderNo(shipment.getOrderNo());
        vo.setShopId(shipment.getShopId());
        vo.setShopName(shipment.getShopName());
        vo.setPickupAddress(shipment.getPickupAddress());
        vo.setPickupRegion(shipment.getPickupRegion());
        vo.setCompanyId(shipment.getCompanyId());
        vo.setCompanyName(shipment.getCompanyName());
        vo.setReceiverName(shipment.getReceiverName());
        vo.setReceiverPhone(shipment.getReceiverPhone());
        vo.setReceiverAddress(shipment.getReceiverAddress());
        vo.setReceiverRegion(shipment.getReceiverRegion());
        vo.setItemsText(shipment.getItemsText());
        vo.setStatus(shipment.getStatus());
        vo.setStatusText(STATUS_TEXT.getOrDefault(shipment.getStatus(), shipment.getStatus()));
        vo.setCourierId(shipment.getCourierId());
        vo.setCourierName(shipment.getCourierName());
        vo.setCreatedAt(shipment.getCreatedAt());
        vo.setTraces(traceMapper.selectList(Wrappers.<ShipmentTrace>lambdaQuery()
                        .eq(ShipmentTrace::getShipmentId, shipment.getId())
                        .orderByDesc(ShipmentTrace::getId))
                .stream().map(trace -> {
                    LogisticsDtos.TraceVO traceVO = new LogisticsDtos.TraceVO();
                    traceVO.setContent(trace.getContent());
                    traceVO.setCreatedAt(trace.getCreatedAt());
                    return traceVO;
                }).toList());
        return vo;
    }

    /** 当前登录快递员的档案 ID */
    public Long currentCourierId() {
        return UserContext.getCourierId();
    }
}
