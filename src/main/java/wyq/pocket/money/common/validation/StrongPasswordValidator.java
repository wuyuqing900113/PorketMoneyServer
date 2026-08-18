package wyq.pocket.money.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link StrongPassword} 校验器：长度与字符组成双重校验（M1 设计 §5.1）。
 */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final int MIN_LENGTH = 8;

    /**
     * 校验密码强度。
     *
     * @param value   密码，null 放行（由 @NotBlank 负责）
     * @param context 校验上下文
     * @return 合规返回 true
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || isStrongEnough(value);
    }

    private boolean isStrongEnough(String value) {
        return value.length() >= MIN_LENGTH && hasLetter(value) && hasDigit(value);
    }

    private boolean hasLetter(String value) {
        return value.chars().anyMatch(Character::isLetter);
    }

    private boolean hasDigit(String value) {
        return value.chars().anyMatch(Character::isDigit);
    }
}
