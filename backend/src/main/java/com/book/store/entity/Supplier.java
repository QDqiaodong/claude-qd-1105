package com.book.store.entity;

import java.time.LocalDateTime;

import com.book.store.common.Dict;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "supplier")
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "name", nullable = false, unique = true, length = 64)
    public String name;

    @Column(name = "contact", length = 32)
    public String contact;

    @Column(name = "phone", length = 20)
    public String phone;

    /** 启用 / 停用（见 Dict.SUPPLIER_STATUS）；旧数据默认启用 */
    @Column(name = "status", length = 16)
    public String status = Dict.SUPPLIER_ACTIVE;

    /** 停用原因；启用状态下为 null */
    @Column(name = "inactive_reason", length = 255)
    public String inactiveReason;

    /** 最近一次启用/停用状态变更时间 */
    @Column(name = "status_changed_at")
    public LocalDateTime statusChangedAt;

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();
}
