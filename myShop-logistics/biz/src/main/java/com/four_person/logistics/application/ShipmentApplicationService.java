package com.four_person.logistics.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.four_person.common.api.PageResult;
import com.four_person.common.api.Results;
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
import com.four_person.order.api.OrderInternalApi;
import com.four_person.order.api.dto.OrderInternalDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentApplicationService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter TRACKING_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

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
    private final OrderInternalApi orderInternalApi;
    private final DividendService dividendService;

    /** 商家发货（经订单服务编排的内部接口）：创建正向运单，同订单已有在途运单时幂等返回 */
    @Transactional
    public LogisticsDtos.ShipmentVO createInternal(LogisticsDtos.ShipmentCreateRequest request) {
        // 幂等保护：同订单已有未签收的正向运单则直接返回，防止编排重试造成重复运单
        Shipment existing = shipmentMapper.selectOne(Wrappers.<Shipment>lambdaQuery()
                .eq(Shipment::getOrderNo, request.getOrderNo())
                .eq(Shipment::getType, Shipment.TYPE_NORMAL)
                .ne(Shipment::getStatus, Shipment.SIGNED)
                .last("LIMIT 1"));
        if (existing != null) {
            return detail(existing.getId());
        }

        LogisticsCompany company = companyMapper.selectById(request.getCompanyId());
        if (company == null) {
            throw new BusinessException(LogisticsErrorCode.COMPANY_NOT_FOUND);
        }
        if (company.getStatus() == null || company.getStatus() != 1) {
            throw new BusinessException(LogisticsErrorCode.COMPANY_NOT_ACTIVE);
        }

        Shipment shipment = new Shipment();
        fillNormalShipment(shipment, request, company);
        insertWithRetry(shipment, company);

        appendTrace(shipment.getId(), "商家已发货，等待快递员揽收");
        return detail(shipment.getId());
    }

    /** 商家同意售后退货：创建反向运单（买家处揽收，寄回商家） */
    @Transactional
    public LogisticsDtos.ShipmentVO createReturn(LogisticsDtos.ReturnShipmentRequest request) {
        LogisticsCompany company = firstActiveCompany();
        if (company == null) {
            throw new BusinessException(LogisticsErrorCode.COMPANY_NOT_ACTIVE, "暂无可用物流公司，请联系平台");
        }

        Shipment shipment = new Shipment();
        newTrackingNo(shipment, company);
        shipment.setOrderNo(request.getOrderNo());
        shipment.setShopId(request.getShopId());
        shipment.setShopName(request.getShopName());
        shipment.setPickupAddress(request.getPickupAddress());
        shipment.setPickupRegion(StringUtils.hasText(request.getPickupRegion())
                ? request.getPickupRegion() : RegionParser.parse(request.getPickupAddress()));
        shipment.setCompanyId(company.getId());
        shipment.setCompanyName(company.getName());
        shipment.setReceiverName(request.getReceiverName());
        shipment.setReceiverPhone(request.getReceiverPhone());
        shipment.setReceiverAddress(request.getReceiverAddress());
        shipment.setReceiverRegion(StringUtils.hasText(request.getReceiverRegion())
                ? request.getReceiverRegion() : RegionParser.parse(request.getReceiverAddress()));
        shipment.setItemsText(request.getItemsText());
        shipment.setStatus(Shipment.WAITING_PICKUP);
        shipment.setType(Shipment.TYPE_RETURN);
        shipment.setAfterSaleNo(request.getAfterSaleNo());
        shipment.setPickupCode(request.getPickupCode());
        insertWithRetry(shipment, company);

        appendTrace(shipment.getId(), "商家已同意退货，等待用户预约上门取件");
        return detail(shipment.getId());
    }

    private void fillNormalShipment(Shipment shipment, LogisticsDtos.ShipmentCreateRequest request,
                                    LogisticsCompany company) {
        newTrackingNo(shipment, company);
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
        shipment.setType(Shipment.TYPE_NORMAL);
        shipment.setAfterSaleNo("");
        shipment.setPickupCode("");
    }

    /** 运单号：公司编码 + 日期时间 + 6 位随机 */
    private void newTrackingNo(Shipment shipment, LogisticsCompany company) {
        shipment.setTrackingNo(company.getCode() + LocalDateTime.now().format(TRACKING_NO_FORMATTER)
                + ThreadLocalRandom.current().nextInt(100000, 1000000));
    }

    /** 撞运单号唯一键时换号重试，规避并发下毫秒级随机碰撞 */
    private void insertWithRetry(Shipment shipment, LogisticsCompany company) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                shipmentMapper.insert(shipment);
                return;
            } catch (DuplicateKeyException ex) {
                if (attempt == 3) {
                    throw ex;
                }
                newTrackingNo(shipment, company);
            }
        }
    }

    public LogisticsDtos.ShipmentVO byTrackingNo(String trackingNo) {
        Shipment shipment = shipmentMapper.selectOne(Wrappers.<Shipment>lambdaQuery()
                .eq(Shipment::getTrackingNo, trackingNo).last("LIMIT 1"));
        return shipment == null ? null : toVO(shipment);
    }

    /** 用户预约上门取件，仅限两天以内 */
    @Transactional
    public LogisticsDtos.ShipmentVO appointment(String trackingNo, LocalDateTime appointmentTime) {
        Shipment shipment = shipmentMapper.selectOne(Wrappers.<Shipment>lambdaQuery()
                .eq(Shipment::getTrackingNo, trackingNo).last("LIMIT 1"));
        if (shipment == null) {
            throw new BusinessException(LogisticsErrorCode.SHIPMENT_NOT_FOUND);
        }
        if (!Shipment.TYPE_RETURN.equals(shipment.getType())) {
            throw new BusinessException(LogisticsErrorCode.APPOINTMENT_NOT_SUPPORTED);
        }
        if (!Shipment.WAITING_PICKUP.equals(shipment.getStatus())) {
            throw new BusinessException(LogisticsErrorCode.ILLEGAL_STATUS, "快递员已揽收，无法修改预约时间");
        }
        LocalDateTime now = LocalDateTime.now();
        if (appointmentTime == null || appointmentTime.isBefore(now.minusMinutes(5))
                || appointmentTime.isAfter(now.plusDays(2))) {
            throw new BusinessException(LogisticsErrorCode.APPOINTMENT_INVALID);
        }
        shipment.setAppointmentTime(appointmentTime);
        shipmentMapper.updateById(shipment);
        appendTrace(shipment.getId(), "用户已预约上门取件：" + appointmentTime.format(TIME_FORMATTER));
        return detail(shipment.getId());
    }

    private LogisticsCompany firstActiveCompany() {
        List<LogisticsCompany> companies = companyMapper.selectList(Wrappers.<LogisticsCompany>lambdaQuery()
                .eq(LogisticsCompany::getStatus, 1)
                .orderByAsc(LogisticsCompany::getId)
                .last("LIMIT 1"));
        return companies.isEmpty() ? null : companies.get(0);
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

    /** 买家查询正向运单：同一订单可能有售后退货运单，优先返回普通运单；对外隐藏取件码 */
    public LogisticsDtos.ShipmentVO byOrderNo(String orderNo) {
        Shipment shipment = shipmentMapper.selectOne(
                Wrappers.<Shipment>lambdaQuery()
                        .eq(Shipment::getOrderNo, orderNo)
                        .orderByAsc(Shipment::getType)
                        .last("LIMIT 1"));
        if (shipment == null) {
            return null;
        }
        LogisticsDtos.ShipmentVO vo = toVO(shipment);
        // 取件码仅售后详情（订单服务）向买家展示，运单查询接口不外泄
        vo.setPickupCode(null);
        return vo;
    }

    /**
     * 快递员的任务：揽收按店铺区域，派送按收货区域。
     * 区域未识别（空）的运单对本区域所有快递员可见，作为兜底避免运单滞留无人认领。
     */
    public List<LogisticsDtos.ShipmentVO> courierTasks(Long courierId, String type) {
        Courier courier = courierMapper.selectById(courierId);
        if (courier == null) {
            throw new BusinessException(LogisticsErrorCode.COURIER_NOT_FOUND);
        }
        LambdaQueryWrapper<Shipment> wrapper = Wrappers.<Shipment>lambdaQuery()
                .eq(Shipment::getCompanyId, courier.getCompanyId());
        if ("pickup".equalsIgnoreCase(type)) {
            // 售后退货运单需要用户先预约取件时间，才会出现在揽收任务中
            wrapper.eq(Shipment::getStatus, Shipment.WAITING_PICKUP)
                    .and(w -> w.eq(Shipment::getPickupRegion, courier.getRegion())
                            .or().eq(Shipment::getPickupRegion, ""))
                    .and(w -> w.eq(Shipment::getType, Shipment.TYPE_NORMAL)
                            .or(q -> q.eq(Shipment::getType, Shipment.TYPE_RETURN)
                                    .isNotNull(Shipment::getAppointmentTime)));
        } else {
            wrapper.in(Shipment::getStatus, Shipment.IN_TRANSIT, Shipment.DELIVERING)
                    .and(w -> w.eq(Shipment::getReceiverRegion, courier.getRegion())
                            .or().eq(Shipment::getReceiverRegion, ""));
        }
        wrapper.orderByAsc(Shipment::getId);
        return shipmentMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Transactional
    public LogisticsDtos.ShipmentVO pickup(Long id, Long courierId, String pickupCode) {
        Shipment shipment = require(id);
        if (!Shipment.WAITING_PICKUP.equals(shipment.getStatus())) {
            throw new BusinessException(LogisticsErrorCode.ILLEGAL_STATUS);
        }
        if (Shipment.TYPE_RETURN.equals(shipment.getType())) {
            if (!StringUtils.hasText(pickupCode)) {
                throw new BusinessException(LogisticsErrorCode.PICKUP_CODE_REQUIRED);
            }
            if (!pickupCode.trim().equals(shipment.getPickupCode())) {
                throw new BusinessException(LogisticsErrorCode.PICKUP_CODE_INVALID);
            }
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
        notifyOrderSigned(shipment);
        settleDividend(shipment);
        return detail(id);
    }

    /**
     * 签收即分红（平台与物流公司按比例分运费）。
     * 分红在独立事务中结算，失败只记录日志，不影响签收。
     */
    private void settleDividend(Shipment shipment) {
        try {
            dividendService.settleOnSign(shipment);
        } catch (Exception ex) {
            log.warn("签收分红结算失败: trackingNo={}, orderNo={}",
                    shipment.getTrackingNo(), shipment.getOrderNo(), ex);
        }
    }

    /**
     * 签收后回调订单服务联动：正向件记录签收时间并启动自动确认倒计时，退货件通知买家商家已收货。
     * 联动失败只记录日志，不影响签收这一物流事实。
     */
    private void notifyOrderSigned(Shipment shipment) {
        try {
            if (Shipment.TYPE_RETURN.equals(shipment.getType())) {
                if (StringUtils.hasText(shipment.getAfterSaleNo())) {
                    OrderInternalDtos.ReturnSignedRequest request = new OrderInternalDtos.ReturnSignedRequest();
                    request.setTrackingNo(shipment.getTrackingNo());
                    Results.data(orderInternalApi.returnShipmentSigned(request));
                }
                return;
            }
            if (StringUtils.hasText(shipment.getOrderNo())) {
                OrderInternalDtos.ShipmentSignedRequest request = new OrderInternalDtos.ShipmentSignedRequest();
                request.setOrderNo(shipment.getOrderNo());
                request.setTrackingNo(shipment.getTrackingNo());
                request.setSignedAt(LocalDateTime.now());
                Results.data(orderInternalApi.shipmentSigned(request));
            }
        } catch (Exception ex) {
            log.warn("签收联动订单服务失败: trackingNo={}, orderNo={}",
                    shipment.getTrackingNo(), shipment.getOrderNo(), ex);
        }
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
        vo.setType(shipment.getType() == null ? Shipment.TYPE_NORMAL : shipment.getType());
        vo.setTypeText(Shipment.TYPE_RETURN.equals(shipment.getType()) ? "售后退货" : "正向发货");
        vo.setAfterSaleNo(shipment.getAfterSaleNo());
        vo.setPickupCode(shipment.getPickupCode());
        vo.setAppointmentTime(shipment.getAppointmentTime());
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
