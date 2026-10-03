package com.book.store.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.book.store.entity.Book;

public interface BookRepository extends JpaRepository<Book, Long> {

    Book findByIsbn(String isbn);

    List<Book> findByStatus(String status);

    List<Book> findByTitleContainingOrAuthorContainingOrIsbnContaining(String title, String author, String isbn);
}
