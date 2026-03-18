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
import net.minestom.server.network.packet.server.common.TransferPacket;
import org.jetbrains.annotations.NotNull;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;
import org.noear.solon.data.annotation.Ds;
import xyz.apleax.ALogin.Enum.AccountType;
import xyz.apleax.ALogin.PO.AccountPO;
import xyz.apleax.ALogin.POJO.AccountIndexCache;
import xyz.apleax.ALogin.SQL.Service.IAccountService;
import xyz.apleax.ALogin.Util.Encrypt.PasswordEncryptor;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 *
 *
 * @author Apleax
 */
@Slf4j
@Managed
@Condition(onClass = MinecraftServer.class)
public class LoginCommand extends Command {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9\\u4e00-\\u9fa5]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$");
    private static final String transfer = Solon.cfg().get("minestom.transfer");
    private static final String cookieKey = Solon.cfg().get("minestom.cookie-key");

    public LoginCommand(@Ds("DataBase") IAccountService accountService,
                        @Inject("AccountCache") LoadingCache<@NotNull String, AccountPO> accountCache,
                        @Inject("AccountIndexCache") LoadingCache<@NotNull AccountIndexCache, String> accountIndexCache,
                        @Inject("Algorithm") PasswordEncryptor encryptor) {
        super("login", "l");

        setDefaultExecutor((sender, _) -> sender.sendMessage("用法: /l '<邮箱>' '<密码>'"));

        ArgumentString email = ArgumentType.String("'邮箱'");
        ArgumentString password = ArgumentType.String("'密码'");

        email.setCallback((sender, _) -> {
            sender.sendMessage("用法: /l '<邮箱>' '<密码>'");
        });

        password.setCallback((sender, _) -> {
            sender.sendMessage("用法: /l '<邮箱>' '<密码>'");
        });

        addSyntax((sender, context) -> {
            String emailStr = context.get(email);
            String passwordStr = context.get(password);
            if (!isEmail(emailStr)) {
                sender.sendMessage("邮箱格式错误");
                return;
            }
            String account = accountIndexCache.get(new AccountIndexCache(AccountType.EMAIL, emailStr));
            Player player = null;
            if (sender instanceof Player) player = (Player) sender;
            if (player == null) return;
            AccountPO accountPO = accountCache.get(account);
            if (accountPO == null) {
                sender.sendMessage(AccountType.EMAIL.getValue() + "不存在");
                return;
            }
            String passwordHex = encryptor.encrypt(passwordStr, accountPO.getSalt());
            if (!accountPO.getPassword().equals(passwordHex)) {
                sender.sendMessage(AccountType.EMAIL.getValue() + "或密码错误");
                return;
            }
            SaTokenContextMockUtil.setMockContext();
            StpUtil.login(accountPO.getAccount(), AccountType.EMAIL.getKey());
            boolean updated = accountService.update((new LambdaUpdateWrapper<AccountPO>()
                    .set(AccountPO::getLastLoginIp, player.getPlayerConnection().getRemoteAddress().toString())
                    .eq(AccountPO::getAccount, accountPO.getAccount())));
            if (updated) accountCache.put(accountPO.getAccount(), accountPO);
            else log.warn("Failed to update last login time for account: {}", accountPO.getAccount());
            String address;
            int port;
            if (transfer.contains(":")) {
                address = transfer.split(":")[0];
                port = Integer.parseInt(transfer.split(":")[1]);
            } else {
                address = transfer;
                port = 25565;
            }
            player.getPlayerConnection().storeCookie(cookieKey + ":token", StpUtil.getTokenValueByLoginId(account).getBytes(StandardCharsets.UTF_8));
            player.sendPacket(new TransferPacket(address, port));
        }, email, password);
    }

    private boolean isEmail(String input) {
        return EMAIL_PATTERN.matcher(input).matches();
    }
}
