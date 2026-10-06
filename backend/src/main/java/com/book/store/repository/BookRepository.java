package com.book.store.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.book.store.entity.Book;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface BookRepository extends JpaRepository<Book, Long> {

    Book findByIsbn(String isbn);

    List<Book> findByStatus(String status);

    List<Book> findByTitleContainingOrAuthorContainingOrIsbnContaining(String title, String author, String isbn);

    /**
     * 入库加库存时行级加锁读取图书，串行更新 stock，避免并发入库丢失库存增量。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT b FROM Book b WHERE b.id = :id")
    Optional<Book> findByIdForUpdate(@Param("id") Long id);
}
