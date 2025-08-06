package xyz.apleax.ALogin.Util.Encrypt;

/**
 * PasswordEncryptor
 *
 * @author Apleax
 */
public interface PasswordEncryptor {
    String encrypt(String password, String salt) throws Exception;

    default String algorithmName() {
        return this.getClass().getSimpleName().replace("Encryptor", "").toUpperCase();
    }
}
