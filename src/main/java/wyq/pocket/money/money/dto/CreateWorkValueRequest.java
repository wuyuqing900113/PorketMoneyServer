package wyq.pocket.money.money.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建工作价值记录请求（M2 设计 §8.2 #21）。
 *
 * @param workMonth       工作月份（YYYY-MM）
 * @param salaryIncome    当月工资收入（仅记录展示，允许 0）
 * @param allowanceAmount 发放入账金额（&gt; 0）
 * @param workSummary     工作内容摘要（可空）
 */
public record CreateWorkValueRequest(
        @NotBlank @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$") String workMonth,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal salaryIncome,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2)
                BigDecimal allowanceAmount,
        @Size(max = 256) String workSummary) {
}
