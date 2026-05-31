package com.ecommerce.ordersplit.repository;

import com.ecommerce.ordersplit.entity.ExportSettings;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 导出配置仓储
 *
 * @author huangxinsong
 */
public interface ExportSettingsRepository extends JpaRepository<ExportSettings, Long> {}
