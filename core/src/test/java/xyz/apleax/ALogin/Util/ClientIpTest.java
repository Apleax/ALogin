package xyz.apleax.ALogin.Util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 客户端 IP 判据测试：把各登录入口原先各自复制的那段校验钉死，
 * 避免"某处放宽/收紧"后各入口行为再次分叉
 */
class ClientIpTest {
    @Test
    void acceptsPlainIpv4AndIpv6() {
        assertTrue(ClientIp.isValid("127.0.0.1"));
        assertTrue(ClientIp.isValid("192.168.1.100"));
        assertTrue(ClientIp.isValid("2001:0db8:85a3:0000:0000:8a2e:0370:7334"));
        assertTrue(ClientIp.isValid("::ffff:127.0.0.1"));
        assertEquals("127.0.0.1", ClientIp.normalize("127.0.0.1"));
    }

    @Test
    void rejectsMissingOrBlank() {
        assertFalse(ClientIp.isValid(null));
        assertFalse(ClientIp.isValid(""));
        assertFalse(ClientIp.isValid("   "));
        assertNull(ClientIp.normalize(null));
        assertNull(ClientIp.normalize(" "));
    }

    @Test
    void rejectsHostPortAndCidrAndWhitespace() {
        // InetSocketAddress#toString 形态（带 / 与端口）与 CIDR 都不是纯 IP
        assertFalse(ClientIp.isValid("/127.0.0.1:25565"));
        assertFalse(ClientIp.isValid("10.0.0.0/8"));
        assertFalse(ClientIp.isValid("127.0.0.1 25565"));
        assertFalse(ClientIp.isValid("127.0.0.1\n"));
    }

    /**
     * 已知边界（沿用原有判据，本次重构刻意不收紧）：不带前导 "/" 的 "ip:port" 会被判为合法——
     * 判据只排除空白与 "/"，无法用"含冒号"排除端口（IPv6 字面量本身含冒号）。
     * 因此调用方必须传纯 IP：LoginCommand 用 InetSocketAddress#getHostString()，
     * 断言里的 ip 由插件用 InetAddress#getHostAddress() 填充。
     */
    @Test
    void keepsLegacyAcceptanceOfHostPortWithoutSlash() {
        assertTrue(ClientIp.isValid("127.0.0.1:25565"));
    }

    @Test
    void rejectsOverlongValue() {
        assertTrue(ClientIp.isValid("a".repeat(ClientIp.MAX_LENGTH)));
        assertFalse(ClientIp.isValid("a".repeat(ClientIp.MAX_LENGTH + 1)));
    }
}
