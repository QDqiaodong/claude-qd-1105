package com.book.store.service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.book.store.common.BizException;
import com.book.store.common.Dict;
import com.book.store.entity.Book;
import com.book.store.entity.Purchase;
import com.book.store.entity.PurchaseItem;
import com.book.store.repository.BookRepository;
import com.book.store.repository.PurchaseItemRepository;
import com.book.store.repository.PurchaseRepository;
import com.book.store.repository.SupplierRepository;

@Service
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final BookRepository bookRepository;
    private final SupplierRepository supplierRepository;

    public PurchaseService(PurchaseRepository purchaseRepository,
                           PurchaseItemRepository purchaseItemRepository,
                           BookRepository bookRepository,
                           SupplierRepository supplierRepository) {
        this.purchaseRepository = purchaseRepository;
        this.purchaseItemRepository = purchaseItemRepository;
        this.bookRepository = bookRepository;
        this.supplierRepository = supplierRepository;
    }

    // ---------------- DTO ----------------
    public static class ItemInput {
        public Long bookId;
        public Integer qty;
        public BigDecimal cost;
    }

    public static class PurchaseCreate {
        public Long supplierId;
        public List<ItemInput> items;
    }

    public static class PurchaseDetail {
        public Purchase purchase;
        public List<PurchaseItem> items;

        public PurchaseDetail(Purchase purchase, List<PurchaseItem> items) {
            this.purchase = purchase;
            this.items = items;
        }
    }

    // ---------------- 查询 ----------------
    public List<Purchase> list(String status) {
        if (status != null && !status.isBlank()) {
            return purchaseRepository.findByStatus(status);
        }
        return purchaseRepository.findAll();
    }

    public PurchaseDetail detail(Long id) {
        Purchase purchase = purchaseRepository.findById(id).orElseThrow(() -> new BizException("进货单不存在"));
        return new PurchaseDetail(purchase, purchaseItemRepository.findByPurchaseId(id));
    }

    // ---------------- 新建 / 编辑 ----------------
    @Transactional
    public Purchase create(PurchaseCreate input) {
        if (input.supplierId == null) {
            throw new BizException("请选择供应商");
        }
        if (supplierRepository.findById(input.supplierId).isEmpty()) {
            throw new BizException("供应商不存在");
        }
        List<ItemInput> items = input.items;
        if (items == null || items.isEmpty()) {
            throw new BizException("进货明细不能为空");
        }
        Purchase purchase = new Purchase();
        purchase.supplierId = input.supplierId;
        purchase.status = "草稿";
        purchase.total = BigDecimal.ZERO;
        Purchase saved = purchaseRepository.save(purchase);
        purchase.total = persistItems(saved.id, items);
        return purchaseRepository.save(saved);
    }

    @Transactional
    public Purchase update(Long id, PurchaseCreate input) {
        Purchase purchase = purchaseRepository.findById(id).orElseThrow(() -> new BizException("进货单不存在"));
        if (!"草稿".equals(purchase.status)) {
            throw new BizException("仅草稿状态可编辑");
        }
        if (input.supplierId != null) {
            if (supplierRepository.findById(input.supplierId).isEmpty()) {
                throw new BizException("供应商不存在");
            }
            purchase.supplierId = input.supplierId;
        }
        List<ItemInput> items = input.items;
        if (items == null || items.isEmpty()) {
            throw new BizException("进货明细不能为空");
        }
        purchaseItemRepository.deleteByPurchaseId(id);
        purchase.total = persistItems(id, items);
        return purchaseRepository.save(purchase);
    }

    private BigDecimal persistItems(Long purchaseId, List<ItemInput> items) {
        BigDecimal total = BigDecimal.ZERO;
        Set<Long> seen = new HashSet<>();
        for (ItemInput it : items) {
            if (it.bookId == null) {
                throw new BizException("明细缺少图书");
            }
            if (bookRepository.findById(it.bookId).isEmpty()) {
                throw new BizException("明细中的图书不存在");
            }
            if (it.qty == null || it.qty <= 0) {
                throw new BizException("进货数量必须大于 0");
            }
            if (it.cost == null || it.cost.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("进价必须大于 0");
            }
            if (!seen.add(it.bookId)) {
                throw new BizException("同一图书在进货单内不可重复");
            }
            PurchaseItem pi = new PurchaseItem();
            pi.purchaseId = purchaseId;
            pi.bookId = it.bookId;
            pi.qty = it.qty;
            pi.cost = it.cost;
            purchaseItemRepository.save(pi);
            total = total.add(it.cost.multiply(BigDecimal.valueOf(it.qty)));
        }
        return total;
    }

    // ---------------- 状态流转 ----------------
    @Transactional
    public Purchase stockIn(Long id) {
        Purchase purchase = purchaseRepository.findById(id).orElseThrow(() -> new BizException("进货单不存在"));
        if (!Dict.canTransferPurchase(purchase.status, "已入库")) {
            throw new BizException("仅草稿状态可入库（已作废不可再入库）");
        }
        List<PurchaseItem> items = purchaseItemRepository.findByPurchaseId(id);
        if (items.isEmpty()) {
            throw new BizException("进货单无明细，无法入库");
        }
        for (PurchaseItem it : items) {
            Book book = bookRepository.findById(it.bookId).orElseThrow(() -> new BizException("明细图书已不存在"));
            book.stock += it.qty;
            // 入库后若有库存且原缺货，恢复在售
            if (book.stock > 0 && "缺货".equals(book.status)) {
                book.status = "在售";
            }
            bookRepository.save(book);
        }
        purchase.status = "已入库";
        return purchaseRepository.save(purchase);
    }

    @Transactional
    public Purchase voidPurchase(Long id) {
        Purchase purchase = purchaseRepository.findById(id).orElseThrow(() -> new BizException("进货单不存在"));
        if ("已作废".equals(purchase.status)) {
            throw new BizException("进货单已作废，不可重复操作");
        }
        if (!"草稿".equals(purchase.status) && !"已入库".equals(purchase.status)) {
            throw new BizException("当前状态不可作废");
        }
        // 已入库的作废需回滚库存
        if ("已入库".equals(purchase.status)) {
            for (PurchaseItem it : purchaseItemRepository.findByPurchaseId(id)) {
                Book book = bookRepository.findById(it.bookId).orElse(null);
                if (book != null) {
                    book.stock = Math.max(0, book.stock - it.qty);
                    if (book.stock == 0 && "在售".equals(book.status)) {
                        book.status = "缺货";
                    }
                    bookRepository.save(book);
                }
            }
        }
        purchase.status = "已作废";
        return purchaseRepository.save(purchase);
    }
}
