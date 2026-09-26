package xyz.apleax.ALogin.Command;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;
import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Managed;
import xyz.apleax.ALogin.Identity.IdentityBindingException;
import xyz.apleax.ALogin.Identity.IdentityLoginService;
import xyz.apleax.ALogin.Identity.IdentityMessages;
import xyz.apleax.ALogin.Identity.PlayerLoginState;
import xyz.apleax.ALogin.Identity.VerifiedIdentity;

/**
 * 把当前已经验证的外部身份绑定到当前 ALogin 账号。
 *
 * @author Apleax
 */
@Managed
@Condition(onClass = MinecraftServer.class)
public class IdentityBindCommand extends Command {
    private final IdentityMessages messages;

    public IdentityBindCommand(PlayerLoginState loginState,
                                IdentityLoginService identityLoginService,
                                IdentityMessages messages) {
        super("identity");
        this.messages = messages;
        setDefaultExecutor((sender, _) -> sender.sendMessage(
                messages.text("identity.command.usage", "用法: /identity bind")));

        Command bind = new Command("bind");
        bind.setDefaultExecutor((sender, _) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(messages.text("identity.command.player-only", "只有玩家可以执行此命令"));
                return;
            }
            String account = loginState.accountOf(player.getUuid());
            VerifiedIdentity identity = loginState.identityOf(player.getUuid());
            if (account == null) {
                player.sendMessage(messages.text(
                        "identity.command.login-required", "请先使用 ALogin 密码登录，再执行此命令"));
                return;
            }
            if (identity == null) {
                player.sendMessage(messages.text(
                        "identity.command.no-identity", "没有找到最近验证的外部身份，请从已配置的入口重新连接"));
                return;
            }
            try {
                identityLoginService.bind(account, identity);
                player.sendMessage(messages.text(
                        "identity.command.success", "外部身份绑定成功，之后可以免输入 ALogin 密码"));
            } catch (IdentityBindingException exception) {
                player.sendMessage(messages.text(exception.messageKey(), exception.getMessage()));
            }
        });
        addSubcommand(bind);
    }
}
