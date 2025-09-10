package xyz.apleax.ALogin.Util;

import org.noear.solon.Solon;
import org.noear.solon.Utils;
import org.noear.solon.vault.VaultCoder;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 *
 *
 * @author Apleax
 */
public class VaultCoderImpl implements VaultCoder {
    private final String charset = "UTF-8";

    private final String algorithm = "AES/ECB/PKCS5Padding";
    private SecretKey key;

    public VaultCoderImpl() {
        this(Solon.cfg().get("DataBase.vault.password"));
    }

    public VaultCoderImpl(String password) {
        try {
            if (Utils.isNotEmpty(password)) {
                byte[] passwordBytes = password.getBytes(charset);
                key = new SecretKeySpec(passwordBytes, "AES");
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * 加密
     */
    @Override
    public String encrypt(String str) throws Exception {
        if (key == null) return str;

        Cipher cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.ENCRYPT_MODE, key);

        byte[] encrypted = cipher.doFinal(str.getBytes(charset));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * 解密
     */
    @Override
    public String decrypt(String str) throws Exception {
        if (key == null) return str;

        //密码
        Cipher cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.DECRYPT_MODE, key);

        byte[] encrypted1 = Base64.getDecoder().decode(str);
        byte[] original = cipher.doFinal(encrypted1);

        return new String(original, charset);
    }
}
