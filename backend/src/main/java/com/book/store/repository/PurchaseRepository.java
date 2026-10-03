package com.book.store.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.book.store.entity.Purchase;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    List<Purchase> findByStatus(String status);
}
