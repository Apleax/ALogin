package xyz.apleax.ALogin.Util.Encrypt;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.bouncycastle.util.encoders.Hex;
import org.noear.solon.annotation.Component;

import java.nio.charset.StandardCharsets;

/**
 * @author Apleax
 */
@Component
public class Argon2Encryptor implements PasswordEncryptor {
    private static final int ITERATIONS = 10;
    private static final int MEMORY = 65536;
    private static final int PARALLELISM = 1;
    private static final int HASH_LENGTH = 64;

    @Override
    public String encrypt(String password, String salt) {
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_i)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(ITERATIONS)
                .withMemoryAsKB(MEMORY)
                .withParallelism(PARALLELISM)
                .withSalt(salt.getBytes())
                .build();
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);
        byte[] result = new byte[HASH_LENGTH];
        generator.generateBytes(password.getBytes(StandardCharsets.UTF_8), result);
        return Hex.toHexString(result);
    }
}
