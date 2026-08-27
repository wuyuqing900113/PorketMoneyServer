package wyq.pocket.money.money.service.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import wyq.pocket.money.money.domain.MoneyAccount;
import wyq.pocket.money.money.mapper.LearningTaskMapper;
import wyq.pocket.money.money.mapper.MoneyAccountMapper;
import wyq.pocket.money.user.event.MemberRemovedEvent;

/**
 * 成员移除联动（M2 设计 §7.4）：冻结账户 + 取消未发放学习任务。
 *
 * <p>与移除主流程同事务执行；监听器内部捕获异常，
 * 联动失败不回滚成员移除（ERROR 日志留痕，人工修复）。
 */
@Component
public class MemberRemovedMoneyListener {

    private static final Logger LOG = LoggerFactory.getLogger(MemberRemovedMoneyListener.class);

    private final MoneyAccountMapper accountMapper;

    private final LearningTaskMapper taskMapper;

    /**
     * 注入协作对象。
     *
     * @param accountMapper 账户 Mapper
     * @param taskMapper    学习任务 Mapper
     */
    public MemberRemovedMoneyListener(MoneyAccountMapper accountMapper,
                                      LearningTaskMapper taskMapper) {
        this.accountMapper = accountMapper;
        this.taskMapper = taskMapper;
    }

    /**
     * 处理成员移除事件。
     *
     * @param event 成员移除事件
     */
    @EventListener
    public void onMemberRemoved(MemberRemovedEvent event) {
        try {
            accountMapper.updateStatusByUserId(event.userId(), MoneyAccount.STATUS_FROZEN);
            int canceled = taskMapper.cancelOpenByAssignee(event.userId());
            LOG.info("MEMBER_MONEY_CASCADE familyId={} userId={} canceledTasks={}",
                    event.familyId(), event.userId(), canceled);
        } catch (RuntimeException e) {
            LOG.error("MEMBER_MONEY_CASCADE_FAILED familyId={} userId={}",
                    event.familyId(), event.userId(), e);
        }
    }
}
