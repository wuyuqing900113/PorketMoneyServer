package wyq.pocket.money.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 单向哈希工具（M1 设计 §4.3 / §7.1）。
 *
 * <p>refresh 令牌与手机号以 SHA-256 哈希落库（可等值查找、不可反推原文）；
 * 与 {@link DataEncryptor} 的可逆加密分工：哈希列用于查找与唯一约束，
 * 加密列用于授权回显。
 */
public final class Hashes {

    private static final String SHA_256 = "SHA-256";

    private Hashes() {
    }

    /**
     * 计算 SHA-256 十六进制摘要（小写，64 字符）。
     *
     * @param input 原文，不能为 null
     * @return 十六进制摘要
     */
    public static String sha256Hex(String input) {
        Objects.requireNonNull(input, "待哈希内容不能为 null");
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 为 JDK 必备算法，不可达
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
