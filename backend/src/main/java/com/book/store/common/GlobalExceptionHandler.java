package com.book.store.common;

import java.sql.SQLException;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务校验失败：400 + 中文业务提示
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(e.getMessage()));
    }

    /**
     * 参数注解校验失败：400 + 字段名与提示
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(message.isEmpty() ? "请求参数校验失败" : message));
    }

    /**
     * 请求体格式错误：400
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail("请求体格式有误，请检查 JSON 字段类型"));
    }

    /**
     * 唯一约束冲突：400（避免在页面上抛出非人话的外键报错）
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail("存在唯一性约束冲突，请检查编号、手机号是否重复"));
    }

    /**
     * 并发锁冲突（停用/启用与建档、入库并发等）：409。
     * 此时数据库事务已回滚，采购单、库存、明细都没有被改动，用户刷新后可安全重试。
     */
    @ExceptionHandler({
            CannotAcquireLockException.class,
            ObjectOptimisticLockingFailureException.class,
            jakarta.persistence.LockTimeoutException.class,
            jakarta.persistence.PessimisticLockException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleLock(Exception e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("数据正被其他操作（如供应商停用/入库）占用，请刷新确认最新状态后重试"));
    }

    /**
     * JPA 包装出来的锁超时（MySQL 行锁等待超时常见于此）同样按 409 处理
     */
    @ExceptionHandler(JpaSystemException.class)
    public ResponseEntity<ApiResponse<Void>> handleJpaSystem(JpaSystemException e) {
        if (isLockFailure(e)) {
            return handleLock(e);
        }
        return handleOther(e);
    }

    /**
     * 沿异常链识别锁等待超时：MySQL 错误码 1205（ER_LOCK_WAIT_TIMEOUT），SQLState 以 HY008 / 40001 上报。
     */
    private boolean isLockFailure(Throwable e) {
        for (Throwable t = e; t != null && t != t.getCause(); t = t.getCause()) {
            if (t instanceof jakarta.persistence.LockTimeoutException
                    || t instanceof jakarta.persistence.PessimisticLockException
                    || t instanceof CannotAcquireLockException) {
                return true;
            }
            if (t instanceof SQLException sqle) {
                if (sqle.getErrorCode() == 1205 || "HY008".equals(sqle.getSQLState())) {
                    return true;
                }
            }
            if (t.getMessage() != null && t.getMessage().toLowerCase().contains("lock wait timeout")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 兜底：500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception e) {
        String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail("服务异常：" + detail));
    }
}
