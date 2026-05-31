package com.ecommerce.ordersplit.util;

import com.ecommerce.ordersplit.exception.BusinessException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 10 位十进制系统编号雪花算法生成器（单机）
 * <p>
 * 位布局（十进制最大值 9999999999）：
 * <ul>
 *   <li>22 位：自自定义纪元起的分钟号段</li>
 *   <li>12 位：同一号段内自增序列（0-4095，每号段 4096 个）</li>
 * </ul>
 * <p>
 * 同一号段序列用尽时，立即切换到下一号段（{@code lastTimestamp + 1}），不等待真实时钟，
 * 适用于偶发大批量导入。之后正常发号从 {@code max(真实分钟, 已消费号段)} 继续，避免回退重复。
 *
 * @author huangxinsong
 */
public final class SnowflakeIdGenerator {

    /** 自定义纪元：2024-01-01 00:00:00（上海时区） */
    private static final long EPOCH_MILLIS =
            java.time.LocalDateTime.of(2024, 1, 1, 0, 0)
                    .atZone(ZoneId.of("Asia/Shanghai"))
                    .toInstant()
                    .toEpochMilli();

    private static final long SEQUENCE_BITS = 12L;
    private static final long TIMESTAMP_BITS = 22L;

    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS;
    private static final long MAX_TIMESTAMP = ~(-1L << TIMESTAMP_BITS);

    private static final SnowflakeIdGenerator INSTANCE = new SnowflakeIdGenerator();

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    SnowflakeIdGenerator() {}

    public static SnowflakeIdGenerator getInstance() {
        return INSTANCE;
    }

    /**
     * 生成 10 位数字系统编号（左侧不足补零）
     */
    public synchronized String nextSystemNo() {
        return formatId(nextId());
    }

    /**
     * 批量生成系统编号（单次 synchronized，号段不足时自动切下一号段）
     */
    public synchronized List<String> nextSystemNos(int count) {
        if (count <= 0) {
            return List.of();
        }
        List<String> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(formatId(nextId()));
        }
        return ids;
    }

    private long nextId() {
        long realMinute = currentMinute();
        if (realMinute > MAX_TIMESTAMP) {
            throw new BusinessException("系统编号时间戳已耗尽，请联系管理员");
        }

        long timestamp = lastTimestamp < 0 ? realMinute : Math.max(realMinute, lastTimestamp);
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                timestamp = advanceSegment(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return (timestamp << TIMESTAMP_SHIFT) | sequence;
    }

    private long advanceSegment(long currentSegment) {
        long nextSegment = currentSegment + 1;
        if (nextSegment > MAX_TIMESTAMP) {
            throw new BusinessException("系统编号号段已耗尽，请联系管理员");
        }
        return nextSegment;
    }

    private static String formatId(long id) {
        return String.format("%010d", id);
    }

    private static long currentMinute() {
        return (System.currentTimeMillis() - EPOCH_MILLIS) / 60_000L;
    }
}
