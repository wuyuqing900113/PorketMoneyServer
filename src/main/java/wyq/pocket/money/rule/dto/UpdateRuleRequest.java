package wyq.pocket.money.rule.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 修改包月规则请求（M2 设计 §8.2 #10，全量更新；起始月不可改）。
 *
 * @param ruleName 规则名称
 * @param amount   每月发放金额
 * @param grantDay 发放日（1–28）
 * @param endMonth 失效月（YYYY-MM，含；可空 = 长期）
 * @param remark   备注（可空）
 */
public record UpdateRuleRequest(
        @NotBlank @Size(max = 32) String ruleName,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotNull @Min(1) @Max(28) Integer grantDay,
        @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$") String endMonth,
        @Size(max = 128) String remark) {
}
