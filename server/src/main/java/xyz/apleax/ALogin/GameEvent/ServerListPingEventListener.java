package xyz.apleax.ALogin.GameEvent;

import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.server.ServerListPingEvent;
import net.minestom.server.ping.ServerListPingType;
import net.minestom.server.ping.Status;
import org.jetbrains.annotations.NotNull;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Managed;
import org.noear.solon.core.Props;
import org.noear.solon.core.util.ResourceUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 *
 *
 * @author Apleax
 */
@Slf4j
@Managed(index = -999)
@Condition(onClass = MinecraftServer.class)
public class ServerListPingEventListener implements EventListener<@NotNull ServerListPingEvent> {

    private static final String appName = Solon.cfg().appName();
    private static final URL iconFile = ResourceUtil.findResource("file:" + appName + "/icon.png");
    private static final List<MiniMotd> minimotd = loadMiniMotd();
    private static final byte[] favicon = loadFavicon();
    private final MiniMessage mm = MiniMessage.miniMessage();

    @Override
    public @NotNull Class<ServerListPingEvent> eventType() {
        return ServerListPingEvent.class;
    }

    @Override
    public @NotNull Result run(ServerListPingEvent event) {
        if (event.getConnection() == null) return Result.INVALID;
        if (event.getPingType().equals(ServerListPingType.MODERN_FULL_RGB)) event.setStatus(Status.builder()
                .description(currentMotd())
                .favicon(favicon)
                .playerInfo(Status.PlayerInfo.onlineCount())
                .versionInfo(new Status.VersionInfo("1.21.11-26.2", event.getConnection().getProtocolVersion()))
                .build());
        return Result.SUCCESS;
    }

    /**
     * 类加载时读取一次服务器图标,缺失时返回 null 并提示
     *
     * @return 图标字节数组,未找到时返回 null
     */
    private static byte[] loadFavicon() {
        if (!ResourceUtil.hasResource("file:" + appName + "/icon.png")) {
            log.warn("icon.png not found, place it in resources/{}/icon.png (64x64 png)", appName);
            try (InputStream in = ResourceUtil.findResource("classpath:" + appName + "/icon.png").openStream()) {
                return in.readAllBytes();
            } catch (IOException e) {
                log.warn("Failed to load icon.png: {}", e.getMessage());
                return null;
            }
        }
        try (InputStream in = iconFile.openStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            log.warn("Failed to load icon.png: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取当前展示的 MOTD,多条配置随机使用
     *
     * @return MOTD 组件
     */
    private Component currentMotd() {
        if (minimotd.isEmpty()) return Component.text("QinServer", TextColor.color(9, 173, 211));
        MiniMotd motd = minimotd.get((int) (Math.random() * minimotd.size()));
        Component description = parseMotd(motd.line1());
        if (motd.line2() != null && !motd.line2().isBlank())
            description = description.append(Component.newline()).append(parseMotd(motd.line2()));
        return description;
    }

    /**
     * 解析 MiniMessage 格式文本,解析失败时按纯文本返回
     *
     * @param text MiniMessage 文本
     * @return 组件
     */
    private Component parseMotd(String text) {
        if (text == null || text.isBlank()) return Component.empty();
        try {
            return mm.deserialize(text);
        } catch (Exception e) {
            return Component.text(text);
        }
    }

    /**
     * 从配置加载 minimotd 列表
     */
    private static List<MiniMotd> loadMiniMotd() {
        List<MiniMotd> motds = new ArrayList<>();
        for (Props props : Solon.cfg().getListedProp("minestom.minimotd"))
            motds.add(new MiniMotd(props.get("line1"), props.get("line2")));
        return motds;
    }

    /**
     * MOTD 配置项
     */
    private record MiniMotd(String line1, String line2) {
    }
}
