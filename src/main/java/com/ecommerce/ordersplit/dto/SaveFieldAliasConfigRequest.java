package com.ecommerce.ordersplit.dto;

import java.util.List;
import lombok.Data;

/**
 * 保存字段别名配置
 *
 * @author huangxinsong
 */
@Data
public class SaveFieldAliasConfigRequest {

    /** 别名列表（不含系统字段名本身，导入时与系统 label 一并参与匹配） */
    private List<String> aliases;
}
