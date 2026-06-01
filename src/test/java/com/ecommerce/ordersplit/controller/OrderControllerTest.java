package com.ecommerce.ordersplit.controller;

import com.ecommerce.ordersplit.dto.SplitResultResponse;
import com.ecommerce.ordersplit.service.LicenseService;
import com.ecommerce.ordersplit.service.OrderProcessService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 订单控制器测试
 *
 * @author huangxinsong
 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private OrderProcessService orderProcessService;

  @MockBean
  private LicenseService licenseService;

  @BeforeEach
  void setUpLicense() {
    when(licenseService.isLicensed()).thenReturn(true);
  }

  @Test
  void split_shouldReturnJson() throws Exception {
    SplitResultResponse response =
        new SplitResultResponse(
            1L, "2026-05-28 12:00:00", 1, 1, 1, List.of(), List.of(), List.of(), 0);
    when(orderProcessService.splitByMerchant(any(), any())).thenReturn(response);

    MockMultipartFile file =
        new MockMultipartFile(
            "file", "orders.xlsx", "application/vnd.ms-excel", new byte[] {1, 2, 3});

    mockMvc
        .perform(multipart("/api/orders/split").file(file))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.taskId").value(1))
        .andExpect(jsonPath("$.issueDate").value("2026-05-28 12:00:00"))
        .andExpect(jsonPath("$.totalRows").value(1));
  }
}
