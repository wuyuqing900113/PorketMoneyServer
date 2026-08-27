package wyq.pocket.money.money.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建学习任务请求（M2 设计 §8.2 #15）。
 *
 * @param assigneeUserId 执行人（孩子）用户 ID
 * @param title          任务标题
 * @param rewardAmount   奖励金额
 * @param deadline       截止日期（可空）
 */
public record CreateLearningTaskRequest(
        @NotNull Long assigneeUserId,
        @NotBlank @Size(max = 64) String title,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal rewardAmount,
        LocalDate deadline) {
}
