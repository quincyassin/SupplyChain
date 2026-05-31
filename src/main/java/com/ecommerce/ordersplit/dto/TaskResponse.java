package com.ecommerce.ordersplit.dto;

import com.ecommerce.ordersplit.model.OperationType;
import com.ecommerce.ordersplit.model.TaskStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 任务响应
 *
 * @author huangxinsong
 */
@Data
@Builder
public class TaskResponse {

  private Long taskId;
  private String originalFileName;
  private OperationType operationType;
  private TaskStatus status;
  private String message;
  private Integer inputRowCount;
  private Integer merchantGroupCount;
  private Integer outputRowCount;
  private LocalDateTime createdAt;
}
