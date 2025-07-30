package xyz.apleax.QinServer;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.Solon;
import org.noear.solon.annotation.SolonMain;
import org.noear.solon.core.util.ResourceUtil;
import org.noear.solon.web.cors.CrossFilter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

@Slf4j
@SolonMain
public class App {
    public static void main(String[] args) {
        Solon.start(App.class, args, app -> {
            app.filter(-1, new CrossFilter());
            String appName = Solon.cfg().appName();
            if (ResourceUtil.findResource("file:" + appName) == null)
                if (!createDir(appName) || !createFile(appName)) Solon.stop();
            String configPath = appName + "/config.yml";
            if (Solon.cfg().env() != null &&
                    !Solon.cfg().env().isEmpty() &&
                    Solon.cfg().env().equals("dev")) configPath = appName + "/config-dev.yml"; // 直接重新赋值
            Solon.cfg().loadAdd(configPath);
        });
    }


    // 创建目录
    private static boolean createDir(String appName) {
        String[] list = ResourceUtil.scanResources("classpath:" + appName + "/*").toArray(new String[0]);
        list = Arrays.stream(list)
                .map(s -> s.substring(0, s.lastIndexOf("/")))
                .toArray(String[]::new);
        File file;
        for (String s : list) {
            file = new File(ResourceUtil.findResource("file:").getPath().substring(1) + s);
            if (file.exists()) continue;
            if (!file.mkdirs()) {
                log.error("createDir err: {}", s);
                return false;
            }
        }
        return true;
    }

    // 复制配置文件
    private static boolean createFile(String appName) {
        String[] list = ResourceUtil.scanResources("classpath:" + appName + "/*").toArray(new String[0]);
        for (String s : list)
            try (InputStream inputStream = ResourceUtil.getResourceAsStream(s);
                 FileOutputStream outputStream = new FileOutputStream(ResourceUtil.findResource("file:").getPath().substring(1) + s)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) outputStream.write(buffer, 0, bytesRead);
            } catch (IOException e) {
                log.error("createFile err: {}", e.getMessage());
                return false;
            }
        return true;
    }
}