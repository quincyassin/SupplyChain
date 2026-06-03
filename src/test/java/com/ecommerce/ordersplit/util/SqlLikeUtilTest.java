package com.ecommerce.ordersplit.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * SQL LIKE 工具测试
 *
 * @author huangxinsong
 */
class SqlLikeUtilTest {

    @Test
    void toContainsPattern_shouldWrapKeyword() {
        assertEquals("%测试%", SqlLikeUtil.toContainsPattern("测试"));
    }

    @Test
    void toContainsPattern_shouldEscapeWildcards() {
        assertEquals("%100\\%%", SqlLikeUtil.toContainsPattern("100%"));
        assertEquals("%a\\_b%", SqlLikeUtil.toContainsPattern("a_b"));
    }

    @Test
    void toContainsPattern_shouldEscapeBackslash() {
        assertEquals("%\\\\%", SqlLikeUtil.toContainsPattern("\\"));
        assertEquals("%a\\\\b%", SqlLikeUtil.toContainsPattern("a\\b"));
    }

    @Test
    void toContainsPattern_shouldReturnNullForBlank() {
        assertNull(SqlLikeUtil.toContainsPattern(null));
        assertNull(SqlLikeUtil.toContainsPattern("   "));
    }
}
