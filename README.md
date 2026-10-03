# 书店 · 图书台账与进销存

基于 Spring Boot + Vue 3（Element Plus）的全栈小应用：维护图书台账，并以进货单入库加库存、销售单结账扣库存（不足拦截、退款回滚）的进销存数量联动。

## 启停

```bash
sh start.sh   # 启动（docker compose 拉起 mysql / backend / frontend）
sh stop.sh    # 停止
```

后端接口以 `/api` 为前缀（`/api/books`、`/api/suppliers`、`/api/purchases`、`/api/sales`），前端使用 hash 路由。
