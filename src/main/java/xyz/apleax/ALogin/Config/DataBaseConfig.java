package xyz.apleax.ALogin.Config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.annotation.Inject;

import javax.sql.DataSource;

/**
 * @author Apleax
 */
@Slf4j
@Configuration
public class DataBaseConfig {
    @Bean(name = "DataBase", typed = true, index = -100)
    public DataSource mysql(@Inject("${DataBase}") DatabaseProperties dbProps) {
        HikariDataSource ds = new HikariDataSource();
        switch (dbProps.getChoose().toLowerCase()) {
            case "mysql" -> ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
            default -> {
                log.error("数据库类型错误");
                Solon.stopBlock();
            }
        }
        String jdbcUrl = buildJdbcUrl(dbProps);
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername(dbProps.getUsername());
        ds.setPassword(dbProps.getPassword());
        ds.setConnectionInitSql("SELECT 1");
        ds.setMinimumIdle(10);
        ds.setMaximumPoolSize(20);
        ds.setInitializationFailTimeout(60_000L);
        ds.setConnectionTimeout(30_000);
        ds.setIdleTimeout(600_000);
        ds.setMaxLifetime(1800_000);
        ds.setLeakDetectionThreshold(60_000);
        return ds;
    }

    private String buildJdbcUrl(DatabaseProperties dbProps) {
        return "jdbc:" +
                dbProps.choose.toLowerCase() +
                "://" +
                dbProps.getHost() +
                ":" +
                dbProps.getPort() +
                "/" +
                dbProps.getDatabase() +
                "?useUnicode=true&characterEncoding=utf8&autoReconnect=true&rewriteBatchedStatements=true";
    }

    /**
     * 数据库配置属性类
     */
    @Setter
    @Getter
    public static class DatabaseProperties {
        private String choose;
        private String host;
        private int port;
        private String database;
        private String username;
        private String password;
    }
}