package wyq.pocket.money.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * EncryptedFieldTypeHandler 单元测试（T3）：写入密文化、读取解密、
 * null 透传（M1 设计 §8.2）。测试密钥为全零固定值，仅测试用。
 */
class EncryptedFieldTypeHandlerTest {

    private static final String PHONE = "13800001234";

    private final DataEncryptor encryptor = new DataEncryptor(
            new CryptoProperties("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="));

    private final EncryptedFieldTypeHandler handler = new EncryptedFieldTypeHandler(encryptor);

    @Test
    void setNonNullParameterShouldWriteCiphertext() throws SQLException {
        PreparedStatement ps = mock(PreparedStatement.class);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        handler.setNonNullParameter(ps, 1, PHONE, JdbcType.VARCHAR);

        verify(ps).setString(eq(1), captor.capture());
        assertThat(captor.getValue()).isNotEqualTo(PHONE);
        assertThat(encryptor.decrypt(captor.getValue())).isEqualTo(PHONE);
    }

    @Test
    void getNullableResultByColumnNameShouldDecrypt() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("phone_encrypted")).thenReturn(encryptor.encrypt(PHONE));

        assertThat(handler.getNullableResult(rs, "phone_encrypted")).isEqualTo(PHONE);
    }

    @Test
    void getNullableResultByColumnIndexShouldDecrypt() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString(3)).thenReturn(encryptor.encrypt(PHONE));

        assertThat(handler.getNullableResult(rs, 3)).isEqualTo(PHONE);
    }

    @Test
    void getNullableResultFromCallableStatementShouldDecrypt() throws SQLException {
        CallableStatement cs = mock(CallableStatement.class);
        when(cs.getString(2)).thenReturn(encryptor.encrypt(PHONE));

        assertThat(handler.getNullableResult(cs, 2)).isEqualTo(PHONE);
    }

    @Test
    void nullColumnValueShouldReturnNull() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("phone_encrypted")).thenReturn(null);

        assertThat(handler.getNullableResult(rs, "phone_encrypted")).isNull();
    }

    @Test
    void nullParameterShouldWriteJdbcNullWithoutEncrypting() throws SQLException {
        PreparedStatement ps = mock(PreparedStatement.class);

        handler.setParameter(ps, 1, null, JdbcType.VARCHAR);

        verify(ps).setNull(1, JdbcType.VARCHAR.TYPE_CODE);
        verify(ps, never()).setString(anyInt(), anyString());
    }
}
