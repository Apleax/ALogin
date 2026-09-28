package xyz.apleax.ALogin.Config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;
import org.noear.solon.core.bean.LifecycleBean;
import org.noear.solon.core.util.ResourceUtil;
import org.noear.solon.vault.VaultUtils;
import xyz.apleax.ALogin.Util.RandomStringUtils;
import xyz.apleax.ALogin.Util.VaultCoderImpl;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 数据库启动配置：构建 {@link DataSource}、初始化数据表、引导 Vault 加密。
 *
 * @author Apleax
 */
@Slf4j
@Configuration
public class DataBaseConfig implements LifecycleBean {
    /**
     * Vault 密码：来自配置，或首次启用时自动生成。实例字段，依赖 @Configuration 单例语义
     */
    private String vaultPassword;
    /**
     * 是否需要自动生成 Vault（配置启用 Vault 但配置中Vault为空时为 true）
     */
    private final boolean needGenerateVaultPassword;

    private HikariDataSource preheatedDataSource;

    public DataBaseConfig() {
        this.vaultPassword = Solon.cfg().get("DataBase.vault.password", "");
        this.needGenerateVaultPassword = Solon.cfg().getBool("DataBase.vault.enabled", false)
                && this.vaultPassword.isBlank();
    }

    @Override
    public void start() {
        if (preheatedDataSource != null) preheatDataSource(preheatedDataSource);
        log.info("DataBaseConfig Loading Complete");
    }

    @Managed(name = "DataBase", typed = true, index = -100)
    @Condition(onMissingBean = DataSource.class, onBean = VaultCoderImpl.class)
    public DataSource database(@Inject("${DataBase}") DatabaseProperties dbProps) {
        log.info("DataBaseConfig Loading...");
        HikariDataSource ds = new HikariDataSource();
        String choose = resolveDbType(dbProps);
        switch (choose) {
            case "mysql" -> ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
            case "sqlserver" -> ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            case "sqlite" -> ds.setDriverClassName("org.sqlite.JDBC");
            default -> throw new IllegalArgumentException("The database type is wrong: " + choose);
        }
        String jdbcUrl = buildJdbcUrl(dbProps, choose);
        ds.setJdbcUrl(jdbcUrl);
        if (needGenerateVaultPassword) {
            log.info("""
                            Vault password: {}
                            Encrypt database name: {}
                            Encrypt database username: {}
                            Encrypt database password: {}""",
                    vaultPassword,
                    VaultUtils.encrypt(dbProps.database()),
                    VaultUtils.encrypt(dbProps.username()),
                    VaultUtils.encrypt(dbProps.password()));
            log.info("Fill in the above in the configuration file");
        }
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

    @Managed(typed = true, index = -100)
    public VaultCoderImpl vaultCoderInit() {
        if (needGenerateVaultPassword) vaultPassword = RandomStringUtils.generateLowerUpper(16);
        return new VaultCoderImpl(vaultPassword);
    }

    /**
     * 解析数据库类型：优先从 jdbc URL 提取，否则用 choose 字段
     */
    private String resolveDbType(DatabaseProperties dbProps) {
        String jdbc = dbProps.jdbc();
        if (jdbc != null && !jdbc.isEmpty()) {
            if (!jdbc.startsWith("jdbc:"))
                throw new IllegalArgumentException("非法 JDBC URL（必须以 jdbc: 开头）: " + jdbc);
            return jdbc.substring(5).split(":", 2)[0].toLowerCase();
        }
        String choose = dbProps.choose();
        if (choose == null || choose.isBlank())
            throw new IllegalArgumentException("必须配置 DataBase.choose 或 DataBase.jdbc");
        return choose.toLowerCase();
    }

    private String buildJdbcUrl(DatabaseProperties dbProps, String choose) {
        String jdbc = dbProps.jdbc();
        if (jdbc != null && !jdbc.isEmpty()) return jdbc;
        String prefix = "jdbc:" + choose + "://";
        return switch (choose) {
            case "mysql" -> prefix + dbProps.host() + ":" + portOrDefault(dbProps.port(), 3306)
                    + "/" + dbProps.database()
                    + "?useUnicode=true&characterEncoding=utf8&autoReconnect=true"
                    // allowMultiQueries 支持 initializeTable 执行含多条语句的 .sql 脚本
                    + "&rewriteBatchedStatements=true&allowMultiQueries=true";
            case "sqlserver" -> prefix + dbProps.host() + ":" + portOrDefault(dbProps.port(), 1433)
                    + ";databaseName=" + dbProps.database()
                    + ";encrypt=false;trustServerCertificate=true";
            case "sqlite" -> prefix + resolveSqlitePath(dbProps);
            default -> throw new IllegalArgumentException("Unsupported database type: " + choose);
        };
    }

    /**
     * port 未配置（record int 缺省为 0）时回退到数据库默认端口。
     */
    private static int portOrDefault(int port, int defaultPort) {
        return port > 0 ? port : defaultPort;
    }

    /**
     * SQLite 库文件路径：未显式配置 path 时，生成至工作目录下的 DataBase/ALogin.db。
     */
    private String resolveSqlitePath(DatabaseProperties dbProps) {
        String path = dbProps.path();
        if (path != null && !path.isBlank()) return path;
        return System.getProperty("user.dir") + File.separator + "DataBase" + File.separator + "ALogin.db";
    }

    private void preheatDataSource(HikariDataSource dataSource) {
        int minIdle = dataSource.getMinimumIdle();
        if (minIdle <= 0) {
            log.debug("跳过连接预热：minimumIdle={}", minIdle);
            return;
        }
        int preheatCount = Math.min(minIdle, 20);
        Connection[] connections = new Connection[preheatCount];
        try {
            for (int i = 0; i < preheatCount; i++) connections[i] = dataSource.getConnection();
            for (int i = 0; i < preheatCount; i++) if (connections[i] != null) connections[i].close();
            log.info("Preheated {} database connections", preheatCount);
        } catch (Exception e) {
            log.warn("Failed to preheat database connections: {}", e.getMessage());
            for (Connection conn : connections)
                if (conn != null) try {
                    conn.close();
                } catch (Exception ignored) {
                }
        }
    }

    private void initializeTable(HikariDataSource dataSource, String choose) {
        try (Connection connection = dataSource.getConnection()) {
            for (String resource : ResourceUtil.scanResources("classpath:SQL/" + choose + "/*")) {
                String name = resource.substring(resource.lastIndexOf("/") + 1, resource.lastIndexOf("."));
                boolean tableExists;
                // try-with-resources 确保 ResultSet/Statement 关闭，避免元数据查询泄漏游标
                try (ResultSet rs = connection.getMetaData().getTables(null, null, name, new String[]{"TABLE"})) {
                    tableExists = rs.next();
                }
                if (tableExists) continue;
                String sql = ResourceUtil.getResourceAsString(resource);
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                }
                log.info("Initialize {} table of {}", name, choose);
            }
        } catch (Exception e) {
            log.error("Failed to initialize table, abort startup", e);
            throw new IllegalStateException("数据库表初始化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 数据库配置属性类。
     */
    public record DatabaseProperties(
            VaultProperties vault,
            String choose,
            String host,
            int port,
            String database,
            String username,
            String password,
            String jdbc,
            String path
    ) {
        public DatabaseProperties {
            database = VaultUtils.guard(database);
            username = VaultUtils.guard(username);
            password = VaultUtils.guard(password);
        }
    }

    public record VaultProperties(
            boolean enabled,
            String password
    ) {
    }
}
