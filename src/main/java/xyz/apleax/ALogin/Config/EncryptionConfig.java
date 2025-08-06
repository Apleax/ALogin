package xyz.apleax.ALogin.Config;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.annotation.Inject;
import xyz.apleax.ALogin.Util.Encrypt.EncryptorSelector;
import xyz.apleax.ALogin.Util.Encrypt.PasswordEncryptor;

@Slf4j
@Configuration
public class EncryptionConfig {
    private final EncryptorSelector selector;
    private final String algorithm;

    public EncryptionConfig(@Inject(value = "${Security.encryption.algorithm}")
                            String algorithm,
                            EncryptorSelector selector) {
        this.algorithm = algorithm;
        this.selector = selector;
    }

    @Bean(index = -100, name = "Algorithm")
    public PasswordEncryptor passwordEncryptor() {
        PasswordEncryptor encryptor = selector.select(algorithm);
        log.info("Using encryption algorithm: {}", encryptor.algorithmName());
        return encryptor;
    }
}
