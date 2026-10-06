package com.book.store.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.book.store.entity.Supplier;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Supplier findByName(String name);

    List<Supplier> findByNameContaining(String keyword);

    List<Supplier> findByStatus(String status);

    List<Supplier> findByNameContainingAndStatus(String keyword, String status);

    /**
     * 行级写锁：供应商停用/启用与采购入库互斥，
     * 保证“停用检查”与“写采购单/库存”在同一事务内原子完成。
     * MySQL 无 WAIT n 语法，统一等待锁，超时由异常处理器转成 409 提示重试。
     */
    @Query("select s from Supplier s where s.id = :id")
    @LockModeType(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    Optional<Supplier> findByIdForUpdate(@Param("id") Long id);
}
