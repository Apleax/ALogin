package xyz.apleax.ALogin.Geyser;

import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.event.PostOrder;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.event.bedrock.SessionJoinEvent;
import org.geysermc.geyser.api.event.bedrock.SessionLoginEvent;
import org.geysermc.geyser.api.event.java.ServerTransferEvent;
import org.geysermc.geyser.api.extension.Extension;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 让基岩版玩家也能走"登录服 -> 代理"转移流程的 Geyser 扩展
 *
 * @author Apleax
 */
public class ALoginTransferExtension implements Extension {
    private static final String FORM_TITLE = "登录";
    private static final String FORM_HINT = "输入邮箱与密码后提交";
    private static final String FORM_CLOSED_HINT = "登录后才能进入服务器, 请填写邮箱与密码";
    private final Map<String, PendingTransfer> pendingTransfers = new ConcurrentHashMap<>();
    /**
     * 刚被本扩展交接给代理的会话，加入服务器后不再弹登录表单
     */
    private final Set<GeyserConnection> handedOverSessions =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private volatile Settings settings;

    @Subscribe(postOrder = PostOrder.FIRST)
    public void onServerTransfer(ServerTransferEvent event) {
        Settings current = settings();
        GeyserConnection connection = event.connection();
        String xuid = connection.xuid();
        if (xuid.isBlank()) {
            logger().error("Missing the XUID of " + connection.bedrockUsername() + ", ignored the transfer");
            return;
        }
        if (current.transferTarget() != null && !current.transferTarget().matches(event.host(), event.port())) {
            logger().debug("Ignored the transfer of " + connection.bedrockUsername()
                    + " to " + event.host() + ":" + event.port());
            return;
        }
        Settings.HostPort bedrock = bedrockAddressOf(connection, current);
        if (bedrock == null) {
            logger().error("Unable to resolve the bedrock address of " + connection.bedrockUsername()
                    + ", please set 'bedrock-address' in " + Settings.FILE_NAME);
            return;
        }
        remember(xuid, new PendingTransfer(event.cookies(), event.host(), event.port(),
                System.currentTimeMillis() + current.cookieTtlMillis(), true));
        event.bedrockHost(bedrock.host());
        event.bedrockPort(bedrock.port());
        logger().info("Moving " + connection.bedrockUsername() + " back to " + bedrock
                + " to hand it over to " + event.host() + ":" + event.port());
    }

    @Subscribe(postOrder = PostOrder.FIRST)
    public void onSessionLogin(SessionLoginEvent event) {
        GeyserConnection connection = event.connection();
        String xuid = connection.xuid();
        PendingTransfer pending = xuid.isBlank() ? null : pendingTransfers.remove(xuid);
        if (pending == null) return;
        if (pending.isExpired()) {
            logger().warning("Dropped the expired transfer of " + connection.bedrockUsername());
            return;
        }
        Settings current = settings();
        String host = current.bedrockTarget() != null ? current.bedrockTarget().host() : pending.host();
        int port = current.bedrockTarget() != null ? current.bedrockTarget().port() : pending.port();
        Map<String, byte[]> cookies = pending.cookies();
        if (cookies.isEmpty())
            logger().warning("The transfer of " + connection.bedrockUsername() + " carried no cookies yet, "
                    + host + ":" + port + " will get no token if the table stays empty");
        event.cookies(cookies);
        event.remoteServer(new RedirectedRemoteServer(event.remoteServer(), host, port));
        event.transferring(true);
        handedOverSessions.add(connection);
        logger().info("Handing " + connection.bedrockUsername() + " over to " + host + ":" + port
                + " with " + cookies.size() + " cookie(s)");
        if (pending.fromLoginServer()) maybeBindXuid(connection, current, xuid, cookies);
    }

    /**
     * 等待服：已绑定 XUID 免密交接，其余按需弹登录表单
     */
    @Subscribe(postOrder = PostOrder.FIRST)
    public void onSessionJoin(SessionJoinEvent event) {
        Settings current = settings();
        GeyserConnection connection = event.connection();
        String xuid = connection.xuid();
        if (xuid.isBlank() || handedOverSessions.remove(connection) || pendingTransfers.containsKey(xuid)) return;
        if (current.xuidAutologin() && tryAutoLogin(connection, current, xuid)) return;
        if (!current.formLogin()) return;
        openLoginForm(connection, current, null, null);
    }

    /**
     * 已绑定 XUID 的免密交接：换取 token 后走与表单登录相同的交接流程
     *
     * @return true 表示已发起交接（不再弹表单）
     */
    private boolean tryAutoLogin(GeyserConnection connection, Settings current, String xuid) {
        Settings.HostPort target = downstreamTarget(current);
        Settings.HostPort bedrock = bedrockAddressOf(connection, current);
        if (target == null || bedrock == null || current.aloginUrl().isBlank()) return false;
        ALoginClient.Result result = new ALoginClient(current.aloginUrl(), current.aloginHeader(), logger())
                .autoLoginByXuid(xuid, connection.bedrockUsername());
        if (!result.success()) {
            logger().debug("XUID " + xuid + " 免密登录不可用(" + result.message() + "), 回落到表单登录");
            return false;
        }
        remember(xuid, new PendingTransfer(
                Map.of(current.cookieKey() + ":token", result.token().getBytes(StandardCharsets.UTF_8)),
                target.host(), target.port(), System.currentTimeMillis() + current.cookieTtlMillis(), false));
        logger().info("Player " + connection.bedrockUsername() + " logged in automatically by XUID,"
                + " reconnecting to hand it over to " + target);
        connection.transfer(bedrock.host(), bedrock.port());
        return true;
    }

    /**
     * 交接路径（/l 登录）补绑一次 XUID
     */
    private void maybeBindXuid(GeyserConnection connection, Settings current, String xuid, Map<String, byte[]> cookies) {
        if (!current.xuidAutologin() || xuid.isBlank() || current.aloginUrl().isBlank()) return;
        byte[] tokenBytes = cookies.get(current.cookieKey() + ":token");
        if (tokenBytes == null) return;
        String token = new String(tokenBytes, StandardCharsets.UTF_8);
        if (token.isBlank()) return;
        String name = connection.bedrockUsername();
        Thread thread = new Thread(() -> new ALoginClient(current.aloginUrl(), current.aloginHeader(), logger())
                .bindXuid(token, xuid, name), "ALogin-BedrockBind");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 表单/免密交接要连接的下游地址
     */
    private Settings.HostPort downstreamTarget(Settings current) {
        return current.bedrockTarget() != null ? current.bedrockTarget() : current.transferTarget();
    }

    /**
     * 弹出登录表单，回填上次邮箱，hint 显示失败原因
     */
    private void openLoginForm(GeyserConnection connection, Settings current, String email, String hint) {
        CustomForm.Builder builder = CustomForm.builder()
                .title(FORM_TITLE)
                .input("邮箱", "输入邮箱", email != null ? email : "")
                .input("密码", "输入密码")
                .label(hint != null ? hint : FORM_HINT);
        builder.validResultHandler((form, response) -> login(connection, current, response.asInput(0), response.asInput(1)));
        builder.invalidResultHandler(result -> logger().warning("The login form of " + connection.bedrockUsername()
                + " got an invalid response (" + result.errorMessage() + "), at component " + result.componentIndex()));
        builder.closedResultHandler(() -> {
            logger().debug("The login form of " + connection.bedrockUsername() + " was closed, reopening it");
            openLoginForm(connection, current, null, FORM_CLOSED_HINT);
        });
        connection.sendForm(builder.build());
    }

    /**
     * 表单回调：校验输入后换取 token 并触发交接
     */
    private void login(GeyserConnection connection, Settings current, String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            openLoginForm(connection, current, email, "邮箱和密码不能为空");
            return;
        }
        String trimmedEmail = email.trim();
        Settings.HostPort target = downstreamTarget(current);
        if (target == null) {
            logger().error("'transfer-target' or 'bedrock-target' must be set when form-login is enabled");
            openLoginForm(connection, current, trimmedEmail, "服务端未配置下游地址, 请联系管理员");
            return;
        }
        if (current.aloginUrl().isBlank()) {
            logger().error("'alogin-url' must be set when form-login is enabled");
            openLoginForm(connection, current, trimmedEmail, "服务端未配置 alogin-url, 请联系管理员");
            return;
        }
        Settings.HostPort bedrock = bedrockAddressOf(connection, current);
        if (bedrock == null) {
            logger().error("Unable to resolve the bedrock address of " + connection.bedrockUsername()
                    + ", please set 'bedrock-address' in " + Settings.FILE_NAME);
            openLoginForm(connection, current, trimmedEmail, "服务端未配置 bedrock-address, 请联系管理员");
            return;
        }
        ALoginClient.Result result = new ALoginClient(current.aloginUrl(), current.aloginHeader(), logger()).login(trimmedEmail, password);
        if (!result.success()) {
            openLoginForm(connection, current, trimmedEmail, result.message());
            return;
        }
        if (current.xuidAutologin() && !connection.xuid().isBlank())
            new ALoginClient(current.aloginUrl(), current.aloginHeader(), logger())
                    .bindXuid(result.token(), connection.xuid(), connection.bedrockUsername());
        Map<String, byte[]> cookies = Map.of(current.cookieKey() + ":token",
                result.token().getBytes(StandardCharsets.UTF_8));
        remember(connection.xuid(), new PendingTransfer(cookies, target.host(), target.port(),
                System.currentTimeMillis() + current.cookieTtlMillis(), false));
        logger().info("Player " + connection.bedrockUsername() + " logged in through the bedrock form,"
                + " reconnecting to hand it over to " + target);
        connection.transfer(bedrock.host(), bedrock.port());
    }

    /**
     * 记录一次待重连的转移并丢弃过期记录
     */
    private void remember(String xuid, PendingTransfer transfer) {
        pendingTransfers.values().removeIf(PendingTransfer::isExpired);
        pendingTransfers.put(xuid, transfer);
    }

    private Settings.HostPort bedrockAddressOf(GeyserConnection connection, Settings current) {
        return current.bedrockAddress() != null ? current.bedrockAddress() : joinAddressOf(connection);
    }

    /**
     * 玩家连接时用的地址
     */
    private Settings.HostPort joinAddressOf(GeyserConnection connection) {
        try {
            return new Settings.HostPort(connection.joinAddress(), connection.joinPort());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Settings settings() {
        Settings current = settings;
        if (current != null) return current;
        synchronized (this) {
            if (settings == null) settings = Settings.load(dataFolder(), logger());
            return settings;
        }
    }

    /**
     * 一次等待基岩客户端重连的转移
     *
     * @param cookies         cookie 表（transfer 路径下为旧会话的活表，仅存引用）
     * @param host            重连后连接的下游地址
     * @param port            重连后连接的下游端口
     * @param expiresAt       过期时间戳
     * @param fromLoginServer 是否来自登录服（/l 登录）
     */
    private record PendingTransfer(Map<String, byte[]> cookies, String host, int port, long expiresAt,
                                   boolean fromLoginServer) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
