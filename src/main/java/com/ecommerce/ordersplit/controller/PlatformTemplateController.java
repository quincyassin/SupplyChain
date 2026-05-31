package com.ecommerce.ordersplit.controller;

import com.ecommerce.ordersplit.dto.PlatformTemplateDetailDto;
import com.ecommerce.ordersplit.dto.PlatformTemplateSummaryDto;
import com.ecommerce.ordersplit.dto.SavePlatformTemplateRequest;
import com.ecommerce.ordersplit.service.PlatformMappingTemplateService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台表头模板 API
 *
 * @author huangxinsong
 */
@RestController
@RequestMapping("/api/orders/platform-templates")
@RequiredArgsConstructor
public class PlatformTemplateController {

  private final PlatformMappingTemplateService platformMappingTemplateService;

  @GetMapping
  public ResponseEntity<List<PlatformTemplateSummaryDto>> list() {
    return ResponseEntity.ok(platformMappingTemplateService.listSummaries());
  }

  @GetMapping("/{platform}")
  public ResponseEntity<PlatformTemplateDetailDto> get(@PathVariable String platform) {
    return ResponseEntity.ok(platformMappingTemplateService.getDetail(platform));
  }

  @PostMapping("/{platform}")
  @ResponseStatus(HttpStatus.CREATED)
  public ResponseEntity<PlatformTemplateDetailDto> create(@PathVariable String platform) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(platformMappingTemplateService.create(platform));
  }

  @PutMapping("/{platform}")
  public ResponseEntity<PlatformTemplateDetailDto> save(
      @PathVariable String platform, @RequestBody SavePlatformTemplateRequest request) {
    return ResponseEntity.ok(platformMappingTemplateService.save(platform, request));
  }

  @DeleteMapping("/{platform}")
  public ResponseEntity<Void> delete(@PathVariable String platform) {
    platformMappingTemplateService.delete(platform);
    return ResponseEntity.noContent().build();
  }
}
