package com.book.store.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.book.store.common.ApiResponse;
import com.book.store.entity.Supplier;
import com.book.store.service.SupplierService;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    public ApiResponse<List<Supplier>> list(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String status) {
        return ApiResponse.success(supplierService.list(keyword, status));
    }

    @PostMapping
    public ApiResponse<Supplier> create(@RequestBody Supplier supplier) {
        return ApiResponse.success(supplierService.create(supplier));
    }

    @PutMapping("/{id}")
    public ApiResponse<Supplier> update(@PathVariable Long id, @RequestBody Supplier supplier) {
        return ApiResponse.success(supplierService.update(id, supplier));
    }

    /** 停用：请求体 {"reason": "停用原因"}，原因为空会被业务校验拒绝 */
    @PostMapping("/{id}/disable")
    public ApiResponse<Supplier> disable(@PathVariable Long id, @RequestBody(required = false) DisableRequest body) {
        String reason = body == null ? null : body.reason;
        return ApiResponse.success(supplierService.disable(id, reason));
    }

    /** 重新启用：无请求体参数 */
    @PostMapping("/{id}/enable")
    public ApiResponse<Supplier> enable(@PathVariable Long id) {
        return ApiResponse.success(supplierService.enable(id));
    }

    public static class DisableRequest {
        public String reason;
    }
}
