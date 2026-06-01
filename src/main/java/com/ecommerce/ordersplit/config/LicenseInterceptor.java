package com.ecommerce.ordersplit.config;

import com.ecommerce.ordersplit.service.LicenseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 未激活时拦截业务 API
 *
 * @author huangxinsong
 */
@Component
@RequiredArgsConstructor
public class LicenseInterceptor implements HandlerInterceptor {

    private final LicenseService licenseService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (licenseService.isLicensed()) {
            return true;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("code", "LICENSE_REQUIRED");
        body.put("message", "软件尚未激活或授权已失效，请先完成激活");
        response.getWriter().write(objectMapper.writeValueAsString(body));
        return false;
    }
}
