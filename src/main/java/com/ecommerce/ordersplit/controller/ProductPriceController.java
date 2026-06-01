package com.ecommerce.ordersplit.controller;

import com.ecommerce.ordersplit.dto.BatchDeleteProductPriceRequest;
import com.ecommerce.ordersplit.dto.BatchDeleteProductPriceResult;
import com.ecommerce.ordersplit.dto.ProductPriceImportResult;
import com.ecommerce.ordersplit.dto.ProductPriceItemDto;
import com.ecommerce.ordersplit.dto.SaveProductPriceRequest;
import com.ecommerce.ordersplit.service.ProductPriceExcelImportService;
import com.ecommerce.ordersplit.service.ProductPriceMaintenanceService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 商品价格维护 API
 *
 * @author huangxinsong
 */
@RestController
@RequestMapping("/api/orders/product-prices")
@RequiredArgsConstructor
public class ProductPriceController {

    private final ProductPriceMaintenanceService productPriceMaintenanceService;
    private final ProductPriceExcelImportService productPriceExcelImportService;

    @GetMapping
    public ResponseEntity<List<ProductPriceItemDto>> listProductPrices(
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ResponseEntity.ok(productPriceMaintenanceService.listProductPrices(keyword));
    }

    @PutMapping
    public ResponseEntity<ProductPriceItemDto> saveProductPrice(
            @RequestBody SaveProductPriceRequest request) {
        return ResponseEntity.ok(productPriceMaintenanceService.saveProductPrice(request));
    }

    @DeleteMapping("/batch")
    public ResponseEntity<BatchDeleteProductPriceResult> batchDeleteProductPrices(
            @RequestBody BatchDeleteProductPriceRequest request) {
        return ResponseEntity.ok(productPriceMaintenanceService.batchDeleteProductPrices(request));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductPriceImportResult> importProductPrices(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(productPriceExcelImportService.importFromExcel(file));
    }

    @GetMapping("/import-template")
    public ResponseEntity<Resource> downloadImportTemplate() {
        return productPriceExcelImportService.buildImportTemplateResponse();
    }
}
