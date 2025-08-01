package xyz.apleax.ALogin.Util;

import xyz.apleax.ALogin.Enum.MessageDigestAlgorithm;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA加密工具
 *
 * @author Apleax
 */
public final class SHAEncryption {

    /**
     * SHA加密
     *
     * @param password  密码
     * @param algorithm 算法
     * @return 加密后的hash值
     * @throws NoSuchAlgorithmException 当前环境无法使用SHA加密时抛出
     * @author Apleax
     */
    public static String computeHash(String password, MessageDigestAlgorithm algorithm) throws NoSuchAlgorithmException {
        MessageDigest messageDigest = MessageDigest.getInstance(algorithm.getKey());
        messageDigest.reset();
        messageDigest.update(password.getBytes());
        byte[] digest = messageDigest.digest();
        return String.format("%0" + (digest.length << 1) + "x", new BigInteger(1, digest));
    }

    /**
     * SHA256加密
     *
     * @param password 密码
     * @return 加密后的hash值
     * @throws NoSuchAlgorithmException 当前环境无法使用SHA加密时抛出
     * @author Apleax
     */
    public static String SHA_256(String password) throws NoSuchAlgorithmException {
        return computeHash(password, MessageDigestAlgorithm.SHA256);
    }

    /**
     * SHA512加密
     *
     * @param password 密码
     * @return 加密后的hash值
     * @throws NoSuchAlgorithmException 当前环境无法使用SHA加密时抛出
     * @author Apleax
     */
    public static String SHA_512(String password) throws NoSuchAlgorithmException {
        return computeHash(password, MessageDigestAlgorithm.SHA512);
    }
}
