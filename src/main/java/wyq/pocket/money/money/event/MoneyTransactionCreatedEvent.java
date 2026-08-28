package wyq.pocket.money.money.event;

import java.math.BigDecimal;

/**
 * 记账成功领域事件（M5 设计 §4.1）：由 AccountTransactionService.apply 在事务内发布。
 *
 * <p>监听方：通知模块（账务变动通知 + 余额不足提醒）。监听器 try-catch，
 * 不回滚记账主流程。携带账户主人与操作人双身份，供接收人解析与文案区分
 * 「自己操作 / 他人代操作」。
 *
 * <p>方向 / 业务类型以 {@link String} 承载（发布方 {@code enum.name()}），
 * 而非引用 {@code money.domain} 的枚举类型：事件为跨模块契约，通知模块仅按
 * 事件 payload 取数、不得依赖 money.domain（M5 设计 §3.4 规则 ②），字符串
 * 快照既保证契约自足，也避免通知模块因取枚举而产生对 money.domain 的编译依赖。
 *
 * @param familyId       家庭 ID
 * @param userId         账户主人用户 ID
 * @param operatorUserId 操作人用户 ID（定时结算为 null）
 * @param direction      流水方向 "IN" / "OUT"
 * @param bizType        业务类型（"MONTHLY_RULE"/"MANUAL_ADD"/"LEARNING_REWARD"/"WORK_VALUE"/"WITHDRAW"）
 * @param amount         金额（>0）
 * @param balanceAfter   记账后余额
 * @param transactionId  流水 ID（biz_ref 指向）
 * @param remark         备注（可空）
 */
public record MoneyTransactionCreatedEvent(long familyId, long userId, Long operatorUserId,
        String direction, String bizType, BigDecimal amount,
        BigDecimal balanceAfter, long transactionId, String remark) {
}
