package com.book.store.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.book.store.common.ApiResponse;
import com.book.store.entity.Book;
import com.book.store.service.BookService;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping
    public ApiResponse<List<Book>> list(@RequestParam(required = false) String status,
                                        @RequestParam(required = false) String keyword) {
        return ApiResponse.success(bookService.list(status, keyword));
    }

    @PostMapping
    public ApiResponse<Book> create(@RequestBody Book book) {
        return ApiResponse.success(bookService.create(book));
    }

    @PutMapping("/{id}")
    public ApiResponse<Book> update(@PathVariable Long id, @RequestBody Book book) {
        return ApiResponse.success(bookService.update(id, book));
    }
}
