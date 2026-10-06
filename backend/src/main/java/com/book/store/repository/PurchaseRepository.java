package com.book.store.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.book.store.entity.Purchase;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    List<Purchase> findByStatus(String status);

    /**
     * 行级写锁：串行化同一张采购单的入库/作废操作，
     * 防止两个并发入库请求同时通过状态检查、导致重复入库（库存加两遍）。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select p from Purchase p where p.id = :id")
    Optional<Purchase> findByIdForUpdate(@Param("id") Long id);
}
