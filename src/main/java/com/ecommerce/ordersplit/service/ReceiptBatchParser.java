package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.util.SystemNoGenerator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 批量回单文本解析（正则自动识别，列顺序不固定；按系统单号匹配）
 *
 * @author huangxinsong
 */
public final class ReceiptBatchParser {

    private static final Pattern SNOWFLAKE_SYSTEM_NO_PATTERN =
            Pattern.compile("(?<![0-9])\\d{10}(?![0-9])");
    private static final Pattern NANO_ID_SYSTEM_NO_PATTERN =
            Pattern.compile("(?<![0-9A-Za-z])[0-9A-Za-z]{16}(?![0-9A-Za-z])");
    private static final Pattern UUID_SYSTEM_NO_PATTERN =
            Pattern.compile(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern LEGACY_SYSTEM_NO_PATTERN =
            Pattern.compile("SYS-\\d{8}-\\d{6}-\\d{2}", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOGISTICS_NO_EMBEDDED_PATTERN = Pattern
            .compile("(?i)(?:SF|YT|ZTO|YD|STO|JT|JD|DB|EMS)\\d{8,}");
    private static final Pattern COMPANY_SUFFIX_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]{2,20}(?:快递|速递|物流)");

    private static final List<String> KNOWN_LOGISTICS_COMPANIES = List.of(
            "顺丰速运",
            "圆通速递",
            "中通快递",
            "韵达速递",
            "申通快递",
            "极兔速递",
            "京东物流",
            "德邦快递",
            "中国邮政",
            "邮政速递",
            "百世快递",
            "丰网速运",
            "菜鸟裹裹",
            "宅急送",
            "天天快递",
            "苏宁物流",
            "安能物流",
            "顺丰",
            "圆通",
            "中通",
            "韵达",
            "申通",
            "极兔",
            "京东",
            "德邦",
            "EMS",
            "邮政",
            "百世",
            "丰网",
            "菜鸟",
            "天天",
            "安能");

    private ReceiptBatchParser() {
    }

    public record ReceiptLine(String systemNo, String logisticsNo, String logisticsCompany) {
    }

    public static List<ReceiptLine> parse(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("请输入回单数据");
        }
        String[] lines = content.split("\\R");
        List<ReceiptLine> parsed = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (isHeaderOnlyLine(line)) {
                continue;
            }
            try {
                parsed.add(parseLine(line));
            } catch (BusinessException ex) {
                throw new BusinessException("第 " + (i + 1) + " 行解析失败：" + ex.getMessage());
            }
        }
        if (parsed.isEmpty()) {
            throw new BusinessException("未解析到有效回单数据，请检查输入格式");
        }
        return parsed;
    }

    private static ReceiptLine parseLine(String line) {
        List<String> tokens = tokenize(line);
        if (tokens.size() >= 3) {
            ReceiptLine fromTokens = tryParseFromTokens(tokens);
            if (fromTokens != null) {
                return fromTokens;
            }
        }
        return parseFromFreeText(line);
    }

    private static ReceiptLine tryParseFromTokens(List<String> tokens) {
        String systemNo = null;
        String logisticsNo = null;
        String logisticsCompany = null;

        for (String token : tokens) {
            FieldType type = classifyToken(token);
            if (type == FieldType.UNKNOWN && systemNo == null) {
                String embedded = findEmbeddedSystemNo(token);
                if (embedded != null) {
                    systemNo = embedded;
                    continue;
                }
            }
            switch (type) {
                case SYSTEM_NO -> {
                    if (systemNo == null) {
                        systemNo = SystemNoGenerator.normalize(token);
                    }
                }
                case LOGISTICS_NO -> {
                    if (logisticsNo == null) {
                        logisticsNo = token.trim();
                    }
                }
                case COMPANY -> {
                    if (logisticsCompany == null) {
                        logisticsCompany = matchCompany(token).orElse(token.trim());
                    }
                }
                default -> {
                    // 忽略无法识别的片段
                }
            }
        }

        if (isFilled(systemNo, logisticsNo, logisticsCompany)) {
            return new ReceiptLine(systemNo, logisticsNo, logisticsCompany);
        }
        return null;
    }

    private static ReceiptLine parseFromFreeText(String line) {
        String systemNo = findSystemNo(line);
        String logisticsCompany = findLogisticsCompany(line);
        String logisticsNo = findLogisticsNo(line, systemNo, logisticsCompany);

        if (!isFilled(systemNo, logisticsNo, logisticsCompany)) {
            throw new BusinessException("未能识别系统单号、物流单号、物流公司，请检查该行内容");
        }
        return new ReceiptLine(systemNo, logisticsNo, logisticsCompany);
    }

    private static boolean isFilled(
            String systemNo, String logisticsNo, String logisticsCompany) {
        return systemNo != null
                && !systemNo.isBlank()
                && logisticsNo != null
                && !logisticsNo.isBlank()
                && logisticsCompany != null
                && !logisticsCompany.isBlank();
    }

    private static List<String> tokenize(String line) {
        String[] raw = line.split("[\\t,，;；|]+");
        if (raw.length < 2) {
            raw = line.split("\\s+");
        }
        List<String> tokens = new ArrayList<>();
        for (String part : raw) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                tokens.add(trimmed);
            }
        }
        return tokens;
    }

    private enum FieldType {
        SYSTEM_NO,
        LOGISTICS_NO,
        COMPANY,
        UNKNOWN
    }

    private static FieldType classifyToken(String token) {
        String value = token.trim();
        if (value.isEmpty()) {
            return FieldType.UNKNOWN;
        }
        if (SystemNoGenerator.isSystemNoFormat(value)) {
            return FieldType.SYSTEM_NO;
        }
        if (matchCompany(value).isPresent()) {
            return FieldType.COMPANY;
        }
        if (COMPANY_SUFFIX_PATTERN.matcher(value).matches()) {
            return FieldType.COMPANY;
        }
        if (isLogisticsNo(value)) {
            return FieldType.LOGISTICS_NO;
        }
        if (containsChinese(value) && value.length() <= 20) {
            return FieldType.COMPANY;
        }
        return FieldType.UNKNOWN;
    }

    private static String findEmbeddedSystemNo(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher snowflakeMatcher = SNOWFLAKE_SYSTEM_NO_PATTERN.matcher(text);
        if (snowflakeMatcher.find()) {
            return SystemNoGenerator.normalize(snowflakeMatcher.group());
        }
        Matcher matcher = NANO_ID_SYSTEM_NO_PATTERN.matcher(text);
        if (matcher.find()) {
            return SystemNoGenerator.normalize(matcher.group());
        }
        return null;
    }

    private static String findSystemNo(String line) {
        Matcher legacyMatcher = LEGACY_SYSTEM_NO_PATTERN.matcher(line);
        if (legacyMatcher.find()) {
            return SystemNoGenerator.normalize(legacyMatcher.group());
        }
        Matcher uuidMatcher = UUID_SYSTEM_NO_PATTERN.matcher(line);
        if (uuidMatcher.find()) {
            return SystemNoGenerator.normalize(uuidMatcher.group());
        }
        Matcher snowflakeMatcher = SNOWFLAKE_SYSTEM_NO_PATTERN.matcher(line);
        if (snowflakeMatcher.find()) {
            return SystemNoGenerator.normalize(snowflakeMatcher.group());
        }
        Matcher nanoIdMatcher = NANO_ID_SYSTEM_NO_PATTERN.matcher(line);
        if (nanoIdMatcher.find()) {
            return SystemNoGenerator.normalize(nanoIdMatcher.group());
        }
        for (String token : tokenize(line)) {
            if (SystemNoGenerator.isSystemNoFormat(token)) {
                return SystemNoGenerator.normalize(token);
            }
        }
        return null;
    }

    private static String findLogisticsCompany(String line) {
        Optional<String> known = matchCompany(line);
        if (known.isPresent()) {
            return known.get();
        }
        Matcher suffixMatcher = COMPANY_SUFFIX_PATTERN.matcher(line);
        if (suffixMatcher.find()) {
            return suffixMatcher.group();
        }
        for (String token : tokenize(line)) {
            if (SystemNoGenerator.isSystemNoFormat(token)) {
                continue;
            }
            if (containsChinese(token) && token.length() <= 20) {
                return token;
            }
        }
        return null;
    }

    private static String findLogisticsNo(String line, String systemNo, String logisticsCompany) {
        String remainder = line;
        if (systemNo != null) {
            remainder = remainder.replace(systemNo, " ");
            remainder = remainder.replace(systemNo.toLowerCase(Locale.ROOT), " ");
        }
        if (logisticsCompany != null) {
            remainder = remainder.replace(logisticsCompany, " ");
        }

        Matcher embeddedMatcher = LOGISTICS_NO_EMBEDDED_PATTERN.matcher(remainder);
        if (embeddedMatcher.find()) {
            return embeddedMatcher.group();
        }

        Matcher matcher = Pattern.compile("(?i)[A-Za-z]{2,6}\\d{8,}").matcher(remainder);
        String best = null;
        while (matcher.find()) {
            String candidate = matcher.group();
            if (isLogisticsNo(candidate) && !candidate.equalsIgnoreCase(systemNo)) {
                if (best == null || candidate.length() > best.length()) {
                    best = candidate;
                }
            }
        }
        if (best != null) {
            return best;
        }

        for (String token : tokenize(remainder)) {
            if (isLogisticsNo(token) && !token.equalsIgnoreCase(systemNo)) {
                return token;
            }
        }
        return null;
    }

    private static boolean isLogisticsNo(String value) {
        String trimmed = value.trim();
        if (trimmed.length() < 8 || trimmed.length() > 32) {
            return false;
        }
        if (SystemNoGenerator.isSystemNoFormat(trimmed)) {
            return false;
        }
        if (matchCompany(trimmed).isPresent()) {
            return false;
        }
        if (containsChinese(trimmed)) {
            return false;
        }
        return trimmed.matches("[A-Za-z0-9]+");
    }

    private static Optional<String> matchCompany(String text) {
        String source = text.trim();
        if (source.isEmpty()) {
            return Optional.empty();
        }
        return KNOWN_LOGISTICS_COMPANIES.stream()
                .filter(source::contains)
                .max(Comparator.comparingInt(String::length));
    }

    private static boolean containsChinese(String value) {
        return value.chars().anyMatch(ch -> ch >= 0x4E00 && ch <= 0x9FFF);
    }

    private static boolean isHeaderOnlyLine(String line) {
        boolean hasSystemNoHint = line.contains("系统单号") || line.contains("系统编号");
        if (!hasSystemNoHint) {
            return false;
        }
        boolean hasLogisticsHint = line.contains("物流单号") || line.contains("物流");
        boolean hasCompanyHint = line.contains("物流公司") || line.contains("公司");
        if (!hasLogisticsHint || !hasCompanyHint) {
            return false;
        }
        return findSystemNo(line) == null && findLogisticsNo(line, null, null) == null;
    }
}
