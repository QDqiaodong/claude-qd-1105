# 书店 · 图书台账与进销存

基于 Spring Boot + Vue 3（Element Plus）的全栈小应用：维护图书台账，并以进货单入库加库存、销售单结账扣库存（不足拦截、退款回滚）的进销存数量联动。

## 启停

```bash
sh start.sh   # 启动（docker compose 拉起 mysql / backend / frontend）
sh stop.sh    # 停止
```

后端接口以 `/api` 为前缀（`/api/books`、`/api/suppliers`、`/api/purchases`、`/api/sales`），前端使用 hash 路由。

## 供应商启用 / 停用

- 供应商带可追踪状态：`启用 / 停用`、停用原因、最近变更时间；每次停用/启用都写入 `supplier_status_log` 流水（供应商页「变更记录」可查）。已有供应商默认保持启用。
- 新建采购草稿只能选择启用供应商；供应商停用后，其名下**已有草稿保留原供应商关系**，仍可查看、补充明细，但**不能入库**，也不能把草稿改挂给停用供应商。
- 入库前在同一事务内用行锁重新校验供应商状态：已停用则**整单拒绝**，采购单状态、图书库存、明细全部回滚、无半更新；重新启用后原草稿可继续入库。
- 停用/启用、建草稿、入库并发时由数据库行锁串行（`SELECT … FOR UPDATE`），不会出现「显示已停用却建成新单」「重复入库」「只更新一半」；并发冲突返回 409，前端提示刷新后安全重试。
- 旧库兼容：应用启动时幂等迁移（补状态列、回填启用、建流水表），旧供应商与旧草稿无需手工处理。
- 状态接口：`GET /api/suppliers?status=启用|停用`、`POST /api/suppliers/{id}/status`（body：`{status, reason}`，停用必填原因）、`GET /api/suppliers/{id}/status-logs`。
