package com.ecommerce.ordersplit.util;

import com.ecommerce.ordersplit.entity.ImportOrder;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 系统编号生成测试
 *
 * @author huangxinsong
 */
class SystemNoGeneratorTest {

    private static final String SAMPLE_NANO_ID = "V1StGXR8Z5jdHi6B";
    private static final String SAMPLE_UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String SAMPLE_SNOWFLAKE = "0123456789";

    @Test
    void generate_shouldReturn10DigitSnowflakeId() {
        String systemNo = SystemNoGenerator.generate();
        assertNotNull(systemNo);
        assertEquals(10, systemNo.length());
        assertTrue(systemNo.matches("\\d{10}"));
        assertTrue(SystemNoGenerator.isSystemNoFormat(systemNo));
        assertFalse(SystemNoGenerator.isLegacySystemNoFormat(systemNo));
    }

    @Test
    void generate_shouldProduceUniqueIds() {
        String first = SystemNoGenerator.generate();
        String second = SystemNoGenerator.generate();
        assertFalse(first.equals(second));
    }

    @Test
    void isSystemNoFormat_shouldRecognizeSnowflakeNanoIdUuidAndLegacy() {
        assertTrue(SystemNoGenerator.isSystemNoFormat(SAMPLE_SNOWFLAKE));
        assertTrue(SystemNoGenerator.isSystemNoFormat(SAMPLE_NANO_ID));
        assertTrue(SystemNoGenerator.isSystemNoFormat(SAMPLE_UUID));
        assertTrue(SystemNoGenerator.isSystemNoFormat("SYS-20260528-000123-26"));
        assertFalse(SystemNoGenerator.isSystemNoFormat("O001"));
        assertFalse(SystemNoGenerator.isSystemNoFormat("123456789"));
    }

    @Test
    void normalize_shouldLowerCaseLegacyOnly() {
        assertEquals(SAMPLE_SNOWFLAKE, SystemNoGenerator.normalize(SAMPLE_SNOWFLAKE));
        assertEquals(SAMPLE_NANO_ID, SystemNoGenerator.normalize(SAMPLE_NANO_ID));
        assertEquals(
                "sys-20260528-000123-26",
                SystemNoGenerator.normalize("SYS-20260528-000123-26"));
    }

    @Test
    void display_shouldPreferStoredSystemNo() {
        ImportOrder order = new ImportOrder();
        order.setIssueDate(LocalDateTime.of(2026, 5, 28, 12, 0));
        order.setSystemNo(SAMPLE_SNOWFLAKE);

        assertEquals(SAMPLE_SNOWFLAKE, SystemNoGenerator.display(order));
    }

    @Test
    void display_shouldReturnEmptyWhenMissing() {
        ImportOrder order = new ImportOrder();
        order.setIssueDate(LocalDateTime.of(2026, 5, 28, 12, 0));

        assertEquals("", SystemNoGenerator.display(order));
    }
}
