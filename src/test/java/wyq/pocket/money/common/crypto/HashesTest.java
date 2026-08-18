package wyq.pocket.money.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

/**
 * Hashes 单元测试：SHA-256 摘要格式与确定性（M1 设计 §4.3）。
 */
class HashesTest {

    @Test
    void emptyStringShouldMatchKnownVector() {
        assertThat(Hashes.sha256Hex(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void outputShouldBeLowercaseHexOf64Chars() {
        assertThat(Hashes.sha256Hex("13800001234"))
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }

    @Test
    void sameInputShouldProduceSameDigest() {
        assertThat(Hashes.sha256Hex("refresh-token-1"))
                .isEqualTo(Hashes.sha256Hex("refresh-token-1"));
    }

    @Test
    void differentInputsShouldProduceDifferentDigests() {
        assertThat(Hashes.sha256Hex("13800001234"))
                .isNotEqualTo(Hashes.sha256Hex("13800001235"));
    }

    @Test
    void nullInputShouldFailFast() {
        assertThatNullPointerException().isThrownBy(() -> Hashes.sha256Hex(null));
    }
}
