package com.book.store.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.book.store.common.BizException;
import com.book.store.common.Dict;
import com.book.store.entity.Supplier;
import com.book.store.repository.SupplierRepository;

@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;

    public SupplierService(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    /**
     * 列表查询：可按名称关键字、状态（启用/停用）组合过滤。
     * 采购建档时只传 {@link Dict#SUPPLIER_ACTIVE} 即可拿到可选供应商。
     */
    public List<Supplier> list(String keyword, String status) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasStatus = status != null && !status.isBlank();
        if (hasStatus && !Dict.SUPPLIER_STATUS.contains(status)) {
            throw new BizException("供应商状态只能是：启用 / 停用");
        }
        if (hasKeyword && hasStatus) {
            return supplierRepository.findByNameContainingAndStatus(keyword.trim(), status);
        }
        if (hasStatus) {
            return supplierRepository.findByStatus(status);
        }
        if (hasKeyword) {
            return supplierRepository.findByNameContaining(keyword.trim());
        }
        return supplierRepository.findAll();
    }

    @Transactional
    public Supplier create(Supplier input) {
        if (input.name == null || input.name.isBlank()) {
            throw new BizException("供应商名称不能为空");
        }
        if (supplierRepository.findByName(input.name) != null) {
            throw new BizException("供应商名称已存在");
        }
        if (input.phone != null && !input.phone.isBlank() && !Dict.isValidPhone(input.phone)) {
            throw new BizException("手机号格式不正确（应为 1 开头的 11 位手机号）");
        }
        Supplier supplier = new Supplier();
        supplier.name = input.name;
        supplier.contact = input.contact;
        supplier.phone = input.phone;
        // 新建供应商一律启用；停用原因不允许在新建时带入
        supplier.status = Dict.SUPPLIER_ACTIVE;
        supplier.inactiveReason = null;
        supplier.statusChangedAt = LocalDateTime.now();
        return supplierRepository.save(supplier);
    }

    /**
     * 编辑资料：只允许改名称/联系人/手机。
     * 启用/停用必须走 {@link #disable} / {@link #enable}，
     * 避免改个联系方式就把停用原因和状态变更时间弄丢，也避免绕过“停用必须填原因”。
     */
    @Transactional
    public Supplier update(Long id, Supplier input) {
        Supplier supplier = supplierRepository.findById(id).orElseThrow(() -> new BizException("供应商不存在"));
        if (input.name != null) {
            if (input.name.isBlank()) {
                throw new BizException("供应商名称不能为空");
            }
            Supplier other = supplierRepository.findByName(input.name);
            if (other != null && !other.id.equals(id)) {
                throw new BizException("供应商名称已存在");
            }
            supplier.name = input.name;
        }
        if (input.contact != null) {
            supplier.contact = input.contact;
        }
        if (input.phone != null) {
            if (!input.phone.isBlank() && !Dict.isValidPhone(input.phone)) {
                throw new BizException("手机号格式不正确（应为 1 开头的 11 位手机号）");
            }
            supplier.phone = input.phone;
        }
        return supplierRepository.save(supplier);
    }

    /**
     * 停用：必须填写停用原因。行锁保证与“正在入库的采购单”互斥——
     * 若此刻有同供应商采购单正在入库（持采购单行锁、随后要取供应商行锁），
     * 本操作会等其提交；反之已停用则入库方会在同一事务里看到最新状态而整单回滚。
     */
    @Transactional
    public Supplier disable(Long id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BizException("停用必须填写停用原因");
        }
        String trimmed = reason.trim();
        if (trimmed.length() > Dict.SUPPLIER_REASON_MAX) {
            throw new BizException("停用原因不能超过 " + Dict.SUPPLIER_REASON_MAX + " 字");
        }
        Supplier supplier = supplierRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("供应商不存在"));
        if (Dict.SUPPLIER_INACTIVE.equals(supplier.status)) {
            throw new BizException("供应商已是停用状态，无需重复停用");
        }
        supplier.status = Dict.SUPPLIER_INACTIVE;
        supplier.inactiveReason = trimmed;
        supplier.statusChangedAt = LocalDateTime.now();
        return supplierRepository.save(supplier);
    }

    /**
     * 重新启用：清空停用原因。启用后，之前挂在该供应商上、被拦住入库的草稿可继续入库。
     */
    @Transactional
    public Supplier enable(Long id) {
        Supplier supplier = supplierRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("供应商不存在"));
        if (Dict.SUPPLIER_ACTIVE.equals(supplier.status)) {
            throw new BizException("供应商已是启用状态，无需重复启用");
        }
        supplier.status = Dict.SUPPLIER_ACTIVE;
        supplier.inactiveReason = null;
        supplier.statusChangedAt = LocalDateTime.now();
        return supplierRepository.save(supplier);
    }
}
