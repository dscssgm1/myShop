package com.four_person.platform.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.four_person.common.api.PageResult;
import com.four_person.common.exception.BusinessException;
import com.four_person.common.web.AccessGuard;
import com.four_person.common.web.UserContext;
import com.four_person.message.api.MessageInternalApi;
import com.four_person.message.api.dto.MessageDtos;
import com.four_person.platform.api.dto.PlatformDtos;
import com.four_person.platform.domain.model.AuditRecord;
import com.four_person.platform.infrastructure.persistence.mapper.AuditRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditApplicationService {

    private static final int NOT_FOUND = 40404;

    private final AuditRecordMapper auditMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final MessageInternalApi messageInternalApi;

    /** 各业务服务提交审核 */
    public PlatformDtos.AuditVO create(PlatformDtos.AuditCreateRequest request) {
        AuditRecord record = new AuditRecord();
        record.setType(request.getType());
        record.setBizId(request.getBizId());
        record.setTitle(request.getTitle());
        record.setDetail(request.getDetail());
        record.setPayload(request.getPayload());
        record.setApplicantId(UserContext.getUserId());
        record.setApplicantName(UserContext.getRole() == null ? "" : UserContext.getRole());
        record.setStatus(AuditRecord.PENDING);
        auditMapper.insert(record);
        return toVO(record);
    }

    public PageResult<PlatformDtos.AuditVO> page(String type, String status, int page, int size) {
        AccessGuard.requireGroup("PLATFORM");
        int pageNo = Math.max(page, 1);
        int pageSize = size < 1 ? 10 : Math.min(size, 100);

        LambdaQueryWrapper<AuditRecord> wrapper = Wrappers.<AuditRecord>lambdaQuery()
                .eq(StringUtils.hasText(type), AuditRecord::getType, type)
                .eq(StringUtils.hasText(status), AuditRecord::getStatus, status)
                .orderByDesc(AuditRecord::getId);

        Page<AuditRecord> result = auditMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(result.getRecords().stream().map(this::toVO).toList(),
                result.getTotal(), pageNo, pageSize);
    }

    public List<PlatformDtos.AuditVO> mine() {
        return auditMapper.selectList(Wrappers.<AuditRecord>lambdaQuery()
                        .eq(AuditRecord::getApplicantId, UserContext.getUserId())
                        .orderByDesc(AuditRecord::getId))
                .stream().map(this::toVO).toList();
    }

    /**
     * 第一级审核（平台业务员）：仅物流入驻申请，PENDING → FIRST_APPROVED。
     */
    @Transactional
    public PlatformDtos.AuditVO firstApprove(Long id, String reason) {
        AccessGuard.requireRole("admin-sales");
        AuditRecord record = requireRecord(id);
        if (!AuditRecord.TYPE_LOGISTICS_APPLY.equals(record.getType())) {
            throw new BusinessException(40006, "该类型不支持初审");
        }
        if (!AuditRecord.PENDING.equals(record.getStatus())) {
            throw new BusinessException(40005, "该审核已处理");
        }
        record.setStatus(AuditRecord.FIRST_APPROVED);
        record.setReason(reason == null ? "" : reason);
        record.setStage1By(UserContext.getUserId());
        record.setStage1At(LocalDateTime.now());
        auditMapper.updateById(record);
        notify(record.getApplicantId(), "物流入驻初审通过",
                "您提交的「" + record.getTitle() + "」已通过平台业务员初审，等待平台管理员终审。");
        return toVO(record);
    }

    @Transactional
    public PlatformDtos.AuditVO approve(Long id, String reason) {
        AuditRecord record = requireRecord(id);
        if (AuditRecord.TYPE_LOGISTICS_APPLY.equals(record.getType())) {
            // 物流入驻：管理员终审，业务员初审通过后才能终审
            AccessGuard.requireRole("admin");
            if (!AuditRecord.FIRST_APPROVED.equals(record.getStatus())) {
                throw new BusinessException(40005, "该申请还未通过业务员初审");
            }
            finish(record, AuditRecord.APPROVED, reason);
            onboardLogisticsCompany(record);
            notify(record.getApplicantId(), "物流入驻审核通过",
                    "恭喜！您提交的「" + record.getTitle() + "」已通过平台管理员终审，物流公司正式入驻。");
            return toVO(record);
        }
        AccessGuard.requireGroup("PLATFORM");
        if (!AuditRecord.PENDING.equals(record.getStatus())) {
            throw new BusinessException(40005, "该审核已处理");
        }
        finish(record, AuditRecord.APPROVED, reason);
        return toVO(record);
    }

    @Transactional
    public PlatformDtos.AuditVO reject(Long id, String reason) {
        AuditRecord record = requireRecord(id);
        if (AuditRecord.TYPE_LOGISTICS_APPLY.equals(record.getType())) {
            // 初审阶段：业务员或管理员均可驳回；终审阶段：仅管理员
            if (AuditRecord.PENDING.equals(record.getStatus())) {
                AccessGuard.requireRole("admin-sales", "admin");
            } else if (AuditRecord.FIRST_APPROVED.equals(record.getStatus())) {
                AccessGuard.requireRole("admin");
            } else {
                throw new BusinessException(40005, "该审核已处理");
            }
            finish(record, AuditRecord.REJECTED, reason);
            notify(record.getApplicantId(), "物流入驻审核未通过",
                    "您提交的「" + record.getTitle() + "」被驳回，原因：" + (reason == null ? "无" : reason));
            return toVO(record);
        }
        AccessGuard.requireGroup("PLATFORM");
        if (!AuditRecord.PENDING.equals(record.getStatus())) {
            throw new BusinessException(40005, "该审核已处理");
        }
        finish(record, AuditRecord.REJECTED, reason);
        return toVO(record);
    }

    private AuditRecord requireRecord(Long id) {
        AuditRecord record = auditMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(NOT_FOUND, "审核记录不存在");
        }
        return record;
    }

    private void finish(AuditRecord record, String status, String reason) {
        record.setStatus(status);
        record.setReason(reason == null ? "" : reason);
        record.setAuditedBy(UserContext.getUserId());
        record.setAuditedAt(LocalDateTime.now());
        auditMapper.updateById(record);
    }

    /** 终审通过后把物流公司写入 logistics_company 表（多服务共享数据库） */
    private void onboardLogisticsCompany(AuditRecord record) {
        try {
            JsonNode payload = objectMapper.readTree(record.getPayload() == null ? "{}" : record.getPayload());
            String name = payload.path("name").asText();
            String code = payload.path("code").asText("");
            if (!StringUtils.hasText(code)) {
                log.warn("物流入驻 payload 缺少 code，跳过建司: auditId={}", record.getId());
                return;
            }
            Long exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM logistics_company WHERE code = ? AND deleted = 0", Long.class, code);
            if (exists != null && exists > 0) {
                log.info("物流公司已存在，跳过重复入驻: code={}", code);
                return;
            }
            jdbcTemplate.update("""
                            INSERT INTO logistics_company (name, code, contact, phone, color, status)
                            VALUES (?, ?, ?, ?, ?, 1)
                            """,
                    name, code.toUpperCase(),
                    payload.path("contact").asText(""), payload.path("phone").asText(""),
                    payload.path("color").isTextual() ? payload.path("color").asText() : "#396afc");
            log.info("物流入驻完成: auditId={}, code={}", record.getId(), code);
        } catch (Exception ex) {
            log.error("物流入驻落库失败: auditId={}, cause={}", record.getId(), ex.getMessage(), ex);
            throw new BusinessException(50001, "物流公司入驻失败，请稍后重试");
        }
    }

    private void notify(Long userId, String title, String content) {
        if (userId == null) {
            return;
        }
        try {
            MessageDtos.NoticeRequest request = new MessageDtos.NoticeRequest();
            request.setUserId(userId);
            request.setTitle(title);
            request.setContent(content);
            messageInternalApi.pushNotice(request);
        } catch (Exception ex) {
            log.warn("推送站内通知失败: userId={}, title={}, cause={}", userId, title, ex.getMessage());
        }
    }

    private PlatformDtos.AuditVO toVO(AuditRecord record) {
        PlatformDtos.AuditVO vo = new PlatformDtos.AuditVO();
        vo.setId(record.getId());
        vo.setType(record.getType());
        vo.setBizId(record.getBizId());
        vo.setTitle(record.getTitle());
        vo.setDetail(record.getDetail());
        vo.setApplicantId(record.getApplicantId());
        vo.setApplicantName(record.getApplicantName());
        vo.setStatus(record.getStatus());
        vo.setReason(record.getReason());
        vo.setPayload(record.getPayload());
        vo.setStage1By(record.getStage1By());
        vo.setStage1At(record.getStage1At());
        vo.setAuditedAt(record.getAuditedAt());
        vo.setCreatedAt(record.getCreatedAt());
        return vo;
    }
}
