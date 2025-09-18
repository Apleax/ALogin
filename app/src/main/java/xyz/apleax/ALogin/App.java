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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Security;
import java.util.Arrays;

@Slf4j
@SolonMain
public class App {
    public static void main(String[] args) {
        Solon.start(App.class, args, app -> {
            // 彩色日志适配检查
            if (JavaUtil.IS_WINDOWS && !Solon.cfg().isFilesMode())
                if (ClassUtil.hasClass(() -> AnsiConsole.class)) try {
                    AnsiConsole.systemInstall();
                } catch (Throwable e) {
                    log.warn("Failed to initialize AnsiConsole");
                }
            // 跨域请求
            app.filter(-1, new CrossFilter());
            String appName = Solon.cfg().appName();
            String[] requiredResources = ResourceUtil.scanResources("classpath:" + appName + "/*").toArray(new String[0]);
            // 文件初始化
            handleFileInitialization(appName, requiredResources);
            Security.addProvider(new BouncyCastleProvider());
            String configPath = appName + "/config.yml";
            if (Solon.cfg().env() != null &&
                    !Solon.cfg().env().isEmpty()) configPath = appName + "/config-" + Solon.cfg().env() + ".yml";
            Solon.cfg().loadAdd(configPath);
            log.info("ALogin Version: {}", Solon.cfg().get("solon.app.version"));
        });
    }

    /**
     * 处理文件初始化/恢复
     *
     * @param appName           应用名称
     * @param requiredResources 必需的资源文件类路径列表
     */
    private static void handleFileInitialization(String appName, String[] requiredResources) {
        if (requiredResources.length == 0) {
            log.warn("No resource files found for initialization");
            return;
        }
        boolean needsInitialization = ResourceUtil.findResource("file:" + appName) == null;
        if (needsInitialization) {
            log.info("ConfigFile Initialization...");
            if (!createDirectories(requiredResources) || !copyAllConfigFiles(requiredResources)) {
                log.error("Initialization failed");
                Solon.stopBlock();
            }
            log.info("ConfigFile Initialization completed");
        } else checkAndRecoverMissingFiles(requiredResources);
    }

    /**
     * 复制所有配置文件
     *
     * @param resourcePaths 必需的资源文件类路径列表
     * @return 是否初始化成功
     */
    private static boolean copyAllConfigFiles(String[] resourcePaths) {
        if (resourcePaths.length == 0) return false;
        for (String resourcePath : resourcePaths) if (recoverSingleFile(resourcePath)) return false;
        return true;
    }

    /**
     * 检查并恢复缺失的文件
     *
     * @param resourcePaths 必需的资源文件类路径列表
     */
    private static void checkAndRecoverMissingFiles(String[] resourcePaths) {
        boolean hasMissingFiles = false;
        for (String resourcePath : resourcePaths) {
            File file = new File(ResourceUtil.findResource("file:").getPath().substring(1) + resourcePath);
            if (!file.exists()) {
                log.debug("Found missing file: {}", resourcePath);
                hasMissingFiles = true;
                if (recoverSingleFile(resourcePath)) log.error("Failed to recover file: {}", resourcePath);
            }
        }
        if (hasMissingFiles) log.debug("Recovered all missing configuration files");
    }

    /**
     * 恢复单个文件
     *
     * @param resourcePath 必需的资源文件类路径
     * @return 是否恢复成功
     */
    private static boolean recoverSingleFile(String resourcePath) {
        File targetFile = new File(ResourceUtil.findResource("file:").getPath().substring(1) + resourcePath);
        File parentDir = targetFile.getParentFile();
        if (createDirectoryIfNotExists(parentDir)) return true;
        try (InputStream inputStream = ResourceUtil.getResourceAsStream(resourcePath);
             FileOutputStream outputStream = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) outputStream.write(buffer, 0, bytesRead);
            return false;
        } catch (IOException e) {
            log.error("Failed to recover file: {}", resourcePath, e);
            return true;
        }
    }

    /**
     * 创建目录（如果不存在）
     *
     * @param directory 目录
     * @return 是否创建成功<br><code>true</code>为失败，<code>false</code>为成功
     */
    private static boolean createDirectoryIfNotExists(File directory) {
        if (directory != null && !directory.exists()) if (!directory.mkdirs()) {
            log.error("Failed to create directory: {}", directory.getAbsolutePath());
            return true;
        }
        return false;
    }

    /**
     * 创建所有必需的目录
     *
     * @param resourcePaths 必需的资源文件类路径列表
     * @return 是否创建成功
     */
    private static boolean createDirectories(String[] resourcePaths) {
        String[] dirPaths = Arrays.stream(resourcePaths)
                .map(s -> s.substring(0, s.lastIndexOf("/")))
                .distinct()
                .toArray(String[]::new);
        for (String dirPath : dirPaths) {
            File dir = new File(ResourceUtil.findResource("file:").getPath().substring(1) + dirPath);
            if (dir.exists()) continue;
            if (createDirectoryIfNotExists(dir)) return false;
        }
        return true;
    }
}