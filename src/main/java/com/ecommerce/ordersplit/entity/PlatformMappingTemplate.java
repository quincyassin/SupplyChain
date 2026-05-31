package com.ecommerce.ordersplit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 平台表头导入模板
 *
 * @author huangxinsong
 */
@Entity
@Table(name = "platform_mapping_template")
@Getter
@Setter
public class PlatformMappingTemplate {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 128)
  private String platform;

  @Column(name = "mapping_json", nullable = false, columnDefinition = "TEXT")
  private String mappingJson;

  @Column(name = "template_headers_json", columnDefinition = "TEXT")
  private String templateHeadersJson;

  @Column(name = "template_file_name", length = 255)
  private String templateFileName;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
