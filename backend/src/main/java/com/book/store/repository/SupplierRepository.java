package com.book.store.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.book.store.entity.Supplier;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Supplier findByName(String name);

    List<Supplier> findByNameContaining(String keyword);
}
