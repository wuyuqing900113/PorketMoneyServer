package wyq.pocket.money.rule.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import wyq.pocket.money.common.audit.AuditAction;
import wyq.pocket.money.common.audit.AuditEntry;
import wyq.pocket.money.common.audit.AuditService;
import wyq.pocket.money.money.domain.MoneyTransaction;
import wyq.pocket.money.money.domain.TxBizType;
import wyq.pocket.money.money.domain.TxDirection;
import wyq.pocket.money.money.domain.TxRefType;
import wyq.pocket.money.money.service.AccountTransactionService;
import wyq.pocket.money.money.service.TxCommand;
import wyq.pocket.money.rule.domain.MoneyRule;
import wyq.pocket.money.rule.domain.RuleGrantRecord;
import wyq.pocket.money.rule.mapper.RuleGrantRecordMapper;
import wyq.pocket.money.user.service.FamilyAccessChecker;

/**
 * 单条规则发放执行器（M2 设计 §7.2）：独立 Bean 保证 @Transactional 生效，
 * 每条规则独立事务，失败仅回滚本条。
 *
 * <p>幂等锚点：先插 rule_grant_record（uk rule_id + grant_month），
 * 唯一键冲突即视为当月已发放直接跳过；成员校验为第二道防线
 * （事件丢失时欠发而非错发，宁漏勿错）。
 */
@Component
public class RuleGrantExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(RuleGrantExecutor.class);

    private final RuleGrantRecordMapper grantRecordMapper;

    private final AccountTransactionService accountTransactionService;

    private final FamilyAccessChecker familyAccessChecker;

    private final AuditService auditService;

    /**
     * 注入协作对象。
     *
     * @param grantRecordMapper         发放记录 Mapper
     * @param accountTransactionService 记账原语
     * @param familyAccessChecker       数据级访问守卫
     * @param auditService              审计服务
     */
    public RuleGrantExecutor(RuleGrantRecordMapper grantRecordMapper,
                             AccountTransactionService accountTransactionService,
                             FamilyAccessChecker familyAccessChecker,
                             AuditService auditService) {
        this.grantRecordMapper = grantRecordMapper;
        this.accountTransactionService = accountTransactionService;
        this.familyAccessChecker = familyAccessChecker;
        this.auditService = auditService;
    }

    /**
     * 发放一条规则的当月金额（幂等）。
     *
     * @param rule  规则
     * @param month 发放月份（YYYY-MM）
     * @return true = 本次实际发放；false = 已发放跳过 / 受益人已移出跳过
     */
    @Transactional
    public boolean settle(MoneyRule rule, String month) {
        if (!familyAccessChecker.isMember(rule.getFamilyId(), rule.getBeneficiaryUserId())) {
            LOG.warn("SETTLE_SKIP_NOT_MEMBER ruleId={} userId={}",
                    rule.getId(), rule.getBeneficiaryUserId());
            return false;
        }
        RuleGrantRecord record = new RuleGrantRecord();
        record.setRuleId(rule.getId());
        record.setGrantMonth(month);
        record.setAmount(rule.getAmount());
        record.setStatus(RuleGrantRecord.STATUS_SUCCESS);
        try {
            grantRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            return false;
        }
        MoneyTransaction tx = accountTransactionService.apply(new TxCommand(rule.getFamilyId(),
                rule.getBeneficiaryUserId(), TxDirection.IN, TxBizType.MONTHLY_RULE,
                rule.getAmount(), TxRefType.RULE_GRANT, record.getId(), null,
                "包月规则发放 " + rule.getRuleName() + " " + month, null));
        grantRecordMapper.updateTransactionId(record.getId(), tx.getId());
        auditService.record(new AuditEntry(null, AuditAction.RULE_GRANT_EXECUTED,
                "MONEY_RULE", String.valueOf(rule.getId()),
                "month=" + month + ";amount=" + rule.getAmount()));
        return true;
    }
}
