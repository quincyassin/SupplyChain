package com.ecommerce.ordersplit.util;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

/**
 * 商家名称拼音与首字母缩写
 *
 * @author huangxinsong
 */
public final class MerchantPinyinUtil {

    private static final HanyuPinyinOutputFormat PINYIN_FORMAT = buildFormat();

    private MerchantPinyinUtil() {}

    /**
     * 商家拼音文件夹名（全拼小写，非汉字保留原字符）
     */
    public static String toPinyinFolder(String merchantName) {
        if (merchantName == null || merchantName.isBlank()) {
            return "merchant";
        }
        StringBuilder builder = new StringBuilder();
        for (char ch : merchantName.trim().toCharArray()) {
            appendPinyinOrChar(builder, ch, false);
        }
        String result = builder.toString().replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        return result.isBlank() ? "merchant" : result;
    }

    /**
     * 商家首字母缩写（汉字取拼音首字母大写，英文数字取大写）
     */
    public static String toInitials(String merchantName) {
        if (merchantName == null || merchantName.isBlank()) {
            return "M";
        }
        StringBuilder builder = new StringBuilder();
        for (char ch : merchantName.trim().toCharArray()) {
            if (isChinese(ch)) {
                try {
                    String[] pinyins = PinyinHelper.toHanyuPinyinStringArray(ch, PINYIN_FORMAT);
                    if (pinyins != null && pinyins.length > 0 && !pinyins[0].isEmpty()) {
                        builder.append(Character.toUpperCase(pinyins[0].charAt(0)));
                    }
                } catch (BadHanyuPinyinOutputFormatCombination ignored) {
                    // 忽略无法转换的字符
                }
            } else if (Character.isLetterOrDigit(ch)) {
                builder.append(Character.toUpperCase(ch));
            }
        }
        return builder.isEmpty() ? "M" : builder.toString();
    }

    private static void appendPinyinOrChar(StringBuilder builder, char ch, boolean uppercase) {
        if (isChinese(ch)) {
            try {
                String pinyin = PinyinHelper.toHanYuPinyinString(String.valueOf(ch), PINYIN_FORMAT, "", false);
                if (!pinyin.isBlank()) {
                    builder.append(uppercase ? pinyin.toUpperCase() : pinyin.toLowerCase());
                    return;
                }
            } catch (BadHanyuPinyinOutputFormatCombination ignored) {
                // 回退为原字符
            }
        }
        if (Character.isLetterOrDigit(ch)) {
            builder.append(uppercase ? Character.toUpperCase(ch) : Character.toLowerCase(ch));
        }
    }

    private static boolean isChinese(char ch) {
        return ch >= 0x4E00 && ch <= 0x9FA5;
    }

    private static HanyuPinyinOutputFormat buildFormat() {
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        format.setVCharType(HanyuPinyinVCharType.WITH_V);
        return format;
    }
}
