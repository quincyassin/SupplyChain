package com.ecommerce.ordersplit.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 商家拼音工具测试
 *
 * @author huangxinsong
 */
class MerchantPinyinUtilTest {

    @Test
    void toPinyinFolder_shouldReturnLowercasePinyin() {
        assertEquals("ceshi", MerchantPinyinUtil.toPinyinFolder("测试"));
        assertEquals("shangjiaa", MerchantPinyinUtil.toPinyinFolder("商家A"));
    }

    @Test
    void toInitials_shouldReturnUppercaseInitials() {
        assertEquals("CS", MerchantPinyinUtil.toInitials("测试"));
        assertEquals("SJA", MerchantPinyinUtil.toInitials("商家A"));
    }
}
