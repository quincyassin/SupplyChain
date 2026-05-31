package com.ecommerce.ordersplit.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 下载响应头 Content-Disposition 工具（支持中文文件名）
 *
 * @author huangxinsong
 */
public final class ContentDispositionUtil {

  private ContentDispositionUtil() {}

  /**
   * 生成 attachment 响应头，兼容 Tomcat 与中文文件名
   */
  public static String attachment(String fileName) {
    String safeAsciiName = fileName.replaceAll("[^\\x20-\\x7E]", "_");
    String encoded =
        URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
    return "attachment; filename=\"" + safeAsciiName + "\"; filename*=UTF-8''" + encoded;
  }
}
