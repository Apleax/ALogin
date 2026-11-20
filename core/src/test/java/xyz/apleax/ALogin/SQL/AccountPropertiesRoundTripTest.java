package xyz.apleax.ALogin.SQL;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import xyz.apleax.ALogin.PO.AccountPO;
import xyz.apleax.ALogin.POJO.GameProfile;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * properties 字段全链路回归测试：真实 H2 内存库 + MP 真实 SQL 生成 + JacksonTypeHandler 双向读写，
 * 并模拟 GameProfileServiceImpl 的组装方式，防止泛型丢失（反序列化成 LinkedHashMap）或列容量不足
 *
 * @author Apleax
 */
class AccountPropertiesRoundTripTest {
    /**
     * 真实 Mojang 纹理载荷（线上实测解码后 304 字节，其中含 Mojang 的缩进空白），
     * 用空格补齐到 304 字节以复现真实样本尺寸（JSON 允许尾随空白）
     */
    private static final String RAW_PAYLOAD = "{\"timestamp\":1791279474239,\"profileId\":"
            + "\"069a79f444e94726a5befca90e38aaf5\",\"profileName\":\"Notch\",\"signatureRequired\":true,"
            + "\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/"
            + "292009a4925b58f02c77dadc3ecef07ea4c7472f64e0fdc32ce5522489362680\"}}}";
    private static final String TEXTURE_PAYLOAD =
            RAW_PAYLOAD + " ".repeat(Math.max(0, 304 - RAW_PAYLOAD.getBytes(StandardCharsets.UTF_8).length));
    /**
     * 真实签名长度（Mojang/MineSkin 签名均为 684 字符）
     */
    private static final int SIGNATURE_LENGTH = 684;

    interface AccountMapper extends BaseMapper<AccountPO> {
    }

    @Test
    void propertiesRoundTripThroughRealDatabase() throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfigUtils.setGlobalConfig(configuration,
                new GlobalConfig().setDbConfig(new GlobalConfig.DbConfig()));
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:properties;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")));
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "test"), AccountPO.class);
        configuration.addMapper(AccountMapper.class);

        try (SqlSession session = new MybatisSqlSessionFactoryBuilder()
                .build(configuration).openSession(true)) {
            session.getConnection().createStatement().execute("""
                    CREATE TABLE account (
                        id BIGINT NOT NULL PRIMARY KEY,
                        account VARCHAR(8) NOT NULL,
                        email VARCHAR(255) NOT NULL,
                        nick_name VARCHAR(32) NOT NULL,
                        password VARCHAR(255) NOT NULL,
                        salt VARCHAR(32) NOT NULL,
                        algorithm VARCHAR(15) NOT NULL,
                        avatar VARCHAR(255),
                        mc_uuid VARCHAR(38) NOT NULL,
                        premium_uuid VARCHAR(36),
                        premium_name VARCHAR(32),
                        bedrock_xuid VARCHAR(32),
                        bedrock_name VARCHAR(32),
                        properties VARCHAR(2000),
                        qq_account INT,
                        registration_time BIGINT NOT NULL,
                        bind_qq_account_time BIGINT,
                        last_login_ip VARCHAR(100),
                        last_change_nick_name_time BIGINT,
                        previous_name VARCHAR(32)
                    )""");

            AccountMapper mapper = session.getMapper(AccountMapper.class);
            assertEquals(304, TEXTURE_PAYLOAD.getBytes(StandardCharsets.UTF_8).length);
            String value = Base64.getEncoder().encodeToString(TEXTURE_PAYLOAD.getBytes(StandardCharsets.UTF_8));
            assertEquals(408, value.length());
            String signature = "A".repeat(SIGNATURE_LENGTH);
            GameProfile.Property texture = new GameProfile.Property("textures", value, signature);
            UUID mcUuid = UUID.randomUUID();

            AccountPO accountPO = new AccountPO();
            accountPO.setId(1L);
            accountPO.setAccount("10000001");
            accountPO.setEmail("tester@example.com");
            accountPO.setNickName("Tester");
            accountPO.setPassword("hashed");
            accountPO.setSalt("salt");
            accountPO.setAlgorithm("ARGON2");
            accountPO.setRegistrationTime(System.currentTimeMillis() / 1000);
            accountPO.setMcUuid(mcUuid);
            accountPO.setProperties(List.of(texture));
            assertEquals(1, mapper.insert(accountPO));

            AccountPO loaded = mapper.selectById(1L);
            List<GameProfile.Property> properties = loaded.getProperties();
            assertEquals(1, properties.size());
            // 泛型必须被保留：若 JacksonTypeHandler 丢失字段泛型，这里会是 LinkedHashMap
            GameProfile.Property loadedTexture = assertInstanceOf(GameProfile.Property.class, properties.getFirst());
            assertEquals("textures", loadedTexture.name());
            assertEquals(value, loadedTexture.value());
            assertEquals(signature, loadedTexture.signature());

            // 按 GameProfileServiceImpl 的原样组装
            GameProfile profile = new GameProfile(loaded.getMcUuid(), loaded.getNickName(), properties);
            assertEquals(mcUuid, profile.id());
            assertEquals("Tester", profile.name());
            assertEquals(texture, profile.properties().getFirst());

            // 生产写路径（SkinPersistenceService 同款）：entity + wrapper 更新后再读回
            GameProfile.Property newTexture = new GameProfile.Property("textures",
                    value.replace('A', 'B'), signature.replace('A', 'B'));
            AccountPO patch = new AccountPO();
            patch.setProperties(List.of(newTexture));
            assertEquals(1, mapper.update(patch,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AccountPO>()
                            .eq(AccountPO::getId, 1L)));
            AccountPO reloaded = mapper.selectById(1L);
            assertEquals(newTexture, reloaded.getProperties().getFirst());

            // 列容量回归：真实样本序列化后 1139 字符，varchar(2000) 必须留有余量
            String serialized;
            try (java.sql.ResultSet rs = session.getConnection().createStatement()
                    .executeQuery("SELECT properties FROM account WHERE id = 1")) {
                assertTrue(rs.next());
                serialized = rs.getString(1);
            }
            assertEquals(1139, serialized.length());
            assertTrue(serialized.length() < 2000, "properties 序列化长度逼近列容量上限");
        }
    }
}
