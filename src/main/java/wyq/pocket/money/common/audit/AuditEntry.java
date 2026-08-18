package wyq.pocket.money.common.audit;

/**
 * 审计事件入参（M1 设计 §9.1）。
 *
 * <p>traceId 与 client_ip 由 {@link AuditService} 从请求上下文自动补全，
 * 调用方不感知；detail 为结构化补充信息，**调用方须先脱敏**（§8.2 红线）。
 *
 * @param userId     用户 ID；匿名事件（如未知账号登录失败）可为 null
 * @param action     审计动作
 * @param targetType 目标对象类型（如 USER / FAMILY），可空
 * @param targetId   目标对象 ID，可空
 * @param detail     脱敏后的结构化补充信息（JSON 文本），可空
 */
public record AuditEntry(Long userId, AuditAction action, String targetType,
        String targetId, String detail) {
}
