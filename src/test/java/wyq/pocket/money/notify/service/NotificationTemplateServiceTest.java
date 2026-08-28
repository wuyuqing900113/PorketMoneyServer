package wyq.pocket.money.notify.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * 确定性文案模板测试（M5 设计 §5.2）：各类型 + bizType 文案拼接、
 * 金额两位小数格式化、未知 bizType 兜底、长度约束（≤128/≤512）。
 */
class NotificationTemplateServiceTest {

    private final NotificationTemplateService template = new NotificationTemplateService();

    @Test
    void txInShouldLabelBizType() {
        assertThat(template.txIn("MONTHLY_RULE", new BigDecimal("50.00")).title())
                .isEqualTo("零花钱入账");
        assertThat(template.txIn("MONTHLY_RULE", new BigDecimal("50.00")).content())
                .isEqualTo("你收到 50.00 元零花钱（包月规则发放）");
        assertThat(template.txIn("MANUAL_ADD", new BigDecimal("100.5")).content())
                .isEqualTo("你收到 100.50 元零花钱（手动存入）");
        assertThat(template.txIn("LEARNING_REWARD", new BigDecimal("30")).content())
                .isEqualTo("你收到 30.00 元零花钱（学习任务奖励）");
        assertThat(template.txIn("WORK_VALUE", new BigDecimal("88.88")).content())
                .isEqualTo("你收到 88.88 元零花钱（工作价值入账）");
    }

    @Test
    void txInShouldFallbackToDefaultLabelForUnknownBizType() {
        assertThat(template.txIn("UNKNOWN", new BigDecimal("1.00")).content())
                .isEqualTo("你收到 1.00 元零花钱（零花钱入账）");
    }

    @Test
    void txOutShouldFormatAmount() {
        assertThat(template.txOut(new BigDecimal("20.00")).title()).isEqualTo("零花钱支出");
        assertThat(template.txOut(new BigDecimal("20.00")).content())
                .isEqualTo("你提取了 20.00 元零花钱");
    }

    @Test
    void lowBalanceShouldIncludeNicknameAndAmount() {
        assertThat(template.lowBalance("小明", new BigDecimal("5.00")).title())
                .isEqualTo("余额不足提醒");
        assertThat(template.lowBalance("小明", new BigDecimal("5.00")).content())
                .isEqualTo("小明账户余额仅剩 5.00 元，已低于提醒阈值");
    }

    @Test
    void ruleExpiredShouldIncludeNameAndMonth() {
        assertThat(template.ruleExpired("每周零花钱", "2026-07").title()).isEqualTo("规则到期");
        assertThat(template.ruleExpired("每周零花钱", "2026-07").content())
                .isEqualTo("规则「每周零花钱」已于 2026-07 到期");
    }

    @Test
    void templatesShouldRespectColumnLengthLimits() {
        BigDecimal max = new BigDecimal("9999999999.99");
        NotificationTemplateService.Template in = template.txIn("MONTHLY_RULE", max);
        NotificationTemplateService.Template out = template.txOut(max);
        NotificationTemplateService.Template low = template.lowBalance("很长的昵称", max);
        NotificationTemplateService.Template expired = template.ruleExpired("很长的规则名", "2026-07");

        assertThat(in.title().length()).isLessThanOrEqualTo(128);
        assertThat(in.content().length()).isLessThanOrEqualTo(512);
        assertThat(out.content().length()).isLessThanOrEqualTo(512);
        assertThat(low.content().length()).isLessThanOrEqualTo(512);
        assertThat(expired.content().length()).isLessThanOrEqualTo(512);
    }
}
