package com.four_person.system.infrastructure.support;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 客户端设备信息：浏览器无法读取 MAC 地址，改由前端生成持久设备 UUID
 * 存于 localStorage，每个请求通过 X-Device-Id / X-Device-Name 头携带。
 */
public final class DeviceUtils {

    public static final String HEADER_DEVICE_ID = "X-Device-Id";
    public static final String HEADER_DEVICE_NAME = "X-Device-Name";

    private DeviceUtils() {
    }

    public static String currentDeviceId() {
        return currentHeader(HEADER_DEVICE_ID);
    }

    public static String currentDeviceName() {
        return currentHeader(HEADER_DEVICE_NAME);
    }

    public static String currentHeader(String name) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "";
        }
        HttpServletRequest request = attributes.getRequest();
        String value = request.getHeader(name);
        return value == null ? "" : value.trim();
    }
}
