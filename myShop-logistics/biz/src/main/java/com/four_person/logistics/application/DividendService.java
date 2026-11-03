package com.four_person.logistics.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.four_person.common.api.PageResult;
import com.four_person.common.exception.BusinessException;
import com.four_person.logistics.api.dto.LogisticsDtos;
import com.four_person.logistics.domain.model.DividendRecord;
import com.four_person.logistics.domain.model.LogisticsCompany;
import com.four_person.logistics.domain.model.Shipment;
import com.four_person.logistics.enums.LogisticsErrorCode;
import com.four_person.logistics.infrastructure.persistence.mapper.DividendRecordMapper;
import com.four_person.logistics.infrastructure.persistence.mapper.LogisticsCompanyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 平台与物流公司的运费分红：
 * 正向运单签收时按配置比例（config_item dividend/platform-ratio）分订单运费，
 * 逐单写分红流水并累加物流公司余额；平台侧另可对单个公司手工调账。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DividendService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    /** 配置缺失或非法时的兜底分成比例 */
    private static final BigDecimal DEFAULT_PLATFORM_RATIO = new BigDecimal("15");

    private final DividendRecordMapper dividendRecordMapper;
    private final LogisticsCompanyMapper companyMapper;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 签收即分红。独立事务（REQUIRES_NEW）：分红失败只记日志，不影响签收这一物流事实；
     * tracking_no 唯一键保证同一运单只结算一次。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void settleOnSign(Shipment shipment) {
        if (!Shipment.TYPE_NORMAL.equals(shipment.getType())) {
            return; // 售后退货件没有运费收入，不参与分红
        }
        BigDecimal freight = fetchFreight(shipment.getOrderNo());
        if (freight == null || freight.compareTo(BigDecimal.ZERO) <= 0) {
            return; // 包邮/运费为零的订单无红可分
        }

        BigDecimal ratio = platformRatio();
        BigDecimal platformAmount = freight.multiply(ratio)
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal companyAmount = freight.subtract(platformAmount);

        DividendRecord record = new DividendRecord();
        record.setTrackingNo(shipment.getTrackingNo());
        record.setType(DividendRecord.TYPE_DIVIDEND);
        record.setOrderNo(shipment.getOrderNo());
        record.setShopId(shipment.getShopId());
        record.setShopName(shipment.getShopName());
        record.setCompanyId(shipment.getCompanyId());
        record.setCompanyName(shipment.getCompanyName());
        record.setFreightAmount(freight);
        record.setPlatformRatio(ratio);
        record.setPlatformAmount(platformAmount);
        record.setCompanyAmount(companyAmount);
        try {
            dividendRecordMapper.insert(record);
        } catch (DuplicateKeyException ignored) {
            return; // 同运单已结算过（签收重试），幂等跳过
        }
        creditBalance(shipment.getCompanyId(), companyAmount);
    }

    /** 物流公司视角：账户余额 + 累计分成 */
    public LogisticsDtos.DividendSummaryVO summary(Long companyId) {
        LogisticsDtos.DividendSummaryVO vo = new LogisticsDtos.DividendSummaryVO();
        LogisticsCompany company = companyMapper.selectById(companyId);
        vo.setBalance(company == null || company.getBalance() == null
                ? BigDecimal.ZERO : company.getBalance());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT COALESCE(SUM(CASE WHEN type = 'DIVIDEND' THEN company_amount ELSE 0 END), 0) AS company_sum, "
                        + "COALESCE(SUM(CASE WHEN type = 'DIVIDEND' THEN platform_amount ELSE 0 END), 0) AS platform_sum, "
                        + "COALESCE(SUM(CASE WHEN type = 'DIVIDEND' THEN 1 ELSE 0 END), 0) AS dividend_cnt "
                        + "FROM dividend_record WHERE company_id = ?", companyId);
        vo.setTotalCompanyAmount(toDecimal(rows, "company_sum"));
        vo.setTotalPlatformAmount(toDecimal(rows, "platform_sum"));
        vo.setDividendCount(toLong(rows, "dividend_cnt"));
        return vo;
    }

    /** 平台视角：整体汇总 + 各公司账户 */
    public LogisticsDtos.DividendAdminSummaryVO adminSummary() {
        LogisticsDtos.DividendAdminSummaryVO vo = new LogisticsDtos.DividendAdminSummaryVO();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT COALESCE(SUM(CASE WHEN type = 'DIVIDEND' THEN platform_amount ELSE 0 END), 0) AS platform_sum, "
                        + "COALESCE(SUM(CASE WHEN type = 'DIVIDEND' THEN company_amount ELSE 0 END), 0) AS company_sum, "
                        + "COALESCE(SUM(CASE WHEN type = 'DIVIDEND' THEN 1 ELSE 0 END), 0) AS dividend_cnt, "
                        + "COALESCE(SUM(CASE WHEN type = 'ADJUST' THEN 1 ELSE 0 END), 0) AS adjust_cnt "
                        + "FROM dividend_record");
        vo.setTotalPlatformAmount(toDecimal(rows, "platform_sum"));
        vo.setTotalCompanyAmount(toDecimal(rows, "company_sum"));
        vo.setDividendCount(toLong(rows, "dividend_cnt"));
        vo.setAdjustCount(toLong(rows, "adjust_cnt"));

        List<Map<String, Object>> companyRows = jdbcTemplate.queryForList(
                "SELECT c.id AS company_id, c.name AS company_name, c.balance, "
                        + "COALESCE(SUM(d.company_amount), 0) AS company_sum, "
                        + "COALESCE(SUM(d.platform_amount), 0) AS platform_sum, "
                        + "COALESCE(COUNT(d.id), 0) AS dividend_cnt "
                        + "FROM logistics_company c "
                        + "LEFT JOIN dividend_record d ON d.company_id = c.id "
                        + "WHERE c.deleted = 0 GROUP BY c.id, c.name, c.balance ORDER BY company_sum DESC");
        List<LogisticsDtos.CompanyDividendVO> companies = new ArrayList<>();
        for (Map<String, Object> row : companyRows) {
            LogisticsDtos.CompanyDividendVO cv = new LogisticsDtos.CompanyDividendVO();
            cv.setCompanyId(((Number) row.get("company_id")).longValue());
            cv.setCompanyName((String) row.get("company_name"));
            cv.setBalance(toDecimal(row, "balance"));
            cv.setCompanyAmount(toDecimal(row, "company_sum"));
            cv.setPlatformAmount(toDecimal(row, "platform_sum"));
            cv.setDividendCount(toLong(row, "dividend_cnt"));
            companies.add(cv);
        }
        vo.setCompanies(companies);
        return vo;
    }

    /** 分红流水分页：companyId 为空时平台看全量 */
    public PageResult<LogisticsDtos.DividendVO> page(Long companyId, int page, int size) {
        int pageNo = Math.max(page, 1);
        int pageSize = size < 1 ? 10 : Math.min(size, 100);
        LambdaQueryWrapper<DividendRecord> wrapper = Wrappers.<DividendRecord>lambdaQuery()
                .eq(companyId != null, DividendRecord::getCompanyId, companyId)
                .orderByDesc(DividendRecord::getId);
        Page<DividendRecord> result = dividendRecordMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(result.getRecords().stream().map(this::toVO).toList(),
                result.getTotal(), pageNo, pageSize);
    }

    /** 平台调账：金额正为入账、负为扣减，同步增减公司余额 */
    @Transactional
    public void adjust(LogisticsDtos.DividendAdjustRequest request) {
        if (request.getAmount().compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException(LogisticsErrorCode.PARAM_INVALID, "调整金额不能为 0");
        }
        LogisticsCompany company = companyMapper.selectById(request.getCompanyId());
        if (company == null) {
            throw new BusinessException(LogisticsErrorCode.COMPANY_NOT_FOUND);
        }

        DividendRecord record = new DividendRecord();
        record.setType(DividendRecord.TYPE_ADJUST);
        record.setCompanyId(company.getId());
        record.setCompanyName(company.getName());
        record.setCompanyAmount(request.getAmount());
        record.setReason(request.getReason());
        dividendRecordMapper.insert(record);
        creditBalance(company.getId(), request.getAmount());
    }

    private void creditBalance(Long companyId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        jdbcTemplate.update("UPDATE logistics_company SET balance = balance + ? WHERE id = ?", amount, companyId);
    }

    /** 跨服务只读订单运费（同库 JdbcTemplate，失败时本单跳过分红） */
    private BigDecimal fetchFreight(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return null;
        }
        try {
            List<BigDecimal> list = jdbcTemplate.queryForList(
                    "SELECT freight_amount FROM order_info WHERE order_no = ? AND deleted = 0",
                    BigDecimal.class, orderNo);
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception ex) {
            log.warn("读取订单运费失败，本单跳过分红: orderNo={}", orderNo, ex);
            return null;
        }
    }

    /** 平台分成比例（config_item dividend/platform-ratio，%） */
    private BigDecimal platformRatio() {
        try {
            List<String> values = jdbcTemplate.queryForList(
                    "SELECT config_value FROM config_item WHERE config_group = 'dividend' AND config_key = 'platform-ratio' LIMIT 1",
                    String.class);
            if (!values.isEmpty() && StringUtils.hasText(values.get(0))) {
                return new BigDecimal(values.get(0).trim());
            }
        } catch (Exception ex) {
            log.warn("读取分成比例配置失败，使用默认比例 {}", DEFAULT_PLATFORM_RATIO, ex);
        }
        return DEFAULT_PLATFORM_RATIO;
    }

    private LogisticsDtos.DividendVO toVO(DividendRecord record) {
        LogisticsDtos.DividendVO vo = new LogisticsDtos.DividendVO();
        vo.setId(record.getId());
        vo.setTrackingNo(record.getTrackingNo());
        vo.setType(record.getType());
        vo.setTypeText(DividendRecord.TYPE_ADJUST.equals(record.getType()) ? "平台调账" : "签收分红");
        vo.setOrderNo(record.getOrderNo());
        vo.setShopName(record.getShopName());
        vo.setCompanyName(record.getCompanyName());
        vo.setFreightAmount(record.getFreightAmount());
        vo.setPlatformRatio(record.getPlatformRatio());
        vo.setPlatformAmount(record.getPlatformAmount());
        vo.setCompanyAmount(record.getCompanyAmount());
        vo.setReason(record.getReason());
        vo.setCreatedAt(record.getCreatedAt());
        return vo;
    }

    private static BigDecimal toDecimal(List<Map<String, Object>> rows, String key) {
        return toDecimal(rows.isEmpty() ? null : rows.get(0), key);
    }

    private static BigDecimal toDecimal(Map<String, Object> row, String key) {
        Object value = row == null ? null : row.get(key);
        return value instanceof Number number ? BigDecimal.valueOf(number.doubleValue()) : BigDecimal.ZERO;
    }

    private static long toLong(List<Map<String, Object>> rows, String key) {
        return toLong(rows.isEmpty() ? null : rows.get(0), key);
    }

    private static long toLong(Map<String, Object> row, String key) {
        Object value = row == null ? null : row.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
