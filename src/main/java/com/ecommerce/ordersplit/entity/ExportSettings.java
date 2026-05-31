package com.ecommerce.ordersplit.entity;

import com.ecommerce.ordersplit.model.ExportMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 导出配置（全局单例，id 固定为 1）
 *
 * @author huangxinsong
 */
@Entity
@Table(name = "export_settings")
@Getter
@Setter
public class ExportSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExportMode mode = ExportMode.SERVER_DIRECTORY;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
