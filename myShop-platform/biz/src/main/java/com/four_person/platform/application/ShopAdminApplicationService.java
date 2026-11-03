package com.four_person.platform.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.four_person.common.api.PageResult;
import com.four_person.common.exception.BusinessException;
import com.four_person.common.web.AccessGuard;
import com.four_person.common.web.UserContext;
import com.four_person.message.api.MessageInternalApi;
import com.four_person.message.api.dto.MessageDtos;
import com.four_person.platform.api.dto.PlatformDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 平台商家管理：店铺列表 / 启用停用、商家入驻申请审核。
 * 多服务共用一个数据库，直接用 JdbcTemplate 操作 shop / shop_apply / sys_user。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopAdminApplicationService {

    private static final String DEFAULT_ICON = "🏪";
    private static final String DEFAULT_COLOR = "linear-gradient(135deg,#ff416c,#ff4b2b)";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final MessageInternalApi messageInternalApi;

    // ==================== 店铺列表 ====================

    public PageResult<PlatformDtos.ShopVO> page(String keyword, Integer status, int page, int size) {
        AccessGuard.requireGroup("PLATFORM");
        int pageNo = Math.max(page, 1);
        int pageSize = size < 1 ? 10 : Math.min(size, 100);

        StringBuilder where = new StringBuilder(" WHERE deleted = 0");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            where.append(" AND (name LIKE ? OR contact LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (status != null) {
            where.append(" AND status = ?");
            args.add(status);
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shop" + where, Long.class, args.toArray());
        args.add(pageSize);
        args.add((pageNo - 1) * pageSize);
        List<PlatformDtos.ShopVO> list = jdbcTemplate.query(
                "SELECT * FROM shop" + where + " ORDER BY id LIMIT ? OFFSET ?",
                this::mapShop, args.toArray());

        return PageResult.of(list, total == null ? 0 : total, pageNo, pageSize);
    }

    private PlatformDtos.ShopVO mapShop(ResultSet rs, int i) throws SQLException {
        PlatformDtos.ShopVO vo = new PlatformDtos.ShopVO();
        vo.setId(rs.getLong("id"));
        vo.setName(rs.getString("name"));
        vo.setIcon(rs.getString("icon"));
        vo.setDescription(rs.getString("description"));
        vo.setContact(rs.getString("contact"));
        vo.setPhone(rs.getString("phone"));
        vo.setRegion(rs.getString("region"));
        vo.setAddress(rs.getString("address"));
        vo.setCategories(rs.getString("categories"));
        vo.setSales(rs.getInt("sales"));
        vo.setRating(rs.getBigDecimal("rating"));
        vo.setStatus(rs.getInt("status"));
        vo.setStatusReason(rs.getString("status_reason"));
        vo.setCreatedAt(localDateTime(rs, "created_at"));
        vo.setLicenseNo(rs.getString("license_no"));
        vo.setLicenseImage(rs.getString("license_image"));
        vo.setLegalPerson(rs.getString("legal_person"));
        vo.setRegisteredAddress(rs.getString("registered_address"));
        vo.setBusinessScope(rs.getString("business_scope"));
        vo.setBusinessTerm(rs.getString("business_term"));
        return vo;
    }

    public void updateStatus(Long id, Integer status, String reason) {
        AccessGuard.requireGroup("PLATFORM");
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(40001, "状态值不合法");
        }
        int updated = jdbcTemplate.update(
                "UPDATE shop SET status = ?, status_reason = ? WHERE id = ? AND deleted = 0",
                status, reason == null ? "" : reason.trim(), id);
        if (updated == 0) {
            throw new BusinessException(40401, "店铺不存在");
        }
    }

    // ==================== 商家入驻审核 ====================

    public PageResult<PlatformDtos.ShopApplyVO> pageApplies(String status, String keyword, int page, int size) {
        AccessGuard.requireGroup("PLATFORM");
        int pageNo = Math.max(page, 1);
        int pageSize = size < 1 ? 10 : Math.min(size, 100);

        StringBuilder where = new StringBuilder(" WHERE a.deleted = 0");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(status)) {
            where.append(" AND a.status = ?");
            args.add(status);
        }
        if (StringUtils.hasText(keyword)) {
            where.append(" AND (a.shop_name LIKE ? OR a.contact LIKE ? OR u.username LIKE ? OR u.nickname LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        String from = " FROM shop_apply a LEFT JOIN sys_user u ON u.id = a.user_id AND u.deleted = 0";

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)" + from + where, Long.class, args.toArray());
        args.add(pageSize);
        args.add((pageNo - 1) * pageSize);
        List<PlatformDtos.ShopApplyVO> list = jdbcTemplate.query(
                "SELECT a.*, u.username, u.nickname, u.email" + from + where
                        + " ORDER BY a.id DESC LIMIT ? OFFSET ?",
                this::mapApply, args.toArray());

        return PageResult.of(list, total == null ? 0 : total, pageNo, pageSize);
    }

    /** 待审核申请数（菜单角标） */
    public long pendingCount() {
        AccessGuard.requireGroup("PLATFORM");
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shop_apply WHERE status = 'PENDING' AND deleted = 0", Long.class);
        return count == null ? 0 : count;
    }

    /**
     * 审核通过：生成店铺 → 申请单置为 APPROVED → 申请人账号升级为商家并绑定店铺 → 站内通知。
     * 申请人需重新登录，JWT 才会带上新的角色与店铺 ID。
     */
    @Transactional
    public PlatformDtos.ShopApplyVO approveApply(Long id, String reason) {
        AccessGuard.requireGroup("PLATFORM");
        Map<String, Object> apply = requirePendingApply(id);
        Long userId = asLong(apply.get("user_id"));

        Map<String, Object> user = jdbcTemplate.queryForMap(
                "SELECT role_code, shop_id FROM sys_user WHERE id = ? AND deleted = 0", userId);
        if (user.get("shop_id") != null) {
            throw new BusinessException(40902, "该用户已是商家，无法重复入驻");
        }

        String shopName = str(apply.get("shop_name"));
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO shop (name, icon, color, description, contact, phone, region, address,"
                            + " categories, license_no, license_image, legal_person, registered_address,"
                            + " business_scope, business_term, sales, status)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 1)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, shopName);
            ps.setString(2, DEFAULT_ICON);
            ps.setString(3, DEFAULT_COLOR);
            ps.setString(4, str(apply.get("description")));
            ps.setString(5, str(apply.get("contact")));
            ps.setString(6, str(apply.get("phone")));
            ps.setString(7, str(apply.get("region")));
            ps.setString(8, str(apply.get("address")));
            ps.setString(9, str(apply.get("categories")).isBlank() ? "[]" : str(apply.get("categories")));
            ps.setString(10, str(apply.get("license_no")));
            ps.setString(11, str(apply.get("license_image")));
            ps.setString(12, str(apply.get("legal_person")));
            ps.setString(13, str(apply.get("registered_address")));
            ps.setString(14, str(apply.get("business_scope")));
            ps.setString(15, str(apply.get("business_term")));
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException(50001, "店铺创建失败，请稍后重试");
        }
        long shopId = key.longValue();

        jdbcTemplate.update("""
                        UPDATE shop_apply
                        SET status = 'APPROVED', reason = ?, shop_id = ?, audited_by = ?, audited_at = NOW()
                        WHERE id = ?
                        """,
                reason == null ? "" : reason.trim(), shopId, UserContext.getUserId(), id);
        jdbcTemplate.update(
                "UPDATE sys_user SET role_code = 'merchant', shop_id = ? WHERE id = ?", shopId, userId);

        notify(userId, "商家入驻审核通过",
                "恭喜！店铺「" + shopName + "」已成功入驻平台。请重新登录后进入「商家中心」管理店铺。");
        log.info("商家入驻通过: applyId={}, userId={}, shopId={}", id, userId, shopId);
        return getApply(id);
    }

    /** 审核驳回：填写原因并站内通知申请人 */
    @Transactional
    public PlatformDtos.ShopApplyVO rejectApply(Long id, String reason) {
        AccessGuard.requireGroup("PLATFORM");
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(40001, "请填写驳回原因");
        }
        Map<String, Object> apply = requirePendingApply(id);
        jdbcTemplate.update("""
                        UPDATE shop_apply
                        SET status = 'REJECTED', reason = ?, audited_by = ?, audited_at = NOW()
                        WHERE id = ? AND status = 'PENDING'
                        """,
                reason.trim(), UserContext.getUserId(), id);

        notify(asLong(apply.get("user_id")), "商家入驻审核未通过",
                "您提交的店铺「" + str(apply.get("shop_name")) + "」入驻申请被驳回，原因：" + reason.trim()
                        + "。修改资料后可重新提交申请。");
        return getApply(id);
    }

    private Map<String, Object> requirePendingApply(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM shop_apply WHERE id = ? AND deleted = 0", id);
        if (rows.isEmpty()) {
            throw new BusinessException(40403, "入驻申请不存在");
        }
        Map<String, Object> apply = rows.get(0);
        if (!"PENDING".equals(str(apply.get("status")))) {
            throw new BusinessException(40903, "该申请已处理");
        }
        return apply;
    }

    private PlatformDtos.ShopApplyVO getApply(Long id) {
        List<PlatformDtos.ShopApplyVO> rows = jdbcTemplate.query(
                "SELECT a.*, u.username, u.nickname, u.email"
                        + " FROM shop_apply a LEFT JOIN sys_user u ON u.id = a.user_id AND u.deleted = 0"
                        + " WHERE a.id = ? AND a.deleted = 0",
                this::mapApply, id);
        if (rows.isEmpty()) {
            throw new BusinessException(40403, "入驻申请不存在");
        }
        return rows.get(0);
    }

    private PlatformDtos.ShopApplyVO mapApply(ResultSet rs, int i) throws SQLException {
        PlatformDtos.ShopApplyVO vo = new PlatformDtos.ShopApplyVO();
        vo.setId(rs.getLong("id"));
        vo.setUserId(rs.getLong("user_id"));
        vo.setUsername(rs.getString("username"));
        vo.setNickname(rs.getString("nickname"));
        vo.setEmail(rs.getString("email"));
        vo.setShopName(rs.getString("shop_name"));
        vo.setCategories(parseCategories(rs.getString("categories")));
        vo.setContact(rs.getString("contact"));
        vo.setPhone(rs.getString("phone"));
        vo.setRegion(rs.getString("region"));
        vo.setAddress(rs.getString("address"));
        vo.setDescription(rs.getString("description"));
        vo.setLicenseNo(rs.getString("license_no"));
        vo.setLicenseImage(rs.getString("license_image"));
        vo.setLegalPerson(rs.getString("legal_person"));
        vo.setRegisteredAddress(rs.getString("registered_address"));
        vo.setBusinessScope(rs.getString("business_scope"));
        vo.setBusinessTerm(rs.getString("business_term"));
        vo.setStatus(rs.getString("status"));
        vo.setReason(rs.getString("reason"));
        vo.setShopId(longOrNull(rs, "shop_id"));
        vo.setAuditedAt(localDateTime(rs, "audited_at"));
        vo.setCreatedAt(localDateTime(rs, "created_at"));
        return vo;
    }

    // ==================== 工具 ====================

    private List<String> parseCategories(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            return Collections.emptyList();
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

    /** JDBC 驱动对 DATETIME 可能返回 LocalDateTime 或 Timestamp，统一处理 */
    private static LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().atStartOfDay();
        }
        return null;
    }

    private static Long longOrNull(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Long asLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
