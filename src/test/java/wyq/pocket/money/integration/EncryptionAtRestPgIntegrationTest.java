package wyq.pocket.money.integration;

import static org.hamcrest.Matchers.equalTo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import wyq.pocket.money.common.crypto.DataEncryptor;
import wyq.pocket.money.common.crypto.Hashes;
import wyq.pocket.money.common.log.MaskingRules;

/**
 * 静态加密落库集成测试（M1 设计 §12.2 EncryptionAtRestIT / §4.6）。
 *
 * <p>直查数据库：手机号以 SHA-256 哈希 + AES-256-GCM 密文存储，全表无明文；
 * 解密往返一致；GET /users/me 仅回显脱敏号；孩子账号不落地任何手机号
 * （COPPA 类儿童隐私合规）。
 */
class EncryptionAtRestPgIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PARENT_PHONE = "13930000001";

    private static final String SECOND_PHONE = "13930000002";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DataEncryptor dataEncryptor;

    @Test
    void phoneShouldBeStoredHashedAndEncryptedNeverPlaintext() throws SQLException {
        TestAccount account = registerAndLogin(PARENT_PHONE);

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT phone_hash, phone_encrypted FROM app_user WHERE id = ?")) {
            statement.setLong(1, account.userId());
            try (ResultSet resultSet = statement.executeQuery()) {
                Assertions.assertTrue(resultSet.next(), "注册用户行必须存在");
                String phoneHash = resultSet.getString("phone_hash");
                String phoneEncrypted = resultSet.getString("phone_encrypted");
                Assertions.assertEquals(Hashes.sha256Hex(PARENT_PHONE), phoneHash);
                Assertions.assertNotNull(phoneEncrypted);
                Assertions.assertFalse(phoneEncrypted.contains(PARENT_PHONE),
                        "密文中不得包含明文手机号");
                Assertions.assertEquals(PARENT_PHONE, dataEncryptor.decrypt(phoneEncrypted),
                        "解密往返必须还原明文");
            }
        }

        // 全表范围：密文列中不存在任何明文手机号子串
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM app_user WHERE phone_encrypted LIKE ?")) {
            statement.setString(1, "%" + PARENT_PHONE + "%");
            try (ResultSet resultSet = statement.executeQuery()) {
                Assertions.assertTrue(resultSet.next());
                Assertions.assertEquals(0, resultSet.getInt(1), "全表不得出现明文手机号");
            }
        }

        // 接口层仅回显脱敏手机号
        withToken(account).when().get("/api/v1/users/me")
                .then().statusCode(200).body("code", equalTo(0))
                .body("data.maskedPhone", equalTo(MaskingRules.mask(PARENT_PHONE)));
    }

    @Test
    void childAccountShouldNotPersistPhone() throws SQLException {
        TestAccount parent = registerAndLogin(SECOND_PHONE);
        long childId = createChild(parent, "pgenc01a");

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT phone_hash, phone_encrypted FROM app_user WHERE id = ?")) {
            statement.setLong(1, childId);
            try (ResultSet resultSet = statement.executeQuery()) {
                Assertions.assertTrue(resultSet.next(), "孩子用户行必须存在");
                Assertions.assertNull(resultSet.getString("phone_hash"),
                        "孩子账号不得落地手机号哈希");
                Assertions.assertNull(resultSet.getString("phone_encrypted"),
                        "孩子账号不得落地手机号密文");
            }
        }
    }
}
