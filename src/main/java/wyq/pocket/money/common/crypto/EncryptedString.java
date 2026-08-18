package wyq.pocket.money.common.crypto;

/**
 * MyBatis 注册隔离标记类型（javaType marker），仅供
 * {@link EncryptedFieldTypeHandler} 的 {@code @MappedTypes} 引用。
 *
 * <p>背景（MyBatis 3.5.19 {@code TypeHandlerRegistry.register(TypeHandler)}
 * 字节码核实）：handler 未标注 {@code @MappedTypes} 时，若其
 * {@code instanceof TypeReference}（{@code BaseTypeHandler<T>} 满足），
 * 注册会经 {@code getRawType()} 把 javaType 推断为泛型实参 String，
 * 从而顶替全局 String 映射——所有 VARCHAR 参数/列都会被加解密。
 * 标注 {@code @MappedTypes(EncryptedString.class)} 后注册仅落在本标记
 * 类型（无任何真实参数/列使用它）与实例表（allTypeHandlersMap），
 * 显式 {@code typeHandler=} 引用仍命中 Spring bean 实例。
 *
 * <p>本类型严禁实例化、继承或被业务代码引用。
 */
public final class EncryptedString {

    private EncryptedString() {
        // 标记类型：禁止实例化
    }
}
