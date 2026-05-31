package com.ecommerce.ordersplit.exception;

/**
 * 业务异常
 *
 * @author huangxinsong
 */
public class BusinessException extends RuntimeException {

  public BusinessException(String message) {
    super(message);
  }
}
