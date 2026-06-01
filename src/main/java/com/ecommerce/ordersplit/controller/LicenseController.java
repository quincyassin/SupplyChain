package com.ecommerce.ordersplit.controller;

import com.ecommerce.ordersplit.dto.ActivateLicenseRequest;
import com.ecommerce.ordersplit.dto.LicenseStatusDto;
import com.ecommerce.ordersplit.service.LicenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 软件授权 API
 *
 * @author huangxinsong
 */
@RestController
@RequestMapping("/api/license")
@RequiredArgsConstructor
public class LicenseController {

    private final LicenseService licenseService;

    @GetMapping("/status")
    public ResponseEntity<LicenseStatusDto> status() {
        return ResponseEntity.ok(licenseService.getStatus());
    }

    @PostMapping("/activate")
    public ResponseEntity<LicenseStatusDto> activate(@RequestBody ActivateLicenseRequest request) {
        return ResponseEntity.ok(licenseService.activate(request.activationCode()));
    }
}
