package com.ecommerce.ordersplit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 商品供货价（按商品名称 + 规格 + 平台维护）
 *
 * @author huangxinsong
 */
@Entity
@Table(
        name = "product_supply_price",
        indexes = {
            @Index(
                    name = "uk_product_supply_price_key",
                    columnList = "product_name,spec,platform",
                    unique = true)
        })
@Getter
@Setter
public class ProductSupplyPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(nullable = false, length = 128)
    private String spec;

    @Column(nullable = false, length = 128)
    private String platform;

    @Column(name = "supply_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal supplyPrice;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
