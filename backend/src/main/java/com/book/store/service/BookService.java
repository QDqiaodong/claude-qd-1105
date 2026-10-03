package com.book.store.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.book.store.common.BizException;
import com.book.store.common.Dict;
import com.book.store.entity.Book;
import com.book.store.repository.BookRepository;

@Service
public class BookService {

    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public List<Book> list(String status, String keyword) {
        if (status != null && !status.isBlank()) {
            return bookRepository.findByStatus(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            return bookRepository.findByTitleContainingOrAuthorContainingOrIsbnContaining(keyword, keyword, keyword);
        }
        return bookRepository.findAll();
    }

    @Transactional
    public Book create(Book input) {
        if (input.isbn == null || input.isbn.isBlank()) {
            throw new BizException("ISBN 不能为空");
        }
        if (bookRepository.findByIsbn(input.isbn) != null) {
            throw new BizException("ISBN 已存在，不可重复");
        }
        if (input.title == null || input.title.isBlank()) {
            throw new BizException("书名不能为空");
        }
        if (input.price == null || input.price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("定价必须大于 0");
        }
        if (input.stock == null || input.stock < 0) {
            throw new BizException("库存不能为负数");
        }
        Book book = new Book();
        book.isbn = input.isbn;
        book.title = input.title;
        book.author = input.author;
        book.price = input.price;
        book.stock = input.stock;
        // 库存为 0 默认置为缺货
        book.status = (input.stock == 0) ? "缺货" : "在售";
        return bookRepository.save(book);
    }

    @Transactional
    public Book update(Long id, Book input) {
        Book book = bookRepository.findById(id).orElseThrow(() -> new BizException("图书不存在"));
        if (input.isbn != null) {
            if (input.isbn.isBlank()) {
                throw new BizException("ISBN 不能为空");
            }
            Book other = bookRepository.findByIsbn(input.isbn);
            if (other != null && !other.id.equals(id)) {
                throw new BizException("ISBN 已存在，不可重复");
            }
            book.isbn = input.isbn;
        }
        if (input.title != null) {
            if (input.title.isBlank()) {
                throw new BizException("书名不能为空");
            }
            book.title = input.title;
        }
        if (input.author != null) {
            book.author = input.author;
        }
        if (input.price != null) {
            if (input.price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("定价必须大于 0");
            }
            book.price = input.price;
        }
        if (input.stock != null) {
            if (input.stock < 0) {
                throw new BizException("库存不能为负数");
            }
            book.stock = input.stock;
        }
        if (input.status != null) {
            if (!Dict.isValidBookStatus(input.status)) {
                throw new BizException("图书状态非法（应为在售/缺货/下架）");
            }
            book.status = input.status;
        }
        // 库存归零且原状态为在售，自动置缺货
        if (book.stock == 0 && "在售".equals(book.status)) {
            book.status = "缺货";
        }
        return bookRepository.save(book);
    }
}
