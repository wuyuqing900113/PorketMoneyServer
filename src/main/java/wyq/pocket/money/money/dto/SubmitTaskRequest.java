package wyq.pocket.money.money.dto;

import jakarta.validation.constraints.Size;

/**
 * 提交学习任务请求（M2 设计 §8.2 #16）。
 *
 * @param submitNote 提交说明（可空）
 */
public record SubmitTaskRequest(@Size(max = 256) String submitNote) {
}
