package wyq.pocket.money.common.crypto;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.springframework.stereotype.Component;

/**
 * 加密列 MyBatis TypeHandler：写入自动加密、读取自动解密（M1 设计 §8.2）。
 *
 * <p>是 {@code phone_encrypted} 等加密列明文进出的**唯一入口**，杜绝绕过：
 * mapper 须以 {@code typeHandler=...} 显式引用本类。
 *
 * <p>注册方式说明：本类为 Spring Bean（构造注入 {@link DataEncryptor}），
 * 由 mybatis-spring-boot 自动收集并经 {@code TypeHandlerRegistry} 登记实例。
 * <b>必须标注 {@code @MappedTypes(EncryptedString.class)}</b>：
 * MyBatis 3.5.19 字节码核实，未标注 {@code @MappedTypes} 的 handler 若
 * {@code instanceof TypeReference}（{@code BaseTypeHandler<T>} 均满足），
 * 注册时经 {@code getRawType()} 把 javaType 推断为泛型实参 String，
 * 顶替全局 String 映射，导致所有 VARCHAR 参数/列被误加解密
 * （T4 集成测试 phone_hash 写入密文事故实证）。标注后注册仅落在
 * 标记类型与实例表（allTypeHandlersMap），显式 {@code typeHandler=}
 * 引用经 getMappingTypeHandler 命中本 bean。
 */
@Component
@MappedTypes(EncryptedString.class)
public class EncryptedFieldTypeHandler extends BaseTypeHandler<String> {

    private final DataEncryptor dataEncryptor;

    /**
     * 构造 TypeHandler。
     *
     * @param dataEncryptor AES-256-GCM 加解密器
     */
    public EncryptedFieldTypeHandler(DataEncryptor dataEncryptor) {
        this.dataEncryptor = dataEncryptor;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter,
            JdbcType jdbcType) throws SQLException {
        ps.setString(i, dataEncryptor.encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return dataEncryptor.decrypt(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return dataEncryptor.decrypt(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return dataEncryptor.decrypt(cs.getString(columnIndex));
    }
}
