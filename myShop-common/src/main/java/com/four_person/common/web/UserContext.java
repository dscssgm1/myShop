package com.four_person.common.web;

import com.four_person.common.exception.BusinessException;
import com.four_person.common.exception.CommonErrorCode;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 当前请求的用户上下文。
 * <p>
 * 网关校验 JWT 后，把身份写入以下请求头；各业务服务直接读取，无需重复解析令牌：
 * <ul>
 *     <li>{@code X-User-Id} 用户 ID</li>
 *     <li>{@code X-User-Role} 角色编码（user / merchant / admin ...）</li>
 *     <li>{@code X-User-Group} 所属端（USER / MERCHANT / LOGISTICS / PLATFORM）</li>
 *     <li>{@code X-User-Shop-Id} 商家所属店铺</li>
 *     <li>{@code X-User-Company-Id} 物流所属公司</li>
 *     <li>{@code X-User-Courier-Id} 快递员档案 ID</li>
 * </ul>
 * 直连服务端口可伪造请求头，生产环境必须只允许网关访问业务服务。
 */
public final class UserContext {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String ROLE_HEADER = "X-User-Role";
    public static final String GROUP_HEADER = "X-User-Group";
    public static final String SHOP_ID_HEADER = "X-User-Shop-Id";
    public static final String COMPANY_ID_HEADER = "X-User-Company-Id";
    public static final String COURIER_ID_HEADER = "X-User-Courier-Id";

    private UserContext() {
    }

    public static Long getUserId() {
        Long userId = getUserIdOrNull();
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "缺少用户身份，请通过网关访问");
        }
        return userId;
    }

    public static Long getUserIdOrNull() {
        return readLong(USER_ID_HEADER);
    }

    public static String getRole() {
        return readHeader(ROLE_HEADER);
    }

    public static String getGroup() {
        return readHeader(GROUP_HEADER);
    }

    public static Long getShopId() {
        return readLong(SHOP_ID_HEADER);
    }

    public static Long getCompanyId() {
        return readLong(COMPANY_ID_HEADER);
    }

    public static Long getCourierId() {
        return readLong(COURIER_ID_HEADER);
    }

    public static Long requireShopId() {
        Long shopId = getShopId();
        if (shopId == null) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "当前账号未绑定店铺");
        }
        return shopId;
    }

    public static Long requireCompanyId() {
        Long companyId = getCompanyId();
        if (companyId == null) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "当前账号未绑定物流公司");
        }
        return companyId;
    }

    private static String readHeader(String name) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        String value = attributes.getRequest().getHeader(name);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static Long readLong(String name) {
        String value = readHeader(name);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "请求头 " + name + " 格式错误");
        }
    }
}
