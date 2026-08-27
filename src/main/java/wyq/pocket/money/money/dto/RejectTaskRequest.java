package wyq.pocket.money.money.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 驳回学习任务请求（M2 设计 §8.2 #18）。
 *
 * @param rejectReason 驳回原因
 */
public record RejectTaskRequest(@NotBlank @Size(max = 256) String rejectReason) {
}
