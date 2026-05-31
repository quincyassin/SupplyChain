package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.exception.BusinessException;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 浏览器导出 ZIP 短期缓存，避免 POST 统计与 GET 下载重复构建
 *
 * @author huangxinsong
 */
@Service
public class ExportDownloadCacheService {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final ConcurrentHashMap<String, CachedExport> cache = new ConcurrentHashMap<>();

    /**
     * 缓存 ZIP 并返回下载令牌
     */
    public String store(String fileName, byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new BusinessException("导出内容为空，无法下载");
        }
        purgeExpired();
        String token = UUID.randomUUID().toString().replace("-", "");
        cache.put(token, new CachedExport(fileName, zipBytes, Instant.now().plus(TTL)));
        return token;
    }

    /**
     * 取出并移除缓存（一次性下载）
     */
    public CachedExport take(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException("导出令牌无效，请重新导出");
        }
        purgeExpired();
        CachedExport cached = cache.remove(token.trim());
        if (cached == null || cached.expiresAt().isBefore(Instant.now())) {
            throw new BusinessException("导出文件已过期，请重新导出");
        }
        return cached;
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, CachedExport>> iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CachedExport> entry = iterator.next();
            if (entry.getValue().expiresAt().isBefore(now)) {
                iterator.remove();
            }
        }
    }

    public record CachedExport(String fileName, byte[] zipBytes, Instant expiresAt) {}
}
