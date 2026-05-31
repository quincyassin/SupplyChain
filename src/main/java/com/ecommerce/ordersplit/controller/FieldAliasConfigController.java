package com.ecommerce.ordersplit.controller;

import com.ecommerce.ordersplit.dto.FieldAliasConfigDto;
import com.ecommerce.ordersplit.dto.SaveFieldAliasConfigRequest;
import com.ecommerce.ordersplit.service.FieldAliasConfigService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字段别名配置 API
 *
 * @author huangxinsong
 */
@RestController
@RequestMapping("/api/orders/field-aliases")
@RequiredArgsConstructor
public class FieldAliasConfigController {

    private final FieldAliasConfigService fieldAliasConfigService;

    @GetMapping
    public ResponseEntity<List<FieldAliasConfigDto>> list() {
        return ResponseEntity.ok(fieldAliasConfigService.listAll());
    }

    @PutMapping("/{fieldKey}")
    public ResponseEntity<FieldAliasConfigDto> save(
            @PathVariable String fieldKey, @RequestBody SaveFieldAliasConfigRequest request) {
        return ResponseEntity.ok(fieldAliasConfigService.save(fieldKey, request));
    }
}
