package wyq.pocket.money.notify.service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * 确定性文案模板（M5 设计 §5.2）。
 *
 * <p>文案由「类型 + bizType + 参数」服务端拼接，不引入 LLM 生成、不即兴发挥
 * （对齐 M4 D33 一致性原则的推广）；模板常量内聚于此消魔法值；
 * title / content 长度约束 VARCHAR(128)/VARCHAR(512)。
 */
@Component
public class NotificationTemplateService {

    /** 入账 bizType → 中文标签（按 TxBizType.name() 索引）。 */
    private static final Map<String, String> BIZ_LABELS = Map.of(
            "MONTHLY_RULE", "包月规则发放",
            "MANUAL_ADD", "手动存入",
            "LEARNING_REWARD", "学习任务奖励",
            "WORK_VALUE", "工作价值入账"
    );

    /** 未知 bizType 的兜底标签。 */
    private static final String DEFAULT_BIZ_LABEL = "零花钱入账";

    /**
     * 入账通知文案（§5.1）。
     *
     * @param bizType 业务类型名（TxBizType.name()）
     * @param amount  金额
     * @return 标题 + 正文
     */
    public Template txIn(String bizType, BigDecimal amount) {
        return new Template("零花钱入账",
                "你收到 " + money(amount) + " 元零花钱（" + label(bizType) + "）");
    }

    /**
     * 出账通知文案（§5.1）。
     *
     * @param amount 金额
     * @return 标题 + 正文
     */
    public Template txOut(BigDecimal amount) {
        return new Template("零花钱支出", "你提取了 " + money(amount) + " 元零花钱");
    }

    /**
     * 余额不足提醒文案（§5.1）。
     *
     * @param nickname 账户主人昵称（可空）
     * @param amount   记账后余额
     * @return 标题 + 正文
     */
    public Template lowBalance(String nickname, BigDecimal amount) {
        return new Template("余额不足提醒",
                nickname + "账户余额仅剩 " + money(amount) + " 元，已低于提醒阈值");
    }

    /**
     * 规则到期文案（§5.1）。
     *
     * @param ruleName 规则名
     * @param endMonth 结束月份（YYYY-MM）
     * @return 标题 + 正文
     */
    public Template ruleExpired(String ruleName, String endMonth) {
        return new Template("规则到期", "规则「" + ruleName + "」已于 " + endMonth + " 到期");
    }

    private String label(String bizType) {
        return BIZ_LABELS.getOrDefault(bizType, DEFAULT_BIZ_LABEL);
    }

    private String money(BigDecimal amount) {
        return String.format(Locale.ROOT, "%.2f", amount);
    }

    /**
     * 标题 + 正文（落 notification.title / content）。
     *
     * @param title   标题（≤128）
     * @param content 正文（≤512）
     */
    public record Template(String title, String content) {
    }
}
