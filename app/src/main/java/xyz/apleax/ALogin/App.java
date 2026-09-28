package xyz.apleax.ALogin;


import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.fusesource.jansi.AnsiConsole;
import org.noear.solon.Solon;
import org.noear.solon.annotation.SolonMain;
import org.noear.solon.core.util.ClassUtil;
import org.noear.solon.core.util.JavaUtil;
import org.noear.solon.core.util.ResourceUtil;
import org.noear.solon.web.cors.CrossFilter;

import java.io.*;
import java.net.URL;
import java.security.Security;

@Slf4j
@SolonMain
public class App {

    private static final File BASE_DIR = new File(".");

    static void main(String[] args) {
        Solon.start(App.class, args, app -> {
            if (JavaUtil.IS_WINDOWS && !Solon.cfg().isFilesMode())
                if (ClassUtil.hasClass(() -> AnsiConsole.class)) try {
                    AnsiConsole.systemInstall();
                } catch (Throwable e) {
                    log.warn("Failed to initialize AnsiConsole");
                }
            Security.addProvider(new BouncyCastleProvider());

            String appName = Solon.cfg().appName();
            String env = Solon.cfg().env();
            boolean isDev = env != null && !env.isEmpty();

            String[] requiredResources = ResourceUtil
                    .scanResources("classpath:" + appName + "/**/*")
                    .toArray(new String[0]);
            log.debug("Scanned {} classpath resources under {}/: {}", requiredResources.length, appName, requiredResources);

            if (!handleFileInitialization(appName, requiredResources, isDev)) return;

            String configFileName = isDev ? "config-dev.yml" : "config.yml";
            URL configPath = ResourceUtil.getResourceByFile("./" + appName + "/" + configFileName);
            if (configPath == null && isDev) configPath = ResourceUtil.getResource(appName + "/" + configFileName);
            if (configPath == null) {
                log.error("Config file not found after initialization: ./{}/{}", appName, configFileName);
                Solon.stop();
                return;
            }
            Solon.cfg().loadAdd(configPath);

            String cross = Solon.cfg().get("cross.allow-origin", "*");
            app.router().filter(-1, new CrossFilter()
                    .allowedOrigins(cross.isEmpty() ? "*" : cross)
                    .allowCredentials(true));
            log.info("ALogin Version: {}", Solon.cfg().get("solon.app.version"));
        });
    }

    /**
     * 首次运行 / 缺失文件时的资源初始化流程。
     * <p>
     * 非 dev 环境下首次初始化完成后会 {@link Solon#stop()} 退出，等待用户编辑配置后手动重启；
     * dev 环境直接返回继续启动，便于 IDE 调试。
     * <p>
     * 补齐缺失文件不会停止应用（模板类文件无需用户编辑）。
     *
     * @param appName           应用名（同时是外部资源根目录名）
     * @param requiredResources 需要初始化的 classpath 资源路径列表
     * @param isDev             是否 dev 环境
     * @return {@code true} 表示可继续启动；{@code false} 表示已请求停止
     */
    private static boolean handleFileInitialization(String appName, String[] requiredResources, boolean isDev) {
        if (requiredResources.length == 0) {
            log.warn("No resource files found for initialization");
            return true;
        }
        File appDir = new File(BASE_DIR, appName);
        if (!appDir.exists()) {
            log.info("ConfigFile Initialization...");
            if (!copyAllResources(requiredResources)) {
                log.error("Initialization failed");
                Solon.stop();
                return false;
            }
            if (!isDev) {
                log.info("ConfigFile Initialization completed, Please restart after configuration");
                Solon.stop();
                return false;
            }
        } else recoverMissingFiles(requiredResources);
        return true;
    }

    /**
     * 复制所有 classpath 资源到外部运行目录（首次初始化）。
     *
     * @return 全部成功返回 {@code true}；任何一个失败立即返回 {@code false}
     */
    private static boolean copyAllResources(String[] resourcePaths) {
        for (String resourcePath : resourcePaths) if (!copySingleResource(resourcePath)) return false;
        return true;
    }

    /**
     * 检查外部目录，补齐缺失的资源文件。
     */
    private static void recoverMissingFiles(String[] resourcePaths) {
        for (String resourcePath : resourcePaths) {
            File target = new File(BASE_DIR, normalize(resourcePath));
            if (target.exists()) continue;
            log.info("Found missing file: {}", resourcePath);
            if (copySingleResource(resourcePath)) log.debug("Recovered file: {}", resourcePath);
            else log.error("Failed to recover file: {}", resourcePath);
        }
    }

    /**
     * 从 classpath 复制单个资源到外部运行目录：自动创建父目录，覆盖已存在文件。
     *
     * @return 成功返回 {@code true}；失败返回 {@code false}
     */
    private static boolean copySingleResource(String resourcePath) {
        String normalized = normalize(resourcePath);
        File target = new File(BASE_DIR, normalized);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            log.error("Failed to create directory: {}", parent.getAbsolutePath());
            return false;
        }
        try {
            InputStream in = ResourceUtil.getResourceAsStream(normalized);
            if (in == null) {
                // classpath 中不存在此资源（可能是 scanResources 返回了目录项，或 jar 内资源缺失）
                log.error("Resource not found in classpath: {}", normalized);
                return false;
            }
            try (in; OutputStream out = new FileOutputStream(target)) {
                in.transferTo(out);
                return true;
            }
        } catch (IOException e) {
            log.error("Failed to copy resource: {}", normalized, e);
            return false;
        }
    }

    /**
     * 去掉资源路径可能的前导斜杠，避免 {@link File#File(File, String)} 把 child 当绝对路径处理。
     */
    private static String normalize(String resourcePath) {
        return resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
    }
}
