package xyz.apleax.ALogin.Util;

import java.security.SecureRandom;
import java.util.Random;

/**
 * 用于生成随机字符串的实用程序。
 *
 * @author HaHaWTH
 * @link <a href="https://github.com/HaHaWTH/AuthMeReReloaded/blob/master/src/main/java/fr/xephi/authme/util/RandomStringUtils.java">AuthMeReReloaded</a>
 */
public final class RandomStringUtils {

    private static final char[] CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final Random RANDOM = new SecureRandom();
    private static final int NUM_INDEX = 10;
    private static final int LOWER_ALPHANUMERIC_INDEX = 36;
    private static final int HEX_MAX_INDEX = 16;

    // Utility class
    private RandomStringUtils() {
    }

    /**
     * 生成一个给定长度的字符串，该字符串由 [0-9a-z] 范围内的随机字符组成。
     *
     * @param length 要生成的随机字符串的长度
     * @return 随机字符串
     */
    public static String generate(int length) {
        return generateString(length, LOWER_ALPHANUMERIC_INDEX);
    }

    /**
     * 生成给定长度的随机十六进制字符串。换句话说，生成的字符串
     * 仅包含 [0-9a-f] 范围内的字符。
     *
     * @param length 要生成的随机字符串的长度
     * @return 随机十六进制字符串
     */
    public static String generateHex(int length) {
        return generateString(length, HEX_MAX_INDEX);
    }

    /**
     * 生成给定长度的随机数字符串。换句话说，生成的字符串
     * 仅包含 [0-9] 范围内的字符。
     *
     * @param length 要生成的随机字符串的长度
     * @return 随机数字符串
     */
    public static String generateNum(int length) {
        return generateString(length, NUM_INDEX);
    }

    /**
     * 生成一个包含数字、小写和大写字母的随机字符串。结果
     * 方法匹配模式 [0-9a-zA-Z]。
     *
     * @param length 要生成的随机字符串的长度
     * @return 随机字符串
     */
    public static String generateLowerUpper(int length) {
        return generateString(length, CHARS.length);
    }

    private static String generateString(int length, int maxIndex) {
        if (length < 0) throw new IllegalArgumentException("Length 必须为正数，但" + length + "不是正数");
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; ++i) sb.append(CHARS[RANDOM.nextInt(maxIndex)]);
        return sb.toString();
    }
}
