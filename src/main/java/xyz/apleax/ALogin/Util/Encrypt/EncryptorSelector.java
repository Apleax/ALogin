package xyz.apleax.ALogin.Util.Encrypt;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EncryptorSelector {
    private final Map<String, PasswordEncryptor> encryptors;

    public EncryptorSelector(List<PasswordEncryptor> encryptorList) {
        this.encryptors = encryptorList.stream()
                .collect(Collectors.toMap(
                        e -> e.algorithmName().toUpperCase(),
                        Function.identity()
                ));
        log.debug("Registered encryptors: {}", encryptors.keySet());
    }

    public PasswordEncryptor select(String algorithm) {
        log.debug("Trying to select algorithm: {}", algorithm);
        PasswordEncryptor encryptor = encryptors.get(algorithm.toUpperCase());
        log.debug("Available encryptors: {}", encryptors.keySet());
        if (encryptor == null) {
            log.error("Unsupported algorithm: {}", algorithm);
            Solon.stopBlock();
        }
        return encryptor;
    }
}