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
import com.book.store.entity.Supplier;
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
        /** 关联供应商的当前状态快照（供应商可能在草稿创建后被停用） */
        public Supplier supplier;

        public PurchaseDetail(Purchase purchase, List<PurchaseItem> items, Supplier supplier) {
            this.purchase = purchase;
            this.items = items;
            this.supplier = supplier;
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
        // 旧草稿可能挂在后来被停用（甚至理论上缺失）的供应商上：查不到就给 null，关系仍保留在 purchase.supplierId
        Supplier supplier = purchase.supplierId == null ? null
                : supplierRepository.findById(purchase.supplierId).orElse(null);
        return new PurchaseDetail(purchase, purchaseItemRepository.findByPurchaseId(id), supplier);
    }

    // ---------------- 新建 / 编辑 ----------------

    /**
     * 新建采购草稿：只能挂“启用”供应商。
     * 先对供应商行加写锁再判状态，与“停用供应商”互斥——
     * 停用和新建并发提交时，只会有一个成功，不会出现界面显示已停用却建出新草稿。
     */
    @Transactional
    public Purchase create(PurchaseCreate input) {
        if (input.supplierId == null) {
            throw new BizException("请选择供应商");
        }
        Supplier supplier = supplierRepository.findByIdForUpdate(input.supplierId)
                .orElseThrow(() -> new BizException("供应商不存在"));
        if (!Dict.isSupplierActive(supplier.status)) {
            throw new BizException(inactiveSupplierMessage(supplier));
        }
        List<ItemInput> items = input.items;
        if (items == null || items.isEmpty()) {
            throw new BizException("进货明细不能为空");
        }
        // 先把所有明细校验一遍，再落库，避免留下半成品草稿
        validateItems(items);
        Purchase purchase = new Purchase();
        purchase.supplierId = input.supplierId;
        purchase.status = "草稿";
        purchase.total = BigDecimal.ZERO;
        Purchase saved = purchaseRepository.save(purchase);
        purchase.total = persistItems(saved.id, items);
        return purchaseRepository.save(saved);
    }

    /**
     * 编辑草稿（可查看、补充/修改明细）：
     * 供应商一旦在草稿建好后被停用，草稿仍保留原供应商关系，明细照常可以补充；
     * 只有“换一家供应商”时才要求新供应商处于启用状态。
     */
    @Transactional
    public Purchase update(Long id, PurchaseCreate input) {
        Purchase purchase = purchaseRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("进货单不存在"));
        if (!"草稿".equals(purchase.status)) {
            throw new BizException("仅草稿状态可编辑");
        }
        if (input.supplierId != null && !input.supplierId.equals(purchase.supplierId)) {
            Supplier target = supplierRepository.findByIdForUpdate(input.supplierId)
                    .orElseThrow(() -> new BizException("供应商不存在"));
            if (!Dict.isSupplierActive(target.status)) {
                throw new BizException(inactiveSupplierMessage(target));
            }
            purchase.supplierId = input.supplierId;
        }
        List<ItemInput> items = input.items;
        if (items == null || items.isEmpty()) {
            throw new BizException("进货明细不能为空");
        }
        validateItems(items);
        purchaseItemRepository.deleteByPurchaseId(id);
        purchase.total = persistItems(id, items);
        return purchaseRepository.save(purchase);
    }

    private void validateItems(List<ItemInput> items) {
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
        }
    }

    private BigDecimal persistItems(Long purchaseId, List<ItemInput> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (ItemInput it : items) {
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

    /**
     * 入库：
     * 1) 先锁采购单行（并发两次入库在这里排队，第二次只能看到“已入库”而失败，杜绝重复入库）；
     * 2) 再锁供应商行并重新校验启用状态——停用与入库并发时，停用方未提交则等待，
     *    已提交则按最新状态整单拒绝；
     * 3) 全部校验通过后才动库存与采购单状态，整个方法一个事务，
     *    任何一步失败由 Spring 回滚：采购单状态、图书库存、明细都不会留下半更新。
     */
    @Transactional
    public Purchase stockIn(Long id) {
        Purchase purchase = purchaseRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("进货单不存在"));
        if (!Dict.canTransferPurchase(purchase.status, "已入库")) {
            throw new BizException("仅草稿状态可入库（已作废不可再入库）");
        }
        if (purchase.supplierId == null) {
            throw new BizException("该进货单未关联供应商，无法入库");
        }
        Supplier supplier = supplierRepository.findByIdForUpdate(purchase.supplierId)
                .orElseThrow(() -> new BizException("供应商不存在，无法入库"));
        if (!Dict.isSupplierActive(supplier.status)) {
            throw new BizException(inactiveSupplierMessage(supplier) + "，该进货单暂不能入库；重新启用后可再试");
        }
        List<PurchaseItem> items = purchaseItemRepository.findByPurchaseId(id);
        if (items.isEmpty()) {
            throw new BizException("进货单无明细，无法入库");
        }
        // 先校验所有明细图书仍存在（此时尚未改任何库存）
        for (PurchaseItem it : items) {
            bookRepository.findById(it.bookId).orElseThrow(() -> new BizException("明细图书已不存在"));
        }
        // 校验通过后统一更新库存
        for (PurchaseItem it : items) {
            Book book = bookRepository.findById(it.bookId).orElseThrow();
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
        Purchase purchase = purchaseRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("进货单不存在"));
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

    private static String inactiveSupplierMessage(Supplier supplier) {
        String reason = supplier.inactiveReason == null || supplier.inactiveReason.isBlank()
                ? "未填写原因" : supplier.inactiveReason;
        return "供应商「" + supplier.name + "」已停用（停用原因：" + reason + "）";
    }
}
