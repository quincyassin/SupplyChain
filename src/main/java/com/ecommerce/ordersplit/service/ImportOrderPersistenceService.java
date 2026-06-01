package com.ecommerce.ordersplit.service;

import com.ecommerce.ordersplit.dto.AssignMerchantPersistenceResult;
import com.ecommerce.ordersplit.dto.DailyTableRowDto;
import com.ecommerce.ordersplit.dto.UpdateImportedOrderFieldsRequest;
import com.ecommerce.ordersplit.entity.ImportOrder;
import com.ecommerce.ordersplit.exception.BusinessException;
import com.ecommerce.ordersplit.model.AfterSalesStatus;
import com.ecommerce.ordersplit.model.ImportOrderReceiptStatus;
import com.ecommerce.ordersplit.model.OrderRow;
import com.ecommerce.ordersplit.repository.ImportOrderRepository;
import com.ecommerce.ordersplit.util.LogisticsNoUtil;
import com.ecommerce.ordersplit.util.PhoneImportValidator;
import com.ecommerce.ordersplit.util.SystemNoGenerator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 导入订单持久化
 *
 * @author huangxinsong
 */
@Service
@RequiredArgsConstructor
public class ImportOrderPersistenceService {

    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final int AFTER_SALES_REMARK_MAX_LENGTH = 512;
    private static final int PHONE_MAX_LENGTH = 32;

    private final ImportOrderRepository importOrderRepository;
    private final DailyTableService dailyTableService;
    private final MerchantConfigService merchantConfigService;
    private final OrderSplitMergeService orderSplitMergeService;
    private final ProductPriceService productPriceService;

    /**
     * 将分单结果追加写入 import_order 表（多次导入叠加，不覆盖历史）
     */
    @Transactional
    public int saveSplitOrders(
            Long taskId,
            String platform,
            Map<String, List<OrderRow>> splitResult,
            LocalDateTime issueDateTime) {
        String normalizedPlatform = normalizePlatform(platform);
        List<ImportOrder> entities = new ArrayList<>();
        for (Map.Entry<String, List<OrderRow>> entry : splitResult.entrySet()) {
            String merchant = entry.getKey();
            List<OrderRow> sourceRows = entry.getValue();
            List<DailyTableRowDto> displayRows = dailyTableService.buildDailyTable(sourceRows, issueDateTime);
            for (int i = 0; i < sourceRows.size(); i++) {
                OrderRow source = sourceRows.get(i);
                DailyTableRowDto display = displayRows.get(i);
                entities.add(
                        toEntity(taskId, normalizedPlatform, merchant, source, display, issueDateTime));
            }
        }
        ProductPriceService.ImportPriceLookup priceLookup =
                productPriceService.buildLookupForImport(entities);
        List<String> systemNos = SystemNoGenerator.generateBatch(entities.size());
        for (int i = 0; i < entities.size(); i++) {
            ImportOrder entity = entities.get(i);
            entity.setSystemNo(systemNos.get(i));
            productPriceService.applyConfiguredPrices(entity, priceLookup);
        }
        importOrderRepository.saveAll(entities);
        return entities.size();
    }

    /**
     * 删除指定日期已入库的单条订单（须在 recently 窗口内）
     */
    @Transactional
    public void deleteOrderForDate(String systemNo, LocalDate date) {
        deleteOrdersForDate(List.of(systemNo), date);
    }

    /**
     * 批量删除指定日期已入库订单
     */
    @Transactional
    public int deleteOrdersForDate(List<String> systemNos, LocalDate date) {
        if (systemNos == null || systemNos.isEmpty()) {
            throw new BusinessException("请先勾选要删除的订单");
        }
        if (date == null) {
            throw new BusinessException("日期参数无效");
        }
        Set<String> distinctSystemNos = new LinkedHashSet<>();
        for (String systemNo : systemNos) {
            distinctSystemNos.add(SystemNoGenerator.requireValid(systemNo));
        }
        List<ImportOrder> entities = importOrderRepository
                .findBySystemNoInOrderByMerchantAscSystemNoAsc(new ArrayList<>(distinctSystemNos));
        if (entities.isEmpty()) {
            throw new BusinessException("未找到选中的订单，请刷新后重试");
        }
        if (entities.size() != distinctSystemNos.size()) {
            throw new BusinessException("部分选中订单不存在或已删除，请刷新后重选");
        }
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        for (ImportOrder entity : entities) {
            LocalDateTime issueDate = entity.getIssueDate();
            if (issueDate == null || issueDate.isBefore(start) || !issueDate.isBefore(end)) {
                throw new BusinessException("只能删除所选日期当天的订单");
            }
        }
        importOrderRepository.deleteAll(entities);
        return entities.size();
    }

    /**
     * 手动修改单条订单商家（仅更新 import_order，不写入商家配置）
     */
    @Transactional
    public void updateOrderMerchant(String systemNo, LocalDate date, String merchantName) {
        if (systemNo == null || systemNo.isBlank()) {
            throw new BusinessException("系统编号无效");
        }
        if (date == null) {
            throw new BusinessException("日期参数无效");
        }
        String merchant = normalizeManualMerchant(merchantName);
        ImportOrder entity = requireOrderForDate(systemNo, date);
        entity.setMerchant(merchant);
        entity.setMerchantSplit(true);
        importOrderRepository.save(entity);
        merchantConfigService.ensureManualMerchant(merchant);
    }

    /**
     * 标记单条订单需售后
     */
    @Transactional
    public void markOrderAfterSales(String systemNo, LocalDate date, String remark) {
        if (systemNo == null || systemNo.isBlank()) {
            throw new BusinessException("系统编号无效");
        }
        if (date == null) {
            throw new BusinessException("日期参数无效");
        }
        String normalizedRemark = normalizeAfterSalesRemark(remark);
        ImportOrder entity = requireOrderForDate(systemNo, date);
        AfterSalesStatus status = entity.getAfterSalesStatus();
        if (status == AfterSalesStatus.PENDING) {
            throw new BusinessException("该订单已在售后处理中，请先取消或完结后再发起");
        }
        entity.setAfterSales(true);
        entity.setAfterSalesStatus(AfterSalesStatus.PENDING);
        entity.setAfterSalesRemark(normalizedRemark);
        entity.setAfterSalesAt(LocalDateTime.now(ZONE_SHANGHAI));
        importOrderRepository.save(entity);
    }

    /**
     * 标记单条订单售后完结
     */
    @Transactional
    public void completeOrderAfterSales(String systemNo, LocalDate date) {
        if (systemNo == null || systemNo.isBlank()) {
            throw new BusinessException("系统编号无效");
        }
        if (date == null) {
            throw new BusinessException("日期参数无效");
        }
        ImportOrder entity = requireOrderForDate(systemNo, date);
        AfterSalesStatus status = entity.getAfterSalesStatus();
        if (status != AfterSalesStatus.PENDING) {
            throw new BusinessException("仅「需售后」订单可标记完结");
        }
        entity.setAfterSales(true);
        entity.setAfterSalesStatus(AfterSalesStatus.COMPLETED);
        importOrderRepository.save(entity);
    }

    /**
     * 取消单条订单售后标记
     */
    @Transactional
    public void cancelOrderAfterSales(String systemNo, LocalDate date) {
        if (systemNo == null || systemNo.isBlank()) {
            throw new BusinessException("系统编号无效");
        }
        if (date == null) {
            throw new BusinessException("日期参数无效");
        }
        ImportOrder entity = requireOrderForDate(systemNo, date);
        AfterSalesStatus status = entity.getAfterSalesStatus();
        if (status != AfterSalesStatus.PENDING) {
            throw new BusinessException("仅「需售后」订单可取消售后");
        }
        entity.setAfterSales(false);
        entity.setAfterSalesStatus(AfterSalesStatus.NONE);
        entity.setAfterSalesRemark(null);
        entity.setAfterSalesAt(null);
        importOrderRepository.save(entity);
    }

    /**
     * 手动修改单条订单可编辑字段
     */
    @Transactional
    public void updateOrderFields(
            String systemNo, LocalDate date, UpdateImportedOrderFieldsRequest request) {
        if (systemNo == null || systemNo.isBlank()) {
            throw new BusinessException("系统编号无效");
        }
        if (date == null) {
            throw new BusinessException("日期参数无效");
        }
        if (request == null || !hasEditableField(request)) {
            throw new BusinessException("请提供要修改的字段");
        }
        ImportOrder entity = requireOrderForDate(systemNo, date);
        if (request.getOrderNo() != null) {
            entity.setOrderNo(normalizeOptionalField(request.getOrderNo(), 64, "订单编号"));
        }
        if (request.getLogisticsNo() != null) {
            entity.setLogisticsNo(normalizeLogisticsNo(request.getLogisticsNo()));
        }
        if (request.getLogisticsCompany() != null) {
            entity.setLogisticsCompany(
                    normalizeOptionalField(request.getLogisticsCompany(), 128, "物流公司"));
        }
        if (request.getReceiver() != null) {
            entity.setReceiver(normalizeOptionalField(request.getReceiver(), 64, "收货人"));
        }
        if (request.getPhone() != null) {
            entity.setPhone(normalizeOptionalField(request.getPhone(), PHONE_MAX_LENGTH, "收货人电话"));
        }
        if (request.getAddress() != null) {
            entity.setAddress(normalizeOptionalField(request.getAddress(), 512, "收货人地址"));
        }
        if (request.getShippingFee() != null) {
            entity.setShippingFee(normalizeShippingFee(request.getShippingFee()));
        }
        if (request.getCostPrice() != null) {
            productPriceService.saveCostPriceAndPropagate(entity, request.getCostPrice());
            entity.setCostPrice(normalizeProductPrice(request.getCostPrice(), "成本价"));
        }
        if (request.getSupplyPrice() != null) {
            productPriceService.saveSupplyPriceAndPropagate(entity, request.getSupplyPrice());
            entity.setSupplyPrice(normalizeProductPrice(request.getSupplyPrice(), "供货价"));
        }
        if (request.getRemark() != null) {
            entity.setRemark(normalizeOptionalField(request.getRemark(), 512, "备注"));
        }
        if (request.getLogisticsNo() != null || request.getLogisticsCompany() != null) {
            applyReceiptStatusIfLogisticsComplete(entity);
        }
        importOrderRepository.save(entity);
    }

    /**
     * 对指定分单日期区间内订单分单：已有商家的订单保留不动，其余按关键字匹配；全部参与导出
     */
    @Transactional
    public AssignMerchantPersistenceResult assignPendingMerchantsInRange(
            LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new BusinessException("开始日期不能晚于结束日期");
        }
        LocalDateTime rangeStart = startDate.atStartOfDay();
        LocalDateTime rangeEndExclusive = endDate.plusDays(1).atStartOfDay();
        List<ImportOrder> ordersInRange =
                importOrderRepository
                        .findByIssueDateGreaterThanEqualAndIssueDateLessThanOrderByPlatformAscMerchantAscSystemNoAsc(
                                rangeStart, rangeEndExclusive);
        if (ordersInRange.isEmpty()) {
            throw new BusinessException("所选日期区间内没有订单，请先上传 Excel");
        }

        List<ImportOrder> pendingOrders = new ArrayList<>();
        List<OrderRow> rowsForSplit = new ArrayList<>();
        int unmatchedPendingCount = 0;
        for (ImportOrder order : ordersInRange) {
            if (hasAssignedMerchant(order)) {
                continue;
            }
            pendingOrders.add(order);
            OrderRow row = toOrderRow(order);
            String merchant = merchantConfigService.resolveByProductName(row.getProductName());
            if (MerchantConfigService.UNMATCHED_MERCHANT_NAME.equals(merchant)) {
                row.setMerchant(MerchantConfigService.PENDING_SPLIT_MERCHANT);
                unmatchedPendingCount++;
            } else {
                row.setMerchant(merchant);
            }
            rowsForSplit.add(row);
        }

        if (!rowsForSplit.isEmpty()) {
            Map<String, List<OrderRow>> splitResult =
                    orderSplitMergeService.groupByMerchant(rowsForSplit);
            Map<String, OrderRow> splitBySystemNo = new HashMap<>();
            for (List<OrderRow> rows : splitResult.values()) {
                for (OrderRow row : rows) {
                    if (row.getSystemNo() != null && !row.getSystemNo().isBlank()) {
                        splitBySystemNo.put(row.getSystemNo(), row);
                    }
                }
            }

            for (ImportOrder entity : pendingOrders) {
                OrderRow assigned = splitBySystemNo.get(entity.getSystemNo());
                if (assigned == null) {
                    continue;
                }
                entity.setMerchant(assigned.getMerchant());
                entity.setMerchantSplit(true);
            }
            importOrderRepository.saveAll(pendingOrders);
        }
        return new AssignMerchantPersistenceResult(
                ordersInRange.size(), unmatchedPendingCount, ordersInRange);
    }

    private boolean hasAssignedMerchant(ImportOrder order) {
        if (order == null) {
            return false;
        }
        String merchant = order.getMerchant();
        if (merchant == null || merchant.isBlank()) {
            return false;
        }
        String trimmed = merchant.trim();
        return !MerchantConfigService.PENDING_SPLIT_MERCHANT.equals(trimmed)
                && !MerchantConfigService.UNMATCHED_MERCHANT_NAME.equals(trimmed);
    }

    /**
     * @deprecated 使用 {@link #assignPendingMerchantsInRange(LocalDate, LocalDate)}
     */
    @Deprecated
    @Transactional
    public AssignMerchantPersistenceResult assignAllPendingMerchants() {
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        return assignPendingMerchantsInRange(today, today);
    }

    @Deprecated
    @Transactional
    public AssignMerchantPersistenceResult assignMerchantsForDate(LocalDate date) {
        return assignPendingMerchantsInRange(date, date);
    }

    private OrderRow toOrderRow(ImportOrder order) {
        return OrderRow.builder()
                .orderNo(order.getOrderNo())
                .merchant(order.getMerchant())
                .productName(order.getProductName())
                .sku(order.getSpec())
                .quantity(order.getQuantity())
                .receiver(order.getReceiver())
                .address(order.getAddress())
                .phone(order.getPhone())
                .shippingFee(order.getShippingFee())
                .sourceRowNum(order.getSourceRowNum() == null ? 0 : order.getSourceRowNum())
                .systemNo(order.getSystemNo())
                .build();
    }

    private String normalizePlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            throw new BusinessException("平台信息缺失，无法保存分单数据");
        }
        return platform.trim();
    }

    private String normalizeManualMerchant(String merchantName) {
        if (merchantName == null || merchantName.isBlank()) {
            throw new BusinessException("商家名称不能为空");
        }
        String trimmed = merchantName.trim();
        if (trimmed.length() > 128) {
            throw new BusinessException("商家名称不能超过 128 个字符");
        }
        if (MerchantConfigService.PENDING_SPLIT_MERCHANT.equals(trimmed)) {
            throw new BusinessException("请填写具体商家名称");
        }
        return trimmed;
    }

    private String normalizeAfterSalesRemark(String remark) {
        if (remark == null || remark.isBlank()) {
            throw new BusinessException("请填写售后原因");
        }
        String trimmed = remark.trim();
        if (trimmed.length() > AFTER_SALES_REMARK_MAX_LENGTH) {
            throw new BusinessException("售后原因不能超过 " + AFTER_SALES_REMARK_MAX_LENGTH + " 个字符");
        }
        return trimmed;
    }

    private ImportOrder requireOrderForDate(String systemNo, LocalDate date) {
        String normalizedSystemNo = SystemNoGenerator.requireValid(systemNo);
        ImportOrder entity = importOrderRepository
                .findById(normalizedSystemNo)
                .orElseThrow(() -> new BusinessException("订单不存在"));
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        LocalDateTime issueDate = entity.getIssueDate();
        if (issueDate == null || issueDate.isBefore(start) || !issueDate.isBefore(end)) {
            throw new BusinessException("只能修改所选日期当天的订单");
        }
        return entity;
    }

    private boolean hasEditableField(UpdateImportedOrderFieldsRequest request) {
        return request.getOrderNo() != null
                || request.getLogisticsNo() != null
                || request.getLogisticsCompany() != null
                || request.getReceiver() != null
                || request.getPhone() != null
                || request.getAddress() != null
                || request.getShippingFee() != null
                || request.getCostPrice() != null
                || request.getSupplyPrice() != null
                || request.getRemark() != null;
    }

    private String normalizeLogisticsNo(String value) {
        String normalized = LogisticsNoUtil.normalize(value);
        return normalizeOptionalField(normalized, 128, "物流单号");
    }

    private String normalizeOptionalField(String value, int maxLength, String label) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BusinessException(label + "不能超过 " + maxLength + " 个字符");
        }
        return trimmed;
    }

    private BigDecimal normalizeShippingFee(BigDecimal shippingFee) {
        if (shippingFee == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (shippingFee.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("运费不能为负数");
        }
        if (shippingFee.precision() - shippingFee.scale() > 10) {
            throw new BusinessException("运费整数部分不能超过 10 位");
        }
        return shippingFee.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeProductPrice(BigDecimal price, String label) {
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(label + "不能为负数");
        }
        if (price.precision() - price.scale() > 10) {
            throw new BusinessException(label + "整数部分不能超过 10 位");
        }
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    private void applyReceiptStatusIfLogisticsComplete(ImportOrder entity) {
        if (hasCompleteLogistics(entity)) {
            entity.setReceiptStatus(ImportOrderReceiptStatus.RECEIPTED);
        }
    }

    private boolean hasCompleteLogistics(ImportOrder entity) {
        return isNotBlank(entity.getLogisticsNo()) && isNotBlank(entity.getLogisticsCompany());
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private void applyImportedAfterSales(
            ImportOrder entity, String afterSalesRemark, LocalDateTime issueDateTime) {
        if (afterSalesRemark == null || afterSalesRemark.isBlank()) {
            entity.setAfterSales(false);
            entity.setAfterSalesStatus(AfterSalesStatus.NONE);
            entity.setAfterSalesRemark(null);
            entity.setAfterSalesAt(null);
            return;
        }
        String normalized = afterSalesRemark.trim();
        if (normalized.length() > AFTER_SALES_REMARK_MAX_LENGTH) {
            throw new BusinessException("售后原因不能超过 " + AFTER_SALES_REMARK_MAX_LENGTH + " 个字符");
        }
        entity.setAfterSales(true);
        entity.setAfterSalesStatus(AfterSalesStatus.PENDING);
        entity.setAfterSalesRemark(normalized);
        entity.setAfterSalesAt(issueDateTime);
    }

    private String normalizePhone(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim();
    }

    private ImportOrder toEntity(
            Long taskId,
            String platform,
            String merchant,
            OrderRow source,
            DailyTableRowDto display,
            LocalDateTime issueDateTime) {
        ImportOrder entity = new ImportOrder();
        entity.setTaskId(taskId);
        entity.setPlatform(platform);
        entity.setMerchant(merchant);
        entity.setMerchantSplit(
                !MerchantConfigService.PENDING_SPLIT_MERCHANT.equals(merchant));
        entity.setOrderNo(display.getOrderNo());
        entity.setProductName(display.getProductName());
        entity.setSpec(display.getSpec());
        entity.setQuantity(display.getQuantity());
        entity.setReceiver(display.getReceiver());
        entity.setAddress(display.getAddress());
        int excelRowNum = source.getSourceRowNum() > 0 ? source.getSourceRowNum() : 0;
        PhoneImportValidator.validateImportValue(display.getPhone(), excelRowNum);
        entity.setPhone(normalizePhone(display.getPhone()));
        entity.setShippingFee(normalizeShippingFee(display.getShippingFee()));
        entity.setRemark(display.getRemark());
        applyImportedAfterSales(entity, source.getAfterSalesRemark(), issueDateTime);
        entity.setReceiptStatus(ImportOrderReceiptStatus.PENDING);
        entity.setIssueDate(issueDateTime);
        entity.setSourceRowNum(source.getSourceRowNum() > 0 ? source.getSourceRowNum() : null);
        return entity;
    }
}
