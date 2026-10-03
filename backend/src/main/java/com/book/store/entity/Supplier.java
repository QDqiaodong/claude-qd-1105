package com.book.store.entity;

import java.time.LocalDateTime;

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

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();
}
