package com.ecommerce.ordersplit.entity;

import com.ecommerce.ordersplit.model.OperationType;
import com.ecommerce.ordersplit.model.TaskStatus;
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
import org.hibernate.annotations.CreationTimestamp;

/**
 * 分单合单处理任务
 *
 * @author huangxinsong
 */
@Entity
@Table(name = "process_task")
@Getter
@Setter
public class ProcessTask {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 255)
  private String originalFileName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OperationType operationType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TaskStatus status;

  @Column(length = 500)
  private String message;

  /** 输入行数 */
  private Integer inputRowCount;

  /** 输出分组数（商家数） */
  private Integer merchantGroupCount;

  /** 输出行数 */
  private Integer outputRowCount;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
