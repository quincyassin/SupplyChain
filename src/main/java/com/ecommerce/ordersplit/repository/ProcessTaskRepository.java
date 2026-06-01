package com.ecommerce.ordersplit.repository;

import com.ecommerce.ordersplit.entity.ProcessTask;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 处理任务仓储
 *
 * @author huangxinsong
 */
public interface ProcessTaskRepository extends JpaRepository<ProcessTask, Long> {

  List<ProcessTask> findTop20ByOrderByCreatedAtDesc();
}
