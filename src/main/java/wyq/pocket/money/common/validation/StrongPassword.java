package wyq.pocket.money.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * 密码强度约束（M1 设计 §5.1 / §5.4）：至少 8 位，且同时包含字母与数字。
 *
 * <p>{@code null} 值交由 {@code @NotBlank} 处理，本注解不重复拦截。
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    /**
     * 校验失败默认提示。
     *
     * @return 提示文案
     */
    String message() default "密码须至少 8 位且同时包含字母与数字";

    /**
     * 校验分组。
     *
     * @return 分组
     */
    Class<?>[] groups() default {};

    /**
     * 负载。
     *
     * @return 负载
     */
    Class<? extends Payload>[] payload() default {};
}
