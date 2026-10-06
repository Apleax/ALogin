package xyz.apleax.ALogin.Command;

import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentString;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.noear.dami2.Dami;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;
import org.noear.solon.core.bean.LifecycleBean;
import org.noear.solon.data.annotation.Ds;
import xyz.apleax.ALogin.Enum.AccountType;
import xyz.apleax.ALogin.PO.AccountPO;
import xyz.apleax.ALogin.POJO.AccountIndexCache;
import xyz.apleax.ALogin.POJO.PremiumAssertion;
import xyz.apleax.ALogin.SQL.Service.IAccountService;
import xyz.apleax.ALogin.Service.Premium.PremiumAssertionService;
import xyz.apleax.ALogin.Util.Encrypt.EncryptorSelector;
import xyz.apleax.ALogin.Util.Encrypt.PasswordEncryptor;
import xyz.apleax.ALogin.Util.TransferAddress;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * 游戏内 /l 登录指令：耗时操作（缓存加载/密码哈希/数据库写入）在独立线程执行，避免阻塞 tick 线程
 *
 * @author Apleax
 */
@Slf4j
@Managed
@Condition(onClass = MinecraftServer.class)
public class LoginCommand extends Command implements LifecycleBean {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+@[A-Za-z0-9]+(\\.[A-Za-z0-9-]+)+$");
    private static final long ATTEMPT_COOLDOWN_MILLIS = 3_000L;

    private final TransferAddress transfer = TransferAddress.parse(Solon.cfg().get("minestom.transfer"));
    private final String cookieKey = Solon.cfg().get("minestom.cookie-key");
    private final ExecutorService loginExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<UUID, Long> lastAttempts = new ConcurrentHashMap<>();

    private final PremiumAssertionService assertionService;
    private final IAccountService accountService;
    private final LoadingCache<@NotNull String, AccountPO> accountCache;
    private final LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache;
    private final PasswordEncryptor encryptor;
    private final EncryptorSelector selector;

    public LoginCommand(@Ds("DataBase") IAccountService accountService,
                        @Inject("AccountCache") LoadingCache<@NotNull String, AccountPO> accountCache,
                        @Inject("AccountIndexCache") LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache,
                        @Inject("Algorithm") PasswordEncryptor encryptor,
                        EncryptorSelector selector,
                        PremiumAssertionService assertionService) {
        super("login", "l");
        this.assertionService = assertionService;
        this.accountService = accountService;
        this.accountCache = accountCache;
        this.accountIndexCache = accountIndexCache;
        this.encryptor = encryptor;
        this.selector = selector;

        setDefaultExecutor((sender, _) -> sender.sendMessage("用法: /l '<邮箱>' '<密码>'"));

        ArgumentString email = ArgumentType.String("'邮箱'");
        ArgumentString password = ArgumentType.String("'密码'");

        email.setCallback((sender, _) -> sender.sendMessage("用法: /l '<邮箱>' '<密码>'"));
        password.setCallback((sender, _) -> sender.sendMessage("用法: /l '<邮箱>' '<密码>'"));

        addSyntax((sender, context) -> {
            String emailStr = context.get(email);
            String passwordStr = context.get(password);
            if (!isEmail(emailStr)) {
                sender.sendMessage("邮箱格式错误");
                return;
            }
            if (!(sender instanceof Player player)) return;
            long now = System.currentTimeMillis();
            Long previous = lastAttempts.put(player.getUuid(), now);
            if (previous != null && now - previous < ATTEMPT_COOLDOWN_MILLIS) {
                sender.sendMessage("操作过于频繁，请稍后再试");
                return;
            }
            loginExecutor.submit(() -> {
                try {
                    doLogin(player, emailStr, passwordStr);
                } catch (Exception e) {
                    log.warn("登录指令处理异常，player={}: {}", player.getUuid(), e.getMessage());
                    player.sendMessage("登录失败，请稍后再试");
                }
            });
        }, email, password);
    }

    @Override
    public void stop() {
        loginExecutor.shutdownNow();
    }

    /**
     * 校验并完成登录，写入进服 cookie 后转移玩家；密码按账号记录的算法验证（存量账号可能使用旧算法）
     */
    private void doLogin(Player player, String emailStr, String passwordStr) {
        String account = accountIndexCache.get(new AccountIndexCache(AccountType.EMAIL, emailStr));
        if (account == null) {
            player.sendMessage(AccountType.EMAIL.getValue() + "不存在");
            return;
        }
        AccountPO accountPO = accountCache.get(account);
        if (accountPO == null) {
            player.sendMessage(AccountType.EMAIL.getValue() + "不存在");
            return;
        }
        PasswordEncryptor actualEncryptor = encryptor;
        String storedAlgorithm = accountPO.getAlgorithm();
        if (storedAlgorithm != null && !storedAlgorithm.isBlank()) {
            actualEncryptor = selector.select(storedAlgorithm);
            if (actualEncryptor == null) {
                log.error("账号 [{}] 使用的加密算法 [{}] 已不可用", account, storedAlgorithm);
                player.sendMessage("该账号使用的加密算法已失效，请联系管理员");
                return;
            }
        }
        String passwordHex = actualEncryptor.encrypt(passwordStr, accountPO.getSalt());
        if (!accountPO.getPassword().equals(passwordHex)) {
            player.sendMessage(AccountType.EMAIL.getValue() + "或密码错误");
            return;
        }
        SaTokenContextMockUtil.setMockContext();
        try {
            StpUtil.login(account, AccountType.EMAIL.getKey());
            // 只取纯 IP, InetSocketAddress#toString 会带 "/" 和端口
            String realIp = ((InetSocketAddress) player.getPlayerConnection().getRemoteAddress())
                    .getHostString();
            boolean updated = accountService.update((new LambdaUpdateWrapper<AccountPO>()
                    .set(AccountPO::getLastLoginIp, realIp)
                    .eq(AccountPO::getAccount, account)));
            if (updated) accountCache.invalidate(account);
            else log.debug("最后登录 IP 无变化或更新失败，account={}", account);
            // 首次绑定：本次会话是正版时把正版 UUID 写入该账号（无断言则不触发）
            PremiumAssertion assertion = assertionService.get(player.getUuid());
            if (assertion != null)
                Dami.bus().send("PremiumBind", Map.of("account", account,
                        "premiumUuid", assertion.uuid().toString(),
                        "premiumName", assertion.name() == null ? "" : assertion.name()));
            String tokenValue = StpUtil.getTokenValueByLoginId(account);
            if (tokenValue == null) {
                log.warn("登录指令处理失败：无法获取会话 token，account={}", account);
                player.sendMessage("登录失败，请稍后再试");
                return;
            }
            player.getPlayerConnection().storeCookie(cookieKey + ":token", tokenValue.getBytes(StandardCharsets.UTF_8));
            player.getPlayerConnection().transfer(transfer.host(), transfer.port());
        } finally {
            SaTokenContextMockUtil.clearContext();
        }
    }

    private boolean isEmail(String input) {
        return EMAIL_PATTERN.matcher(input).matches();
    }
}
