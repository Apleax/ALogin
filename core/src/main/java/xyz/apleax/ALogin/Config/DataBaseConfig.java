package xyz.apleax.ALogin.Config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.annotation.Inject;
import org.noear.solon.core.bean.LifecycleBean;
import org.noear.solon.core.util.ResourceUtil;

import javax.sql.DataSource;
import java.sql.ResultSet;

/**
 * @author Apleax
 */
@Slf4j
@Configuration
public record DataBaseConfig() implements LifecycleBean {
    private static HikariDataSource preheatedDataSource;

    @Override
    public void start() {
        if (preheatedDataSource != null) preheatDataSource(preheatedDataSource);
        log.info("DataBaseConfig Loading Complete");
    }

    @Bean(name = "DataBase", typed = true, index = -100)
    public DataSource database(@Inject("${DataBase}") DatabaseProperties dbProps) {
        log.info("DataBaseConfig Loading...");
        HikariDataSource ds = new HikariDataSource();
        String choose;
        if (dbProps.jdbc() != null && !dbProps.jdbc().isEmpty())
            choose = dbProps.jdbc().substring(5).split(":", 2)[0].toLowerCase();
        else choose = dbProps.choose().toLowerCase();
        switch (choose) {
            case "mysql" -> ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
            case "sqlserver" -> ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            case "sqlite" -> ds.setDriverClassName("org.sqlite.JDBC");
            default -> throw new IllegalArgumentException("The database type is wrong: " + dbProps.choose());
        }
        String jdbcUrl = buildJdbcUrl(dbProps);
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername(dbProps.username());
        ds.setPassword(dbProps.password());
        if ("sqlite".equals(choose)) {
            ds.setMaximumPoolSize(1);
            ds.setConnectionInitSql("PRAGMA foreign_keys = ON");
        } else {
            ds.setConnectionInitSql("SELECT 1");
            ds.setMinimumIdle(10);
            ds.setMaximumPoolSize(20);
            ds.setInitializationFailTimeout(60_000L);
            ds.setConnectionTimeout(30_000);
            ds.setIdleTimeout(600_000);
            ds.setMaxLifetime(1800_000);
            ds.setLeakDetectionThreshold(60_000);
        }
        preheatedDataSource = ds;
        initializeTable(ds, choose);
        return ds;
    }

    private String buildJdbcUrl(DatabaseProperties dbProps) {
        if (dbProps.jdbc() != null && !dbProps.jdbc().isEmpty()) return dbProps.jdbc();
        String jdbcUrl = "jdbc:" + dbProps.choose().toLowerCase() + "://";
        switch (dbProps.choose().toLowerCase()) {
            case "mysql" -> {
                return jdbcUrl +
                        dbProps.host() +
                        ":" +
                        dbProps.port() +
                        "/" +
                        dbProps.database() +
                        "?useUnicode=true&characterEncoding=utf8&autoReconnect=true&rewriteBatchedStatements=true";
            }
            case "sqlserver" -> {
                return jdbcUrl +
                        dbProps.host() +
                        ":" +
                        dbProps.port() +
                        ";databaseName=" +
                        dbProps.database() +
                        ";encrypt=false;trustServerCertificate=true";
            }
            case "sqlite" -> {
                String path = dbProps.path();
                if (path == null || path.isEmpty())
                    path = ResourceUtil.findResource("file:" + Solon.cfg().appName()).getPath().substring(1) + "DataBase/ALogin.db";
                return jdbcUrl + path;
            }
            default -> throw new IllegalArgumentException("Unsupported database type: " + dbProps.choose());
        }
    }

    private void preheatDataSource(HikariDataSource dataSource) {
        int preheatCount = Math.min(dataSource.getMinimumIdle(), 20);
        java.sql.Connection[] connections = new java.sql.Connection[preheatCount];
        try {
            for (int i = 0; i < preheatCount; i++) connections[i] = dataSource.getConnection();
            for (int i = 0; i < preheatCount; i++) if (connections[i] != null) connections[i].close();
            log.info("Preheated {} database connections", preheatCount);
        } catch (Exception e) {
            log.warn("Failed to preheat database connections: {}", e.getMessage());
            for (java.sql.Connection conn : connections)
                if (conn != null) try {
                    conn.close();
                } catch (Exception ignored) {
                }
        }
    }

    private void initializeTable(HikariDataSource dataSource, String choose) {
        try (java.sql.Connection connection = dataSource.getConnection()) {
            for (String resource : ResourceUtil.scanResources("classpath:SQL/" + choose + "/*")) {
                String name = resource.substring(resource.lastIndexOf("/") + 1, resource.lastIndexOf("."));
                boolean tableExists;
                ResultSet rs = connection.getMetaData().getTables(null, null, name, new String[]{"TABLE"});
                tableExists = rs.next();
                if (tableExists) continue;
                String sql = ResourceUtil.getResourceAsString(resource);
                java.sql.Statement statement = connection.createStatement();
                statement.execute(sql);
                log.info("Initialized table {}", choose);
            }
        } catch (Exception e) {
            log.warn("Failed to initialize table: {}", e.getMessage(), e);
        }
    }

    /**
     * 数据库配置属性类
     */
    public record DatabaseProperties(
            String choose,
            String host,
            int port,
            String database,
            String username,
            String password,
            String jdbc,
            String path
    ) {
    }
}