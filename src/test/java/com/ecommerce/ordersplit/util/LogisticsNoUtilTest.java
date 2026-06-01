package com.ecommerce.ordersplit.util;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 物流单号分隔工具测试
 *
 * @author huangxinsong
 */
class LogisticsNoUtilTest {

    @Test
    void split_shouldSupportEnglishComma() {
        assertEquals(List.of("SF111", "SF222"), LogisticsNoUtil.split("SF111,SF222"));
    }

    @Test
    void split_shouldSupportChineseComma() {
        assertEquals(List.of("YD-111", "YD-222"), LogisticsNoUtil.split("YD-111，YD-222"));
    }

    @Test
    void split_shouldSupportMixedCommas() {
        assertEquals(List.of("A", "B", "C"), LogisticsNoUtil.split("A,B，C"));
    }

    @Test
    void normalize_shouldConvertChineseCommaToEnglishComma() {
        assertEquals("YD-111,YD-222", LogisticsNoUtil.normalize("YD-111，YD-222"));
    }

    @Test
    void join_shouldUseEnglishComma() {
        assertEquals("SF111,SF222", LogisticsNoUtil.join(List.of("SF111", " SF222 ")));
    }

    @Test
    void join_shouldReturnNullForEmptyList() {
        assertNull(LogisticsNoUtil.join(List.of()));
    }
}
