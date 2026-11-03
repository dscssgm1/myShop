package com.four_person.platform.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.four_person.common.exception.BusinessException;
import com.four_person.common.web.UserContext;
import com.four_person.platform.api.dto.PlatformDtos;
import com.four_person.platform.domain.model.AuditRecord;
import com.four_person.platform.infrastructure.persistence.mapper.AuditRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 物流入驻申请：买家/用户提交，复用 audit_record（type=LOGISTICS_APPLY），
 * 由平台业务员初审 → 平台管理员终审，终审通过后写入 logistics_company。
 */
@Service
@RequiredArgsConstructor
public class LogisticsApplyApplicationService {

    private final AuditRecordMapper auditMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PlatformDtos.AuditVO apply(PlatformDtos.LogisticsApplyRequest request) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        String name = request.getName().trim();
        String code = request.getCode().trim().toUpperCase();
        if (!StringUtils.hasText(request.getContact())) {
            throw new BusinessException(40001, "请填写联系人");
        }
        if (!StringUtils.hasText(request.getPhone())) {
            throw new BusinessException(40002, "请填写联系电话");
        }

        // 该公司编码是否已在审核流程中
        Long inFlight = auditMapper.selectCount(Wrappers.<AuditRecord>lambdaQuery()
                .eq(AuditRecord::getType, AuditRecord.TYPE_LOGISTICS_APPLY)
                .in(AuditRecord::getStatus, AuditRecord.PENDING, AuditRecord.FIRST_APPROVED)
                .like(AuditRecord::getPayload, "\"" + code + "\""));
        if (inFlight != null && inFlight > 0) {
            throw new BusinessException(40901, "该公司编码已有正在审核的入驻申请");
        }
        // 该公司编码是否已入驻
        Long exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM logistics_company WHERE code = ? AND deleted = 0", Long.class, code);
        if (exists != null && exists > 0) {
            throw new BusinessException(40902, "该公司编码已存在，请直接联系平台开通账号");
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("name", name);
        payload.put("code", code);
        payload.put("contact", request.getContact().trim());
        payload.put("phone", request.getPhone().trim());
        payload.put("description", request.getDescription() == null ? "" : request.getDescription().trim());

        AuditRecord record = new AuditRecord();
        record.setType(AuditRecord.TYPE_LOGISTICS_APPLY);
        record.setTitle("物流公司入驻：" + name);
        record.setDetail("编码 " + code + " ｜ 联系人 " + request.getContact().trim() + "（" + request.getPhone().trim() + "）");
        record.setPayload(payload.toString());
        record.setApplicantId(userId);
        record.setApplicantName(UserContext.getRole() == null ? "" : UserContext.getRole());
        record.setStatus(AuditRecord.PENDING);
        auditMapper.insert(record);
        return toVO(record);
    }

    /** 我的物流入驻申请及进度 */
    public List<PlatformDtos.AuditVO> mine() {
        return auditMapper.selectList(Wrappers.<AuditRecord>lambdaQuery()
                        .eq(AuditRecord::getType, AuditRecord.TYPE_LOGISTICS_APPLY)
                        .eq(AuditRecord::getApplicantId, UserContext.getUserId())
                        .orderByDesc(AuditRecord::getId))
                .stream().map(this::toVO).toList();
    }

    private PlatformDtos.AuditVO toVO(AuditRecord record) {
        PlatformDtos.AuditVO vo = new PlatformDtos.AuditVO();
        vo.setId(record.getId());
        vo.setType(record.getType());
        vo.setTitle(record.getTitle());
        vo.setDetail(record.getDetail());
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
