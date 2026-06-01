package com.ecommerce.ordersplit.dto;

import lombok.Builder;
import lombok.Value;

/**
 * 授权状态
 *
 * @author huangxinsong
 */
@Value
@Builder
public class LicenseStatusDto {

    boolean licensed;
    boolean enforced;
    String machineId;
    String machineIdDisplay;
    String platform;
    String expireAt;
    Long remainingDays;
    String message;
}
