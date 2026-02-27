package xyz.apleax.ALogin.Util.Encrypt;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Managed;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

/**
 * SHA256加密
 *
 * @author Apleax
 */
@Slf4j
@Managed
public class SHA256Encryptor implements PasswordEncryptor {
    @Override
    public String encrypt(String password, String salt) {
        try {
            MessageDigest messageDigest;
            messageDigest = MessageDigest.getInstance("SHA-256", "BC");
            byte[] digest = messageDigest.digest((password + salt).getBytes(StandardCharsets.UTF_8));
            return Hex.toHexString(digest);
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            log.error("SHA-256 encryption failed, No algorithm or provider can be found", e);
            Solon.stopBlock();
        }
        log.error("Encryption failed");
        Solon.stopBlock();
        return null;
    }
}
