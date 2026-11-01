package com.four_person.logistics.infrastructure.support;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 从收货地址文本中识别配送区域。
 * 生产环境应改为地址库（省市区编码），这里覆盖演示数据中的城市。
 */
public final class RegionParser {

    private static final Map<String, String> DISTRICTS = new LinkedHashMap<>();

    static {
        DISTRICTS.put("朝阳区", "北京市·朝阳区");
        DISTRICTS.put("海淀区", "北京市·海淀区");
        DISTRICTS.put("东城区", "北京市·东城区");
        DISTRICTS.put("西城区", "北京市·西城区");
        DISTRICTS.put("丰台区", "北京市·丰台区");
        DISTRICTS.put("浦东新区", "上海市·浦东新区");
        DISTRICTS.put("徐汇区", "上海市·徐汇区");
        DISTRICTS.put("天河区", "广州市·天河区");
        DISTRICTS.put("越秀区", "广州市·越秀区");
    }

    private RegionParser() {
    }

    public static String parse(String address) {
        if (address == null || address.isBlank()) {
            return "";
        }
        return DISTRICTS.entrySet().stream()
                .filter(entry -> address.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("");
    }
}
