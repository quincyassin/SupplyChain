package com.ecommerce.ordersplit.controller;

import com.ecommerce.ordersplit.dto.MerchantConfigDto;
import com.ecommerce.ordersplit.dto.SaveMerchantConfigRequest;
import com.ecommerce.ordersplit.service.MerchantConfigService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商家配置 API
 *
 * @author huangxinsong
 */
@RestController
@RequestMapping("/api/orders/merchant-configs")
@RequiredArgsConstructor
public class MerchantConfigController {

  private final MerchantConfigService merchantConfigService;

  @GetMapping
  public ResponseEntity<List<MerchantConfigDto>> list() {
    return ResponseEntity.ok(merchantConfigService.listAll());
  }

  @PostMapping
  public ResponseEntity<MerchantConfigDto> create(@RequestBody SaveMerchantConfigRequest request) {
    return ResponseEntity.ok(merchantConfigService.create(request));
  }

  @PutMapping("/{id}")
  public ResponseEntity<MerchantConfigDto> update(
      @PathVariable Long id, @RequestBody SaveMerchantConfigRequest request) {
    return ResponseEntity.ok(merchantConfigService.update(id, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    merchantConfigService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
