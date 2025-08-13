package xyz.apleax.ALogin.Util.Encrypt;

import org.bouncycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

/**
 *
 *
 * @author Apleax
 */
public class SHA256Encryptor implements PasswordEncryptor {
    @Override
    public String encrypt(String password, String salt) throws NoSuchAlgorithmException, NoSuchProviderException {
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256", "BC");
        byte[] digest = messageDigest.digest((password + salt).getBytes(StandardCharsets.UTF_8));
        return Hex.toHexString(digest);
    }
}
