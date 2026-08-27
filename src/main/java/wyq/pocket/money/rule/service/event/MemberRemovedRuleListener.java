package wyq.pocket.money.rule.service.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import wyq.pocket.money.rule.mapper.MoneyRuleMapper;
import wyq.pocket.money.user.event.MemberRemovedEvent;

/**
 * 成员移除联动（M2 设计 §7.4，D11）：暂停被移除成员全部 ACTIVE 规则，
 * 保证「移除即停发」无窗口期；结算任务的成员校验为第二道防线。
 *
 * <p>与移除主流程同事务执行；监听器内部捕获异常，
 * 联动失败不回滚成员移除（ERROR 日志留痕，结算兜底欠发而非错发）。
 */
@Component
public class MemberRemovedRuleListener {

    private static final Logger LOG = LoggerFactory.getLogger(MemberRemovedRuleListener.class);

    private final MoneyRuleMapper ruleMapper;

    /**
     * 注入规则 Mapper。
     *
     * @param ruleMapper 规则 Mapper
     */
    public MemberRemovedRuleListener(MoneyRuleMapper ruleMapper) {
        this.ruleMapper = ruleMapper;
    }

    /**
     * 处理成员移除事件。
     *
     * @param event 成员移除事件
     */
    @EventListener
    public void onMemberRemoved(MemberRemovedEvent event) {
        try {
            int paused = ruleMapper.pauseActiveByBeneficiary(event.userId());
            LOG.info("MEMBER_RULE_CASCADE familyId={} userId={} pausedRules={}",
                    event.familyId(), event.userId(), paused);
        } catch (RuntimeException e) {
            LOG.error("MEMBER_RULE_CASCADE_FAILED familyId={} userId={}",
                    event.familyId(), event.userId(), e);
        }
    }
}
