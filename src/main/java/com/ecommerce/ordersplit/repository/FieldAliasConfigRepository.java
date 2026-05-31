package com.ecommerce.ordersplit.repository;

import com.ecommerce.ordersplit.entity.FieldAliasConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 字段别名配置仓储
 *
 * @author huangxinsong
 */
public interface FieldAliasConfigRepository extends JpaRepository<FieldAliasConfig, Long> {

    Optional<FieldAliasConfig> findByFieldKey(String fieldKey);

    List<FieldAliasConfig> findAllByOrderByFieldKeyAsc();

    long count();
}
