package xyz.apleax.ALogin.Config;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.annotation.Inject;
import xyz.apleax.ALogin.Util.Encrypt.EncryptorSelector;
import xyz.apleax.ALogin.Util.Encrypt.PasswordEncryptor;

@Slf4j
@Configuration
public record EncryptionConfig(EncryptorSelector selector) {
    @Inject(value = "${Security.encryption.algorithm}")
    private static String algorithm;

    @Bean(index = -100, name = "Algorithm")
    public PasswordEncryptor passwordEncryptor() {
        PasswordEncryptor encryptor = selector.select(algorithm);
        if (encryptor == null) throw new IllegalArgumentException("No encryption algorithm found for: " + algorithm);
        log.info("Using encryption algorithm: {}", encryptor.algorithmName());
        return encryptor;
    }
}
