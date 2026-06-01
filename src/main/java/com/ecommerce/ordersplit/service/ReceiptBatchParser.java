package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.util.LogisticsNoUtil;
import com.ecommerce.ordersplit.util.SystemNoGenerator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final Pattern COMPANY_SUFFIX_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]{2,20}(?:快递|速递|物流)");
    private static final int MAX_LOGISTICS_NO_LENGTH = 128;

    /** 物流公司英文缩写（独立出现时识别，不破坏 YD-xxx 运单号） */
    private static final Map<String, String> LOGISTICS_COMPANY_ABBREVIATIONS = buildCompanyAbbreviations();

    private static Map<String, String> buildCompanyAbbreviations() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("SF", "顺丰");
        map.put("YT", "圆通");
        map.put("ZTO", "中通");
        map.put("YD", "韵达");
        map.put("STO", "申通");
        map.put("JT", "极兔");
        map.put("JD", "京东");
        map.put("DB", "德邦");
        map.put("EMS", "EMS");
        return Map.copyOf(map);
    }

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
        String logisticsCompany = null;
        List<String> logisticsNumbers = new ArrayList<>();

        for (String token : tokens) {
            String value = token.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (systemNo == null && SystemNoGenerator.isSystemNoFormat(value)) {
                systemNo = SystemNoGenerator.normalize(value);
                continue;
            }
            if (logisticsCompany == null && isKnownCompanyToken(value)) {
                logisticsCompany = resolveCompanyName(value);
                continue;
            }
            appendLogisticsToken(value, logisticsNumbers, systemNo);
        }

        String logisticsNo = joinLogisticsNos(logisticsNumbers);
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
        List<String> tokens = new ArrayList<>();
        if (line.contains("\t")) {
            for (String part : line.split("\\t")) {
                addTrimmedToken(tokens, part);
            }
            return tokens;
        }
        String[] byComma = line.split("[,，]");
        if (byComma.length == 3 && !line.contains(" ") && !line.contains("\t")) {
            for (String part : byComma) {
                addTrimmedToken(tokens, part);
            }
            return tokens;
        }
        for (String part : line.split("\\s+")) {
            addTrimmedToken(tokens, part);
        }
        return tokens;
    }

    private static void addTrimmedToken(List<String> tokens, String part) {
        if (part == null) {
            return;
        }
        String trimmed = part.trim();
        if (!trimmed.isEmpty()) {
            tokens.add(trimmed);
        }
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
            Optional<String> abbreviation = resolveCompanyAbbreviation(token);
            if (abbreviation.isPresent()) {
                return abbreviation.get();
            }
            if (isKnownCompanyToken(token)) {
                return matchCompany(token).orElse(token);
            }
            if (isChineseOnlyCompanyName(token)) {
                return token;
            }
        }
        return null;
    }

    private static String removeStandaloneToken(String line, String token) {
        if (line == null || token == null || token.isBlank()) {
            return line;
        }
        Pattern pattern =
                Pattern.compile(
                        "(?<![A-Za-z0-9-])" + Pattern.quote(token) + "(?![A-Za-z0-9-])",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return pattern.matcher(line).replaceAll(" ");
    }

    private static String removeStandaloneSystemNo(String line, String systemNo) {
        if (line == null || systemNo == null || systemNo.isBlank()) {
            return line;
        }
        Pattern pattern =
                Pattern.compile(
                        "(?<![A-Za-z0-9-])" + Pattern.quote(systemNo) + "(?![0-9])",
                        Pattern.CASE_INSENSITIVE);
        return pattern.matcher(line).replaceAll(" ");
    }

    private static Optional<String> resolveCompanyAbbreviation(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String normalized = token.trim().toUpperCase(Locale.ROOT);
        String company = LOGISTICS_COMPANY_ABBREVIATIONS.get(normalized);
        return company == null ? Optional.empty() : Optional.of(company);
    }

    private static String resolveCompanyName(String token) {
        return resolveCompanyAbbreviation(token).orElseGet(() -> matchCompany(token).orElse(token.trim()));
    }

    private static String findLogisticsNo(String line, String systemNo, String logisticsCompany) {
        String remainder = line;
        if (systemNo != null) {
            remainder = removeStandaloneSystemNo(remainder, systemNo);
            remainder = removeStandaloneSystemNo(remainder, systemNo.toLowerCase(Locale.ROOT));
        }
        if (logisticsCompany != null) {
            remainder = removeStandaloneToken(remainder, logisticsCompany);
        }
        for (Map.Entry<String, String> entry : LOGISTICS_COMPANY_ABBREVIATIONS.entrySet()) {
            if (entry.getValue().equals(logisticsCompany)) {
                remainder = removeStandaloneToken(remainder, entry.getKey());
            }
        }

        List<String> logisticsNumbers = new ArrayList<>();
        String normalizedRemainder = remainder == null ? "" : remainder.trim();
        if (normalizedRemainder.isEmpty()) {
            return null;
        }
        appendLogisticsToken(normalizedRemainder, logisticsNumbers, systemNo);
        return joinLogisticsNos(logisticsNumbers);
    }

    private static void appendLogisticsToken(
            String token, List<String> logisticsNumbers, String systemNo) {
        if (token == null || token.isBlank()) {
            return;
        }
        if (LogisticsNoUtil.containsSeparator(token)) {
            for (String part : LogisticsNoUtil.split(token)) {
                addLogisticsNo(logisticsNumbers, part, systemNo);
            }
            return;
        }
        for (String part : token.split("\\s+")) {
            addLogisticsNo(logisticsNumbers, part, systemNo);
        }
    }

    private static void addLogisticsNo(
            List<String> logisticsNumbers, String candidate, String systemNo) {
        if (!isLogisticsCandidate(candidate, systemNo)) {
            return;
        }
        logisticsNumbers.add(candidate.trim());
    }

    private static String joinLogisticsNos(List<String> logisticsNumbers) {
        return LogisticsNoUtil.join(logisticsNumbers);
    }

    private static boolean isLogisticsCandidate(String value, String systemNo) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_LOGISTICS_NO_LENGTH) {
            return false;
        }
        if (systemNo != null && trimmed.equalsIgnoreCase(systemNo)) {
            return false;
        }
        if (SystemNoGenerator.isSystemNoFormat(trimmed)) {
            return false;
        }
        if (isChineseOnlyCompanyName(trimmed)) {
            return false;
        }
        return !isKnownCompanyToken(trimmed);
    }

    private static boolean isKnownCompanyToken(String token) {
        String value = token.trim();
        if (value.isEmpty()) {
            return false;
        }
        if (resolveCompanyAbbreviation(value).isPresent()) {
            return true;
        }
        if (matchCompany(value).isPresent()) {
            return true;
        }
        return COMPANY_SUFFIX_PATTERN.matcher(value).matches();
    }

    private static boolean isChineseOnlyCompanyName(String token) {
        String value = token.trim();
        return containsChinese(value)
                && !value.matches(".*[A-Za-z0-9].*")
                && value.length() <= 20;
    }

    private static Optional<String> matchCompany(String text) {
        String source = text.trim();
        if (source.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> abbreviation = resolveCompanyAbbreviation(source);
        if (abbreviation.isPresent()) {
            return abbreviation;
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
