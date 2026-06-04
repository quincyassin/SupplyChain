package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.DailyTableRowDto;
import com.ecommerce.ordersplit.model.OrderRow;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 当日发单表格生成
 *
 * @author huangxinsong
 */
@Service
public class DailyTableService {

  private static final DateTimeFormatter ISSUE_DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  public LocalDateTime currentIssueDateTime() {
    return LocalDateTime.now();
  }

  public String currentIssueDateText() {
    return formatIssueDate(currentIssueDateTime());
  }

  public String formatIssueDate(LocalDateTime issueDateTime) {
    return issueDateTime.format(ISSUE_DATE_FORMAT);
  }

  public List<DailyTableRowDto> buildDailyTable(List<OrderRow> sourceRows) {
    return buildDailyTable(sourceRows, currentIssueDateTime());
  }

  public List<DailyTableRowDto> buildDailyTable(
      List<OrderRow> sourceRows, LocalDateTime issueDateTime) {
    String issueDate = formatIssueDate(issueDateTime);
    List<DailyTableRowDto> rows = new ArrayList<>();
    for (OrderRow source : sourceRows) {
      rows.add(
          DailyTableRowDto.builder()
              .orderNo(nullToEmpty(source.getOrderNo()))
              .productName(nullToEmpty(source.getProductName()))
              .spec(nullToEmpty(source.getSku()))
              .quantity(source.getQuantity() == null ? 0 : source.getQuantity())
              .receiver(nullToEmpty(source.getReceiver()))
              .address(nullToEmpty(source.getAddress()))
              .phone(nullToEmpty(source.getPhone()))
              .shippingFee(source.getShippingFee() == null ? BigDecimal.ZERO : source.getShippingFee())
              .remark(nullToEmpty(source.getRemark()))
              .logisticsNo(nullToEmpty(source.getLogisticsNo()))
              .logisticsCompany(nullToEmpty(source.getLogisticsCompany()))
              .issueDate(issueDate)
              .build());
    }
    return rows;
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
