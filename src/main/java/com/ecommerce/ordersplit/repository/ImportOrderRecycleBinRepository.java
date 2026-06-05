package com.ecommerce.ordersplit.repository;

import com.ecommerce.ordersplit.entity.ImportOrderRecycleBin;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 回收站订单仓储
 *
 * @author huangxinsong
 */
public interface ImportOrderRecycleBinRepository
        extends JpaRepository<ImportOrderRecycleBin, String> {

    List<ImportOrderRecycleBin> findBySystemNoInOrderByDeletedAtDescSystemNoDesc(
            Collection<String> systemNos);
}
