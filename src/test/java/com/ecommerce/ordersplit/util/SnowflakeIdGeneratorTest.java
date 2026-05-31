package com.ecommerce.ordersplit.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 雪花 ID 生成器测试
 *
 * @author huangxinsong
 */
class SnowflakeIdGeneratorTest {

    @Test
    void nextSystemNo_shouldReturnFixedLengthNumericId() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator();
        String first = generator.nextSystemNo();
        String second = generator.nextSystemNo();

        assertEquals(10, first.length());
        assertEquals(10, second.length());
        assertTrue(first.matches("\\d{10}"));
        assertTrue(second.matches("\\d{10}"));
        assertTrue(Long.parseLong(first) < Long.parseLong(second));
    }

    @Test
    void nextSystemNos_shouldGenerateUniqueIdsForBulkImport() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator();
        List<String> ids = generator.nextSystemNos(3000);

        assertEquals(3000, ids.size());
        assertEquals(3000, ids.stream().distinct().count());
        ids.forEach(id -> assertTrue(id.matches("\\d{10}")));
    }

    @Test
    void nextSystemNos_shouldAdvanceSegmentWithoutWaitingForTenThousandRows() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator();
        List<String> ids = generator.nextSystemNos(10_000);

        assertEquals(10_000, ids.size());
        assertEquals(10_000, ids.stream().distinct().count());
        ids.forEach(id -> assertTrue(id.matches("\\d{10}")));

        String afterBulk = generator.nextSystemNo();
        assertTrue(afterBulk.matches("\\d{10}"));
        assertTrue(ids.stream().noneMatch(afterBulk::equals));
    }
}
