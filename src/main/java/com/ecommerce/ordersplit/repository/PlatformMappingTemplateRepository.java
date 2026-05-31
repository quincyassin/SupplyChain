package com.ecommerce.ordersplit.repository;

import com.ecommerce.ordersplit.entity.PlatformMappingTemplate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 平台表头模板仓储
 *
 * @author huangxinsong
 */
public interface PlatformMappingTemplateRepository
    extends JpaRepository<PlatformMappingTemplate, Long> {

  Optional<PlatformMappingTemplate> findByPlatform(String platform);

  boolean existsByPlatform(String platform);

  List<PlatformMappingTemplate> findAllByOrderByPlatformAsc();
}
