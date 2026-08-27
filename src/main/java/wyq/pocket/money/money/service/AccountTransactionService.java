package wyq.pocket.money.money.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import wyq.pocket.money.common.exception.BusinessException;
import wyq.pocket.money.common.web.CommonErrorCode;
import wyq.pocket.money.money.domain.MoneyAccount;
import wyq.pocket.money.money.domain.MoneyTransaction;
import wyq.pocket.money.money.domain.TxDirection;
import wyq.pocket.money.money.dto.MoneyErrorCode;
import wyq.pocket.money.money.mapper.MoneyAccountMapper;
import wyq.pocket.money.money.mapper.MoneyTransactionMapper;

/**
 * 记账原语：余额快照 + 流水台账同事务双写（M2 设计 §4.3 七步流程）。
 *
 * <p>流程：校验金额（业务兜底 300004）→ 定位账户（入账惰性开户 / 出账要求已开户）
 * → 校验冻结 → 校验余额 → version 乐观锁条件更新快照 → 插入流水（balance_after）
 * → 提交。乐观锁冲突重试，上限 {@value #MAX_ATTEMPTS} 次；耗尽按可重试系统错误抛出。
 *
 * <p>余额下限另有数据库 CHECK(balance &gt;= 0) 兜底。该兜底仅在条件更新
 * 串行化失效的极端场景才可能被触及（乐观锁已保证同版本仅一笔成功），
 * 实际触发时按 DataIntegrityViolationException 走全局兜底错误码。
 */
@Component
public class AccountTransactionService {

    /** 乐观锁重试上限。 */
    private static final int MAX_ATTEMPTS = 3;

    private final AccountService accountService;

    private final MoneyAccountMapper accountMapper;

    private final MoneyTransactionMapper transactionMapper;

    /**
     * 注入协作对象。
     *
     * @param accountService   账户服务
     * @param accountMapper    账户 Mapper
     * @param transactionMapper 流水 Mapper
     */
    public AccountTransactionService(AccountService accountService,
                                     MoneyAccountMapper accountMapper,
                                     MoneyTransactionMapper transactionMapper) {
        this.accountService = accountService;
        this.accountMapper = accountMapper;
        this.transactionMapper = transactionMapper;
    }

    /**
     * 执行一笔记账（同事务双写快照与流水）。
     *
     * @param cmd 记账命令
     * @return 已入库流水
     * @throws BusinessException 300004 金额非法 / 300001 余额不足 /
     *                           300002 账户冻结 / 900003 重试耗尽
     */
    @Transactional
    public MoneyTransaction apply(TxCommand cmd) {
        requirePositiveAmount(cmd);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            MoneyTransaction tx = tryApply(cmd);
            if (tx != null) {
                return tx;
            }
        }
        throw new BusinessException(CommonErrorCode.DATABASE_ERROR, "记账乐观锁重试耗尽");
    }

    /**
     * 金额业务兜底校验（设计 §4.3 步骤 3）：入口 Bean Validation 已拦截，
     * 此处防绕过入口的调用方（定时任务、内部编排）传入非法金额。
     *
     * @param cmd 记账命令
     * @throws BusinessException 300004 金额为空或非正
     */
    private void requirePositiveAmount(TxCommand cmd) {
        if (cmd.amount() == null || cmd.amount().signum() <= 0) {
            throw new BusinessException(MoneyErrorCode.AMOUNT_INVALID);
        }
    }

    private MoneyTransaction tryApply(TxCommand cmd) {
        MoneyAccount account = resolveAccount(cmd);
        requireActive(account);
        BigDecimal balanceAfter = balanceAfter(account, cmd);
        if (!applyDelta(account, cmd)) {
            return null;
        }
        MoneyTransaction tx = buildTransaction(cmd, account, balanceAfter);
        transactionMapper.insert(tx);
        return tx;
    }

    private MoneyAccount resolveAccount(TxCommand cmd) {
        if (cmd.direction() == TxDirection.IN) {
            return accountService.getOrOpen(cmd.familyId(), cmd.userId());
        }
        return accountService.requireAccount(cmd.userId());
    }

    private void requireActive(MoneyAccount account) {
        if (!MoneyAccount.STATUS_ACTIVE.equals(account.getStatus())) {
            throw new BusinessException(MoneyErrorCode.ACCOUNT_FROZEN);
        }
    }

    private BigDecimal balanceAfter(MoneyAccount account, TxCommand cmd) {
        if (cmd.direction() == TxDirection.IN) {
            return account.getBalance().add(cmd.amount());
        }
        if (account.getBalance().compareTo(cmd.amount()) < 0) {
            throw new BusinessException(MoneyErrorCode.BALANCE_NOT_ENOUGH);
        }
        return account.getBalance().subtract(cmd.amount());
    }

    private boolean applyDelta(MoneyAccount account, TxCommand cmd) {
        boolean isIn = cmd.direction() == TxDirection.IN;
        BigDecimal delta = isIn ? cmd.amount() : cmd.amount().negate();
        BigDecimal incomeDelta = isIn ? cmd.amount() : BigDecimal.ZERO;
        BigDecimal expenseDelta = isIn ? BigDecimal.ZERO : cmd.amount();
        return accountMapper.applyDelta(account.getId(), account.getVersion(),
                delta, incomeDelta, expenseDelta) == 1;
    }

    private MoneyTransaction buildTransaction(TxCommand cmd, MoneyAccount account,
                                              BigDecimal balanceAfter) {
        MoneyTransaction tx = new MoneyTransaction();
        tx.setFamilyId(cmd.familyId());
        tx.setAccountId(account.getId());
        tx.setUserId(cmd.userId());
        tx.setDirection(cmd.direction());
        tx.setBizType(cmd.bizType());
        tx.setAmount(cmd.amount());
        tx.setBalanceAfter(balanceAfter);
        tx.setRefType(cmd.refType());
        tx.setRefId(cmd.refId());
        tx.setOperatorUserId(cmd.operatorUserId());
        tx.setRemark(cmd.remark());
        tx.setRequestId(cmd.requestId());
        return tx;
    }
}
