package com.ecommerce.ordersplit.entity;

import com.ecommerce.ordersplit.model.MerchantConfigVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 商家配置（名称 + 商品名称匹配关键字）
 *
 * @author huangxinsong
 */
@Entity
@Table(name = "merchant_config")
@Getter
@Setter
public class MerchantConfig {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 128)
  private String name;

  @Column(name = "keywords_json", nullable = false, columnDefinition = "TEXT")
  private String keywordsJson;

  /** 可见性：VISIBLE 展示在配置页，HIDDEN 为表格手工维护 */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MerchantConfigVisibility visibility = MerchantConfigVisibility.VISIBLE;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
