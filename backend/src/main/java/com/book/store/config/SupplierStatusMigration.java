package com.book.store.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.book.store.common.Dict;

/**
 * 供应商“启用/停用”能力的启动期兼容迁移。
 *
 * <p>部署环境的 MySQL 数据目录可能是旧镜像烘好的（supplier 表没有状态列），
 * ddl-auto 又是 none。本类在 BeanFactory 后置阶段执行——此时 DataSource 已就绪、
 * 而 EntityManagerFactory（Hibernate 元数据/首条 SQL）尚未初始化，
 * 因此旧库可以在应用真正使用 supplier 表之前完成补列：</p>
 * <ul>
 *   <li>缺列则 ADD COLUMN，新列带默认值“启用”；</li>
 *   <li>历史供应商记录（status 为 NULL）回填为“启用”，状态变更时间回填 created_at；</li>
 *   <li>历史采购草稿 / 明细一概不动——停用供应商后旧草稿仍保留原供应商关系。</li>
 * </ul>
 * 全新镜像里 schema.sql 已含这些列，检查到列存在即跳过，完全幂等。
 */
@Component
@Order(0)
public class SupplierStatusMigration implements BeanFactoryPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(SupplierStatusMigration.class);

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // 主动触发 DataSource 初始化；此时尚未进入事务/JPA 生命周期，直连执行 DDL 最稳妥
        DataSource dataSource = beanFactory.getBean(DataSource.class);
        try (Connection conn = dataSource.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(true); // DDL 在 MySQL 里隐式提交，自动提交也保证回填 UPDATE 立即落库
            try {
                boolean hasStatus = columnExists(conn, "status");
                boolean hasReason = columnExists(conn, "inactive_reason");
                boolean hasChangedAt = columnExists(conn, "status_changed_at");

                try (Statement st = conn.createStatement()) {
                    if (!hasStatus) {
                        st.executeUpdate("ALTER TABLE supplier "
                                + "ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT '" + Dict.SUPPLIER_ACTIVE
                                + "' COMMENT '启用/停用' AFTER phone");
                        log.info("[迁移] supplier.status 列已补建，历史供应商默认置为「{}」", Dict.SUPPLIER_ACTIVE);
                    }
                    if (!hasReason) {
                        st.executeUpdate("ALTER TABLE supplier "
                                + "ADD COLUMN inactive_reason VARCHAR(255) NULL COMMENT '停用原因' AFTER status");
                    }
                    if (!hasChangedAt) {
                        st.executeUpdate("ALTER TABLE supplier "
                                + "ADD COLUMN status_changed_at DATETIME NULL COMMENT '最近一次启用/停用时间' "
                                + "AFTER inactive_reason");
                    }
                    // 兜底回填：哪怕列原本就存在但数据为空（异常旧数据），也保证默认启用、有迹可循
                    int filled = st.executeUpdate(
                            "UPDATE supplier SET status = '" + Dict.SUPPLIER_ACTIVE
                                    + "' WHERE status IS NULL OR status = ''");
                    st.executeUpdate("UPDATE supplier SET status_changed_at = created_at "
                            + "WHERE status_changed_at IS NULL");
                    if (filled > 0) {
                        log.info("[迁移] {} 条历史供应商记录回填为「{}」", filled, Dict.SUPPLIER_ACTIVE);
                    }
                }
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            // 迁移失败属于环境级问题，直接中断启动，避免在缺列状态下半运行
            throw new IllegalStateException("供应商状态字段迁移失败：" + e.getMessage(), e);
        }
    }

    private boolean columnExists(Connection conn, String column) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        // 不同驱动/平台对 catalog 与表名大小写处理不同，多试几种组合，任一命中即存在
        String[] tablePatterns = {"supplier", "SUPPLIER"};
        for (String table : tablePatterns) {
            try (ResultSet rs = meta.getColumns(conn.getCatalog(), null, table, column)) {
                if (rs.next()) {
                    return true;
                }
            }
            try (ResultSet rs = meta.getColumns(conn.getCatalog(), null, table, column.toUpperCase())) {
                if (rs.next()) {
                    return true;
                }
            }
        }
        return false;
    }
}
