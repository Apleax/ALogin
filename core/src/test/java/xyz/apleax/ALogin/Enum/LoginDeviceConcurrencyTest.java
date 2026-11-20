package xyz.apleax.ALogin.Enum;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 登录设备（终端）维度的顶号契约：{@code is-concurrent=false} 时只顶掉同 device 的旧会话，
 * 不同 device（web / game / bedrock）互不影响。
 * <p>
 * 该契约是"用凭证类型当 device 会顶错人"这一结论的基础，也保证 {@link LoginDevice} 的取值
 * 在框架层确实按终端隔离；Sa-Token 的默认行为一旦变化，这里会先在构建期报出来。
 */
class LoginDeviceConcurrencyTest {

    @AfterEach
    void clearContext() {
        SaTokenContextMockUtil.clearContext();
    }

    @Test
    void sameDeviceLoginsReplaceEachOther() {
        SaManager.getConfig().setIsConcurrent(false).setIsShare(false);
        SaTokenContextMockUtil.setMockContext();

        StpUtil.login("10000001", LoginDevice.WEB.getKey());
        String firstWeb = StpUtil.getTokenValue();
        assertNotNull(firstWeb);

        StpUtil.login("10000001", LoginDevice.WEB.getKey());
        String secondWeb = StpUtil.getTokenValue();
        assertNotNull(secondWeb);

        // 同 device：新登录顶掉旧会话（旧 token 失效）
        assertNull(StpUtil.getLoginIdByToken(firstWeb));
        assertEquals("10000001", StpUtil.getLoginIdByToken(secondWeb));
    }

    @Test
    void differentDevicesCoexist() {
        SaManager.getConfig().setIsConcurrent(false).setIsShare(false);
        SaTokenContextMockUtil.setMockContext();

        StpUtil.login("10000002", LoginDevice.WEB.getKey());
        String webToken = StpUtil.getTokenValue();

        StpUtil.login("10000002", LoginDevice.GAME.getKey());
        String gameToken = StpUtil.getTokenValue();

        StpUtil.login("10000002", LoginDevice.BEDROCK.getKey());
        String bedrockToken = StpUtil.getTokenValue();

        // 不同 device：各自独立在线
        assertEquals("10000002", StpUtil.getLoginIdByToken(webToken));
        assertEquals("10000002", StpUtil.getLoginIdByToken(gameToken));
        assertEquals("10000002", StpUtil.getLoginIdByToken(bedrockToken));
        assertEquals(3, StpUtil.getTokenValueListByLoginId("10000002").size());

        // 游戏端再次登录只顶掉游戏端
        StpUtil.login("10000002", LoginDevice.GAME.getKey());
        assertEquals("10000002", StpUtil.getLoginIdByToken(webToken));
        assertEquals("10000002", StpUtil.getLoginIdByToken(bedrockToken));
        assertNull(StpUtil.getLoginIdByToken(gameToken));
    }

    @Test
    void noDeviceLookupReturnsOtherDevicesToken() {
        SaManager.getConfig().setIsConcurrent(false).setIsShare(false);
        SaTokenContextMockUtil.setMockContext();

        StpUtil.login("10000003", LoginDevice.WEB.getKey());
        String webToken = StpUtil.getTokenValue();
        StpUtil.login("10000003", LoginDevice.GAME.getKey());
        String gameToken = StpUtil.getTokenValue();

        // 不带 device 的查询取的是"队列最后一个"（跨设备不筛选）——所以进服 cookie 不能靠它反查
        assertEquals(gameToken, StpUtil.getTokenValueByLoginId("10000003"));

        // 带上 device 才能精确取到目标终端的 token
        assertEquals(webToken, StpUtil.getTokenValueByLoginId("10000003", LoginDevice.WEB.getKey()));
        assertEquals(gameToken, StpUtil.getTokenValueByLoginId("10000003", LoginDevice.GAME.getKey()));
    }
}
