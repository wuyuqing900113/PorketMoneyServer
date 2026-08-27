package wyq.pocket.money.rule.service.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import wyq.pocket.money.rule.mapper.MoneyRuleMapper;
import wyq.pocket.money.user.event.MemberRemovedEvent;

/**
 * 成员移除规则联动单元测试（M2 设计 §7.4 D11）：
 * 暂停被移除成员全部 ACTIVE 规则；异常吞掉不阻断主流程（结算兜底宁漏勿错）。
 */
class MemberRemovedRuleListenerTest {

    private final MoneyRuleMapper ruleMapper = mock(MoneyRuleMapper.class);

    private final MemberRemovedRuleListener listener =
            new MemberRemovedRuleListener(ruleMapper);

    @Test
    void onMemberRemovedShouldPauseActiveRules() {
        listener.onMemberRemoved(new MemberRemovedEvent(10L, 2L));

        verify(ruleMapper).pauseActiveByBeneficiary(2L);
    }

    @Test
    void onMemberRemovedShouldSwallowFailure() {
        doThrow(new RuntimeException("db down"))
                .when(ruleMapper).pauseActiveByBeneficiary(any(Long.class));

        listener.onMemberRemoved(new MemberRemovedEvent(10L, 2L));

        verify(ruleMapper).pauseActiveByBeneficiary(2L);
    }
}
