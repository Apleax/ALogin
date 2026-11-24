package xyz.apleax.ALogin.SQL;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import xyz.apleax.ALogin.PO.SkinPO;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * skin.avatar 全链路回归测试：真实 H2 内存库 + MP 真实 SQL 生成 + BlobTypeHandler 双向读写。
 * <p>
 * 覆盖两件事：新列能正常往返；以及生产写路径（SkinPersistenceService 同款 wrapper 显式 set）
 * 在头像贴图生成失败时能把 avatar 写回 NULL（避免皮肤已换、头像还是旧的）。
 *
 * @author Apleax
 */
class SkinAvatarRoundTripTest {
    interface SkinMapper extends BaseMapper<SkinPO> {
    }

    @Test
    void skinAvatarRoundTripThroughRealDatabase() throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfigUtils.setGlobalConfig(configuration,
                new GlobalConfig().setDbConfig(new GlobalConfig.DbConfig()));
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:skin;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")));
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "test"), SkinPO.class);
        configuration.addMapper(SkinMapper.class);

        try (SqlSession session = new MybatisSqlSessionFactoryBuilder()
                .build(configuration).openSession(true)) {
            session.getConnection().createStatement().execute("""
                    CREATE TABLE skin (
                        id BIGINT NOT NULL PRIMARY KEY,
                        mc_uuid VARCHAR(38) NOT NULL,
                        skin_uuid VARCHAR(38) NOT NULL,
                        skin_img BLOB NOT NULL,
                        avatar BLOB
                    )""");

            SkinMapper mapper = session.getMapper(SkinMapper.class);
            UUID mcUuid = UUID.randomUUID();
            byte[] skinBytes = {1, 2, 3, 4, 5};
            byte[] avatarBytes = {9, 8, 7};

            SkinPO inserted = new SkinPO();
            inserted.setId(1L);
            inserted.setMcUuid(mcUuid);
            inserted.setSkinUuid(UUID.randomUUID());
            inserted.setSkinImg(skinBytes);
            inserted.setAvatar(avatarBytes);
            assertEquals(1, mapper.insert(inserted));

            SkinPO loaded = mapper.selectById(1L);
            assertEquals(mcUuid, loaded.getMcUuid());
            assertArrayEquals(skinBytes, loaded.getSkinImg());
            assertArrayEquals(avatarBytes, loaded.getAvatar());

            // 生产写路径：wrapper 显式 set 三列，avatar 传 null（贴图生成失败）必须真的落 NULL
            UUID newSkinUuid = UUID.randomUUID();
            byte[] newSkinBytes = {7, 7};
            assertEquals(1, mapper.update(null, new LambdaUpdateWrapper<SkinPO>()
                    .set(SkinPO::getSkinUuid, newSkinUuid)
                    .set(SkinPO::getSkinImg, newSkinBytes)
                    .set(SkinPO::getAvatar, null)
                    .eq(SkinPO::getMcUuid, mcUuid)));

            SkinPO reloaded = mapper.selectById(1L);
            assertEquals(newSkinUuid, reloaded.getSkinUuid());
            assertArrayEquals(newSkinBytes, reloaded.getSkinImg());
            assertNull(reloaded.getAvatar());
        }
    }
}
