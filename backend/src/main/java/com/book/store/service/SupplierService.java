package com.book.store.service;

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

    public List<Supplier> list(String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            return supplierRepository.findByNameContaining(keyword);
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
        return supplierRepository.save(supplier);
    }

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
}
