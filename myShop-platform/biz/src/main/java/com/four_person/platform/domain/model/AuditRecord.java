package com.four_person.platform.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_record")
public class AuditRecord {

    public static final String PENDING = "PENDING";
    public static final String FIRST_APPROVED = "FIRST_APPROVED";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String CANCELLED = "CANCELLED";

    /** 物流入驻申请类型（两级审核：业务员初审 → 管理员终审） */
    public static final String TYPE_LOGISTICS_APPLY = "LOGISTICS_APPLY";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String type;
    private Long bizId;
    private String title;
    private String detail;
    private Long applicantId;
    private String applicantName;
    private String status;
    private String reason;
    private String payload;
    private Long auditedBy;
    private LocalDateTime auditedAt;
    /** 两级审核：第一级（业务员初审）审核人/时间 */
    private Long stage1By;
    private LocalDateTime stage1At;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
