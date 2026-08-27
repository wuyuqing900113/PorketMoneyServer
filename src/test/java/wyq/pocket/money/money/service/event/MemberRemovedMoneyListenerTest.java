package wyq.pocket.money.money.service.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import wyq.pocket.money.money.domain.MoneyAccount;
import wyq.pocket.money.money.mapper.LearningTaskMapper;
import wyq.pocket.money.money.mapper.MoneyAccountMapper;
import wyq.pocket.money.user.event.MemberRemovedEvent;

/**
 * 成员移除资金联动单元测试（M2 设计 §7.4）：
 * 冻结账户 + 取消未发放任务；异常吞掉不阻断成员移除主流程。
 */
class MemberRemovedMoneyListenerTest {

    private final MoneyAccountMapper accountMapper = mock(MoneyAccountMapper.class);

    private final LearningTaskMapper taskMapper = mock(LearningTaskMapper.class);

    private final MemberRemovedMoneyListener listener =
            new MemberRemovedMoneyListener(accountMapper, taskMapper);

    @Test
    void onMemberRemovedShouldFreezeAccountAndCancelOpenTasks() {
        listener.onMemberRemoved(new MemberRemovedEvent(10L, 2L));

        verify(accountMapper).updateStatusByUserId(2L, MoneyAccount.STATUS_FROZEN);
        verify(taskMapper).cancelOpenByAssignee(eq(2L));
    }

    @Test
    void onMemberRemovedShouldSwallowFailure() {
        doThrow(new RuntimeException("db down"))
                .when(accountMapper).updateStatusByUserId(any(Long.class), any());

        // 联动失败不回滚成员移除：异常被捕获
        listener.onMemberRemoved(new MemberRemovedEvent(10L, 2L));

        verify(accountMapper).updateStatusByUserId(2L, MoneyAccount.STATUS_FROZEN);
    }
}
