package com.ecommerce.ordersplit.license;

import java.time.LocalDate;

/**
 * 激活码签名载荷
 *
 * @author huangxinsong
 */
public record LicensePayload(int version, String machineId, LocalDate expireAt) {

    public static final int CURRENT_VERSION = 1;

    public LicensePayload(String machineId, LocalDate expireAt) {
        this(CURRENT_VERSION, machineId, expireAt);
    }
}
