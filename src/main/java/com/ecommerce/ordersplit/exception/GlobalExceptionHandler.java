package com.ecommerce.ordersplit.exception;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理
 *
 * @author huangxinsong
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException ex) {
    return buildError(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<Map<String, Object>> handleMaxUpload(MaxUploadSizeExceededException ex) {
    return buildError(HttpStatus.BAD_REQUEST, "上传文件超过大小限制（最大 20MB）");
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<Map<String, Object>> handleNotFound(NoResourceFoundException ex) {
    return buildError(
        HttpStatus.NOT_FOUND, "接口不存在，请确认后端已重启至最新版本: " + ex.getResourcePath());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
    return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误: " + ex.getMessage());
  }

  private ResponseEntity<Map<String, Object>> buildError(HttpStatus status, String message) {
    Map<String, Object> body = new HashMap<>();
    body.put("success", false);
    body.put("message", message);
    return ResponseEntity.status(status).body(body);
  }
}
