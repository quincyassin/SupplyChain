package com.ecommerce.ordersplit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.ecommerce.ordersplit.entity.ImportOrder;
import com.ecommerce.ordersplit.model.ImportOrderReceiptStatus;
import com.ecommerce.ordersplit.util.SqlLikeUtil;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 验证商品搜索原生 SQL 在 MySQL 下可正确绑定两个 LIKE 参数
 *
 * @author huangxinsong
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderProductCatalogServiceSqlIntegrationTest {

    @Autowired private EntityManager entityManager;

    @Autowired private OrderProductCatalogService orderProductCatalogService;

    @Test
    @Transactional
    void listDistinctProducts_shouldFilterByKeywordWithoutParameterBindingError() {
        ImportOrder order = new ImportOrder();
        order.setSystemNo("1000000001");
        order.setTaskId(1L);
        order.setMerchant("商家A");
        order.setPlatform("淘宝");
        order.setProductName("测试商品");
        order.setSpec("默认");
        order.setIssueDate(LocalDateTime.of(2026, 1, 1, 0, 0));
        order.setReceiptStatus(ImportOrderReceiptStatus.PENDING);
        entityManager.persist(order);
        entityManager.flush();

        String pattern = SqlLikeUtil.toContainsPattern("测试");
        var keys = orderProductCatalogService.listDistinctProducts(pattern);

        assertFalse(keys.isEmpty());
        assertEquals("测试商品", keys.get(0).productName());
    }
}
