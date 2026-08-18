package wyq.pocket.money.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.Test;

/**
 * DataEncryptor 单元测试：往返、篡改检测、IV 随机性、null 安全、密钥校验。
 */
class DataEncryptorTest {

    /** 测试密钥：随机 32 字节，非任何环境真实密钥。 */
    private static final CryptoProperties PROPERTIES = new CryptoProperties(randomKeyBase64());

    private final DataEncryptor encryptor = new DataEncryptor(PROPERTIES);

    private static String randomKeyBase64() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @Test
    void shouldEncryptAndDecryptRoundTrip() {
        String cipher = encryptor.encrypt("13800001234");

        assertThat(cipher).isNotBlank().isNotEqualTo("13800001234");
        assertThat(encryptor.decrypt(cipher)).isEqualTo("13800001234");
    }

    @Test
    void shouldRejectTamperedCiphertext() {
        String cipher = encryptor.encrypt("13800001234");
        char flipped = cipher.charAt(0) == 'A' ? 'B' : 'A';
        String tampered = flipped + cipher.substring(1);

        assertThatThrownBy(() -> encryptor.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("解密失败");
    }

    @Test
    void shouldProduceDifferentCiphertextForSamePlain() {
        assertThat(encryptor.encrypt("13800001234"))
                .isNotEqualTo(encryptor.encrypt("13800001234"));
    }

    @Test
    void shouldPassThroughNull() {
        assertThat(encryptor.encrypt(null)).isNull();
        assertThat(encryptor.decrypt(null)).isNull();
    }

    @Test
    void shouldRejectBlankKey() {
        assertThatThrownBy(() -> new DataEncryptor(new CryptoProperties("")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未配置");
    }

    @Test
    void shouldRejectWrongKeyLength() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new DataEncryptor(new CryptoProperties(shortKey)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 字节");
    }
}
