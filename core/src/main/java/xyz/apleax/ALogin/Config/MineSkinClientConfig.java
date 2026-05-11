package xyz.apleax.ALogin.Config;

import org.mineskin.JsoupRequestHandler;
import org.mineskin.MineSkinClient;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Managed;

/**
 *
 *
 * @author Apleax
 */
@Configuration
public class MineSkinClientConfig {
    @Inject("${minestom.mineskin-api-key}")
    private static String MineskinApiKey;

    @Managed
    public MineSkinClient getClient() {
        return MineSkinClient.builder()
                .requestHandler(JsoupRequestHandler::new)
                .userAgent("ALogin")
                .apiKey(MineskinApiKey)
                .build();
    }
}
