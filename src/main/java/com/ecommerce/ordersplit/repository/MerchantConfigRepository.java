package com.ecommerce.ordersplit.repository;

import com.ecommerce.ordersplit.entity.MerchantConfig;
import com.ecommerce.ordersplit.model.MerchantConfigVisibility;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 商家配置仓储
 *
 * @author huangxinsong
 */
public interface MerchantConfigRepository extends JpaRepository<MerchantConfig, Long> {

  Optional<MerchantConfig> findByName(String name);

  boolean existsByName(String name);

  /** 分单匹配：包含展示与隐藏商家 */
  List<MerchantConfig> findAllByOrderByNameAsc();

  /** 配置页列表：仅展示 VISIBLE */
  List<MerchantConfig> findAllByVisibilityOrderByIdDesc(MerchantConfigVisibility visibility);

  long countByVisibility(MerchantConfigVisibility visibility);
}
