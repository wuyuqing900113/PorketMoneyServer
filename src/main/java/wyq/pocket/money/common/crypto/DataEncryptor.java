package wyq.pocket.money.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * AES-256-GCM 敏感数据加密器（M1 设计 §8.1）。
 *
 * <p>落库格式：{@code Base64(IV(12字节) ‖ 密文 ‖ 认证标签(128bit))}；
 * GCM 认证加密保证密文任何篡改都会导致解密失败。
 * 密钥经环境变量注入，缺失或长度不符时启动即失败（fail-fast）。
 */
@Component
public final class DataEncryptor {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private static final String KEY_ALGORITHM = "AES";

    private static final int IV_LENGTH_BYTES = 12;

    private static final int TAG_LENGTH_BITS = 128;

    private static final int KEY_LENGTH_BYTES = 32;

    private final SecretKey key;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 构造加密器并校验密钥。
     *
     * @param properties 加密配置（dataKey 为 Base64 编码的 32 字节密钥）
     */
    public DataEncryptor(CryptoProperties properties) {
        this.key = loadKey(properties.dataKey());
    }

    /**
     * 加密明文。
     *
     * @param plain 明文，允许 null
     * @return Base64 密文；入参 null 原样返回 null
     */
    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] output = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, output, 0, iv.length);
            System.arraycopy(cipherBytes, 0, output, iv.length, cipherBytes.length);
            return Base64.getEncoder().encodeToString(output);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("敏感数据加密失败", e);
        }
    }

    /**
     * 解密密文。
     *
     * @param encoded Base64 密文，允许 null
     * @return 明文；入参 null 原样返回 null
     * @throws IllegalStateException 密文被篡改、格式非法或密钥不匹配
     */
    public String decrypt(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_LENGTH_BITS, decoded, 0, IV_LENGTH_BYTES));
            byte[] plain = cipher.doFinal(decoded, IV_LENGTH_BYTES, decoded.length - IV_LENGTH_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("敏感数据解密失败（密文损坏或密钥不匹配）", e);
        }
    }

    private static SecretKey loadKey(String base64Key) {
        byte[] bytes = decodeConfiguredKey(base64Key);
        requireAes256Length(bytes);
        return new SecretKeySpec(bytes, KEY_ALGORITHM);
    }

    private static byte[] decodeConfiguredKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("DATA_ENCRYPTION_KEY 未配置（环境变量注入，禁止硬编码）");
        }
        return Base64.getDecoder().decode(base64Key.trim());
    }

    private static void requireAes256Length(byte[] bytes) {
        if (bytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException("DATA_ENCRYPTION_KEY 必须为 32 字节（AES-256）");
        }
    }
}
