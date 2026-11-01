package com.four_person.platform.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.four_person.common.api.PageResult;
import com.four_person.common.exception.BusinessException;
import com.four_person.common.web.AccessGuard;
import com.four_person.common.web.UserContext;
import com.four_person.platform.api.dto.PlatformDtos;
import com.four_person.platform.domain.model.AuditRecord;
import com.four_person.platform.infrastructure.persistence.mapper.AuditRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditApplicationService {

    private static final int NOT_FOUND = 40404;

    private final AuditRecordMapper auditMapper;

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

    @Transactional
    public PlatformDtos.AuditVO approve(Long id, String reason) {
        return finish(id, AuditRecord.APPROVED, reason);
    }

    @Transactional
    public PlatformDtos.AuditVO reject(Long id, String reason) {
        return finish(id, AuditRecord.REJECTED, reason);
    }

    private PlatformDtos.AuditVO finish(Long id, String status, String reason) {
        AccessGuard.requireGroup("PLATFORM");
        AuditRecord record = auditMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(NOT_FOUND, "审核记录不存在");
        }
        if (!AuditRecord.PENDING.equals(record.getStatus())) {
            throw new BusinessException(40005, "该审核已处理");
        }
        record.setStatus(status);
        record.setReason(reason == null ? "" : reason);
        record.setAuditedBy(UserContext.getUserId());
        record.setAuditedAt(LocalDateTime.now());
        auditMapper.updateById(record);
        return toVO(record);
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
        vo.setAuditedAt(record.getAuditedAt());
        vo.setCreatedAt(record.getCreatedAt());
        return vo;
    }
}
