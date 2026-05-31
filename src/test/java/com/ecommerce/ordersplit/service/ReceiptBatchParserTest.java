package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 批量回单文本解析测试
 *
 * @author huangxinsong
 */
class ReceiptBatchParserTest {

    private static final String SAMPLE_SNOWFLAKE = "0123456789";
    private static final String SAMPLE_SNOWFLAKE_2 = "0123456790";
    private static final String SAMPLE_NANO_ID = "V1StGXR8Z5jdHi6B";

    @Test
    void parse_shouldSupportDefaultColumnOrder() {
        var lines =
                ReceiptBatchParser.parse(
                        SAMPLE_SNOWFLAKE
                                + "\tSF1234567890\t顺丰\n"
                                + SAMPLE_SNOWFLAKE_2 + ",SF6543210987,圆通");

        assertEquals(2, lines.size());
        assertEquals(SAMPLE_SNOWFLAKE, lines.get(0).systemNo());
        assertEquals("SF1234567890", lines.get(0).logisticsNo());
        assertEquals("顺丰", lines.get(0).logisticsCompany());
    }

    @Test
    void parse_shouldSupportShuffledColumnOrder() {
        var lines =
                ReceiptBatchParser.parse("顺丰\tSF1234567890\t" + SAMPLE_SNOWFLAKE);

        assertEquals(1, lines.size());
        assertEquals(SAMPLE_SNOWFLAKE, lines.get(0).systemNo());
        assertEquals("SF1234567890", lines.get(0).logisticsNo());
        assertEquals("顺丰", lines.get(0).logisticsCompany());
    }

    @Test
    void parse_shouldSupportFreeTextLine() {
        var lines =
                ReceiptBatchParser.parse(
                        "圆通速递 运单YT9876543210987 对应系统单号" + SAMPLE_SNOWFLAKE);

        assertEquals(1, lines.size());
        assertEquals(SAMPLE_SNOWFLAKE, lines.get(0).systemNo());
        assertEquals("YT9876543210987", lines.get(0).logisticsNo());
        assertEquals("圆通速递", lines.get(0).logisticsCompany());
    }

    @Test
    void parse_shouldSupportLegacySystemNoFormat() {
        var lines =
                ReceiptBatchParser.parse("顺丰\tSF1234567890\tSYS-20260528-000123-26");

        assertEquals(1, lines.size());
        assertEquals("sys-20260528-000123-26", lines.get(0).systemNo());
    }

    @Test
    void parse_shouldSkipHeaderLine() {
        var lines =
                ReceiptBatchParser.parse(
                        "系统单号\t物流单号\t物流公司\n" + SAMPLE_SNOWFLAKE + "\tSF1234567890\t顺丰");

        assertEquals(1, lines.size());
        assertEquals(SAMPLE_SNOWFLAKE, lines.get(0).systemNo());
    }

    @Test
    void parse_shouldSupportLegacyNanoIdFormat() {
        var lines =
                ReceiptBatchParser.parse("顺丰\tSF1234567890\t" + SAMPLE_NANO_ID);

        assertEquals(1, lines.size());
        assertEquals(SAMPLE_NANO_ID, lines.get(0).systemNo());
    }

    @Test
    void parse_shouldRejectLineWithoutThreeFields() {
        assertThrows(
                BusinessException.class,
                () -> ReceiptBatchParser.parse(SAMPLE_SNOWFLAKE + "\t仅两列"));
    }
}
